// ═══════════════════════════════════════════════════════════════
// Elysium Console — JNI Bridge (bridge.cpp)
// ═══════════════════════════════════════════════════════════════
// Central native bridge implementing:
// 1. Libretro core lifecycle (dlopen/dlsym)
// 2. Thread pinning to prime cores (sched_setaffinity)
// 3. Zero-copy AHardwareBuffer rendering pipeline
// 4. Dynamic cycle multiplier for telemetry-driven adjustments
// ═══════════════════════════════════════════════════════════════

#include <jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <time.h>
#include <atomic>
#include <mutex>

#include "libretro.h"
#include "thread_utils.h"
#include "hardware_buffer_renderer.h"
#include "audio_engine.h"
#include "input_manager.h"

#define TAG "ElysiumBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// Global State
// ═══════════════════════════════════════════════════════════════

namespace {

// Libretro core handle
void* g_coreHandle = nullptr;

// Libretro core function pointers
retro_init_t                  g_retro_init = nullptr;
retro_deinit_t                g_retro_deinit = nullptr;
retro_api_version_t           g_retro_api_version = nullptr;
retro_get_system_info_t       g_retro_get_system_info = nullptr;
retro_get_system_av_info_t    g_retro_get_system_av_info = nullptr;
retro_set_environment_t       g_retro_set_environment = nullptr;
retro_set_video_refresh_t     g_retro_set_video_refresh = nullptr;
retro_set_audio_sample_t      g_retro_set_audio_sample = nullptr;
retro_set_audio_sample_batch_t g_retro_set_audio_sample_batch = nullptr;
retro_set_input_poll_t        g_retro_set_input_poll = nullptr;
retro_set_input_state_t       g_retro_set_input_state = nullptr;
retro_load_game_t             g_retro_load_game = nullptr;
retro_unload_game_t           g_retro_unload_game = nullptr;
retro_run_t                   g_retro_run = nullptr;
retro_reset_t                 g_retro_reset = nullptr;
retro_serialize_size_t        g_retro_serialize_size = nullptr;
retro_serialize_t             g_retro_serialize = nullptr;
retro_unserialize_t           g_retro_unserialize = nullptr;

// Zero-copy renderer
elysium::HardwareBufferRenderer g_renderer;

// Audio engine (OpenSL ES)
elysium::AudioEngine g_audioEngine;

// Input manager
elysium::InputManager g_inputManager;

// Telemetry state
std::atomic<float> g_cycleMultiplier{1.0f};
std::atomic<double> g_currentFps{0.0};
std::atomic<double> g_frameTimeMs{0.0};
std::mutex g_frameMutex;

// Frame timing
struct timespec g_lastFrameTime = {0, 0};
uint64_t g_frameCount = 0;
double g_fpsAccumulator = 0.0;
int g_fpsSampleCount = 0;

// Current AV info
retro_system_av_info g_avInfo = {};
unsigned g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;

// System paths
char g_systemDir[512] = "/data/data/com.elysium.console/files/system";
char g_saveDir[512] = "/data/data/com.elysium.console/files/saves";

} // anonymous namespace

// ═══════════════════════════════════════════════════════════════
// Libretro Callbacks
// ═══════════════════════════════════════════════════════════════

static bool elysium_environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const unsigned* fmt = static_cast<const unsigned*>(data);
            g_pixelFormat = *fmt;
            LOGI("Pixel format set to: %u", g_pixelFormat);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            *static_cast<const char**>(data) = g_systemDir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            *static_cast<const char**>(data) = g_saveDir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* var = static_cast<retro_variable*>(data);
            var->value = nullptr;
            return false;
        }
        default:
            return false;
    }
}

static void elysium_video_refresh(const void* data, unsigned width,
                                   unsigned height, size_t pitch) {
    if (!data) {
        return; // Duplicate frame, skip
    }

    // Update the hardware buffer with the frame data (zero-copy path)
    if (g_renderer.isInitialized()) {
        // Convert from core pixel format to RGBA8888 if needed
        // For XRGB8888, the buffer can be passed directly
        if (g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
            g_renderer.updateFrame(data, width, height, pitch);
        } else {
            // For RGB565, we need a conversion buffer
            // This path is less optimal but handles older cores
            g_renderer.updateFrame(data, width, height, pitch);
        }
    }

    // Update frame timing telemetry
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);

    if (g_lastFrameTime.tv_sec != 0) {
        double deltaMs = (now.tv_sec - g_lastFrameTime.tv_sec) * 1000.0
                        + (now.tv_nsec - g_lastFrameTime.tv_nsec) / 1000000.0;

        g_frameTimeMs.store(deltaMs, std::memory_order_relaxed);

        if (deltaMs > 0.0) {
            double instantFps = 1000.0 / deltaMs;
            g_fpsAccumulator += instantFps;
            g_fpsSampleCount++;

            // Rolling average over 30 samples
            if (g_fpsSampleCount >= 30) {
                g_currentFps.store(g_fpsAccumulator / g_fpsSampleCount,
                                   std::memory_order_relaxed);
                g_fpsAccumulator = 0.0;
                g_fpsSampleCount = 0;
            }
        }
    }

    g_lastFrameTime = now;
    g_frameCount++;
}

static void elysium_audio_sample(int16_t left, int16_t right) {
    if (g_audioEngine.isInitialized()) {
        g_audioEngine.queueSample(left, right);
    }
}

static size_t elysium_audio_sample_batch(const int16_t* data, size_t frames) {
    if (g_audioEngine.isInitialized()) {
        return g_audioEngine.queueSamples(data, frames);
    }
    return frames;
}

static void elysium_input_poll() {
    // Input polling — the InputManager maintains state atomically,
    // so no work needed here. State is read in input_state.
}

static int16_t elysium_input_state(unsigned port, unsigned device,
                                    unsigned index, unsigned id) {
    return g_inputManager.getInputState(port, device, index, id);
}

// ═══════════════════════════════════════════════════════════════
// Helper: Load a symbol from the core library
// ═══════════════════════════════════════════════════════════════

template<typename T>
static bool loadSymbol(T& out, const char* name) {
    out = reinterpret_cast<T>(dlsym(g_coreHandle, name));
    if (!out) {
        LOGE("Failed to load symbol: %s — %s", name, dlerror());
        return false;
    }
    return true;
}

// ═══════════════════════════════════════════════════════════════
// JNI Exports
// ═══════════════════════════════════════════════════════════════

extern "C" {

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeInit(
    JNIEnv* env, jobject thiz,
    jstring systemPath, jstring savePath
) {
    // Store system paths
    const char* sys = env->GetStringUTFChars(systemPath, nullptr);
    const char* sav = env->GetStringUTFChars(savePath, nullptr);
    strncpy(g_systemDir, sys, sizeof(g_systemDir) - 1);
    strncpy(g_saveDir, sav, sizeof(g_saveDir) - 1);
    env->ReleaseStringUTFChars(systemPath, sys);
    env->ReleaseStringUTFChars(savePath, sav);

    // Reset telemetry
    g_currentFps.store(0.0);
    g_frameTimeMs.store(0.0);
    g_frameCount = 0;
    g_fpsAccumulator = 0.0;
    g_fpsSampleCount = 0;
    g_lastFrameTime = {0, 0};
    g_cycleMultiplier.store(1.0f);
    g_inputManager.reset();

    LOGI("Elysium Bridge initialized. System: %s | Save: %s", g_systemDir, g_saveDir);
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadCore(
    JNIEnv* env, jobject thiz, jstring corePath
) {
    // Unload previous core if loaded
    if (g_coreHandle) {
        if (g_retro_deinit) g_retro_deinit();
        dlclose(g_coreHandle);
        g_coreHandle = nullptr;
    }

    const char* path = env->GetStringUTFChars(corePath, nullptr);
    LOGI("Loading core: %s", path);

    g_coreHandle = dlopen(path, RTLD_LAZY);
    if (!g_coreHandle) {
        LOGE("dlopen failed: %s", dlerror());
        env->ReleaseStringUTFChars(corePath, path);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(corePath, path);

    // Resolve all Libretro symbols
    bool ok = true;
    ok &= loadSymbol(g_retro_init, "retro_init");
    ok &= loadSymbol(g_retro_deinit, "retro_deinit");
    ok &= loadSymbol(g_retro_api_version, "retro_api_version");
    ok &= loadSymbol(g_retro_get_system_info, "retro_get_system_info");
    ok &= loadSymbol(g_retro_get_system_av_info, "retro_get_system_av_info");
    ok &= loadSymbol(g_retro_set_environment, "retro_set_environment");
    ok &= loadSymbol(g_retro_set_video_refresh, "retro_set_video_refresh");
    ok &= loadSymbol(g_retro_set_audio_sample, "retro_set_audio_sample");
    ok &= loadSymbol(g_retro_set_audio_sample_batch, "retro_set_audio_sample_batch");
    ok &= loadSymbol(g_retro_set_input_poll, "retro_set_input_poll");
    ok &= loadSymbol(g_retro_set_input_state, "retro_set_input_state");
    ok &= loadSymbol(g_retro_load_game, "retro_load_game");
    ok &= loadSymbol(g_retro_unload_game, "retro_unload_game");
    g_retro_run = (retro_run_t)dlsym(g_coreHandle, "retro_run");
    g_retro_reset = (retro_reset_t)dlsym(g_coreHandle, "retro_reset");
    g_retro_serialize_size = (retro_serialize_size_t)dlsym(g_coreHandle, "retro_serialize_size");
    g_retro_serialize = (retro_serialize_t)dlsym(g_coreHandle, "retro_serialize");
    g_retro_unserialize = (retro_unserialize_t)dlsym(g_coreHandle, "retro_unserialize");

    if (!g_retro_init || !g_retro_load_game || !g_retro_run) {
        LOGE("Failed to resolve all Libretro symbols");
        dlclose(g_coreHandle);
        g_coreHandle = nullptr;
        return JNI_FALSE;
    }

    // Set callbacks
    g_retro_set_environment(elysium_environment);
    g_retro_set_video_refresh(elysium_video_refresh);
    g_retro_set_audio_sample(elysium_audio_sample);
    g_retro_set_audio_sample_batch(elysium_audio_sample_batch);
    g_retro_set_input_poll(elysium_input_poll);
    g_retro_set_input_state(elysium_input_state);

    // Initialize the core
    g_retro_init();

    // Log core info
    retro_system_info sysInfo = {};
    g_retro_get_system_info(&sysInfo);
    LOGI("Core loaded: %s v%s (extensions: %s)",
         sysInfo.library_name, sysInfo.library_version,
         sysInfo.valid_extensions);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadRom(
    JNIEnv* env, jobject thiz, jstring romPath, jint fd
) {
    if (!g_coreHandle || !g_retro_load_game) {
        LOGE("No core loaded");
        return JNI_FALSE;
    }

    char finalPath[1024];
    if (fd >= 0) {
        // Use the /proc/self/fd trick to bridge SAF descriptors to native fopen
        sprintf(finalPath, "/proc/self/fd/%d", fd);
        LOGI("Loading ROM via File Descriptor: %s", finalPath);
    } else {
        const char* path = env->GetStringUTFChars(romPath, nullptr);
        strncpy(finalPath, path, sizeof(finalPath) - 1);
        env->ReleaseStringUTFChars(romPath, path);
        LOGI("Loading ROM via path: %s", finalPath);
    }

    retro_game_info gameInfo = {};
    gameInfo.path = finalPath;
    gameInfo.data = nullptr;
    gameInfo.size = 0;
    gameInfo.meta = nullptr;

    bool loaded = g_retro_load_game(&gameInfo);

    if (!loaded) {
        LOGE("retro_load_game failed for path: %s", finalPath);
        return JNI_FALSE;
    }

    // Get AV info and initialize the renderer
    g_retro_get_system_av_info(&g_avInfo);
    LOGI("AV Info: %ux%u @ %.2f fps, audio: %.0f Hz",
         g_avInfo.geometry.base_width, g_avInfo.geometry.base_height,
         g_avInfo.timing.fps, g_avInfo.timing.sample_rate);

    // Initialize the zero-copy hardware buffer renderer
    if (!g_renderer.initialize(g_avInfo.geometry.max_width,
                                g_avInfo.geometry.max_height)) {
        LOGW("HardwareBuffer init failed, falling back to standard rendering");
    }

    // Initialize audio engine with the core's sample rate
    if (!g_audioEngine.initialize(
            static_cast<int>(g_avInfo.timing.sample_rate))) {
        LOGW("Audio engine init failed, continuing without audio");
    }

    // Reset frame timing
    g_lastFrameTime = {0, 0};
    g_frameCount = 0;
    g_currentFps.store(g_avInfo.timing.fps);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeRunFrame(
    JNIEnv* env, jobject thiz
) {
    if (!g_retro_run) {
        return;
    }

    // Apply cycle multiplier (for telemetry-driven performance adjustment)
    float multiplier = g_cycleMultiplier.load(std::memory_order_relaxed);
    int framesToRun = static_cast<int>(multiplier);
    if (framesToRun < 1) framesToRun = 1;

    for (int i = 0; i < framesToRun; ++i) {
        g_retro_run();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativePinThreads(
    JNIEnv* env, jobject thiz, jintArray coreIds
) {
    if (!coreIds) {
        // Auto-detect prime cores
        return elysium::pinToPrimeCores() ? JNI_TRUE : JNI_FALSE;
    }

    jint* ids = env->GetIntArrayElements(coreIds, nullptr);
    jsize len = env->GetArrayLength(coreIds);

    bool result = elysium::pinThreadToCores(ids, static_cast<int>(len));

    env->ReleaseIntArrayElements(coreIds, ids, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetCycleMultiplier(
    JNIEnv* env, jobject thiz, jfloat multiplier
) {
    float clamped = multiplier;
    if (clamped < 0.25f) clamped = 0.25f;
    if (clamped > 4.0f) clamped = 4.0f;
    g_cycleMultiplier.store(clamped, std::memory_order_relaxed);
    LOGI("Cycle multiplier set to: %.2f", clamped);
}

JNIEXPORT jdouble JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeGetFps(
    JNIEnv* env, jobject thiz
) {
    return g_currentFps.load(std::memory_order_relaxed);
}

JNIEXPORT jdouble JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeGetFrameTime(
    JNIEnv* env, jobject thiz
) {
    return g_frameTimeMs.load(std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeShutdown(
    JNIEnv* env, jobject thiz
) {
    LOGI("Shutting down Elysium Bridge...");

    // Shutdown audio engine
    g_audioEngine.shutdown();

    // Reset input
    g_inputManager.reset();

    // Release renderer
    g_renderer.release();

    // Unload game and deinit core
    if (g_retro_unload_game) g_retro_unload_game();
    if (g_retro_deinit) g_retro_deinit();

    // Close the dynamic library
    if (g_coreHandle) {
        dlclose(g_coreHandle);
        g_coreHandle = nullptr;
    }

    // Clear function pointers
    g_retro_init = nullptr;
    g_retro_deinit = nullptr;
    g_retro_api_version = nullptr;
    g_retro_get_system_info = nullptr;
    g_retro_get_system_av_info = nullptr;
    g_retro_set_environment = nullptr;
    g_retro_set_video_refresh = nullptr;
    g_retro_set_audio_sample = nullptr;
    g_retro_set_audio_sample_batch = nullptr;
    g_retro_set_input_poll = nullptr;
    g_retro_set_input_state = nullptr;
    g_retro_load_game = nullptr;
    g_retro_unload_game = nullptr;
    g_retro_run = nullptr;
    g_retro_reset = nullptr;

    LOGI("Elysium Bridge shutdown complete");
}

// ═══════════════════════════════════════════════════════════════
// Input JNI — Key Event Routing
// ═══════════════════════════════════════════════════════════════

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeKeyEvent(
    JNIEnv* env, jobject thiz,
    jint keycode, jboolean pressed
) {
    return g_inputManager.handleKeyEvent(keycode, pressed == JNI_TRUE)
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetButton(
    JNIEnv* env, jobject thiz,
    jint retroId, jboolean pressed
) {
    g_inputManager.setButton(static_cast<unsigned>(retroId), pressed == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetGpuDriver(
    JNIEnv* env, jobject thiz, jstring driverPath
) {
    if (driverPath == nullptr) {
        unsetenv("VK_ICD_FILENAMES");
        LOGI("GPU Driver reset to system default");
        return;
    }

    const char* path = env->GetStringUTFChars(driverPath, nullptr);
    if (path && strlen(path) > 0) {
        // Redirect Vulkan loader to the custom ICD (Turnip/Mesa)
        setenv("VK_ICD_FILENAMES", path, 1);
        LOGI("Custom GPU Driver set: %s", path);
    } else {
        unsetenv("VK_ICD_FILENAMES");
        LOGI("GPU Driver reset to system default (empty path)");
    }
    env->ReleaseStringUTFChars(driverPath, path);
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSaveState(
    JNIEnv* env, jobject thiz, jstring path
) {
    if (!g_coreHandle || !g_retro_serialize || !g_retro_serialize_size) return JNI_FALSE;

    size_t size = g_retro_serialize_size();
    if (size == 0) return JNI_FALSE;

    void* buffer = malloc(size);
    if (!buffer) return JNI_FALSE;

    bool success = g_retro_serialize(buffer, size);
    if (success) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        FILE* f = fopen(nativePath, "wb");
        if (f) {
            fwrite(buffer, 1, size, f);
            fclose(f);
            LOGI("State saved to: %s (%zu bytes)", nativePath, size);
        } else {
            success = false;
        }
        env->ReleaseStringUTFChars(path, nativePath);
    }

    free(buffer);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadState(
    JNIEnv* env, jobject thiz, jstring path
) {
    if (!g_coreHandle || !g_retro_unserialize) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    FILE* f = fopen(nativePath, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(path, nativePath);
        return JNI_FALSE;
    }

    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);

    void* buffer = malloc(size);
    if (!buffer) {
        fclose(f);
        env->ReleaseStringUTFChars(path, nativePath);
        return JNI_FALSE;
    }

    fread(buffer, 1, size, f);
    fclose(f);

    bool success = g_retro_unserialize(buffer, size);
    if (success) {
        LOGI("State loaded from: %s", nativePath);
    }

    free(buffer);
    env->ReleaseStringUTFChars(path, nativePath);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetVisualEffect(
    JNIEnv* env, jobject thiz, jint effectId
) {
    g_renderer.setVisualEffect(effectId);
}

} // extern "C"
