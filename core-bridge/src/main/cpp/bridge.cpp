// ═══════════════════════════════════════════════════════════════
// Elysium Console — JNI Bridge (bridge.cpp)
// ═══════════════════════════════════════════════════════════════

#include <jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#include <atomic>
#include <mutex>
#include <type_traits>
#include <unordered_map>
#include <string>
#include <vector>

#include "libretro.h"
#include "thread_utils.h"
#include "hardware_buffer_renderer.h"
#include "audio_engine.h"
#include "input_manager.h"

#define TAG "ElysiumBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Forward declarations for Haptic Core (Libretro Rumble)
#define RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE 38
enum retro_rumble_effect { RETRO_RUMBLE_STRONG = 0, RETRO_RUMBLE_WEAK = 1, RETRO_RUMBLE_DUMMY = 0x7fffffff };
struct retro_rumble_interface { bool (*set_rumble_state)(unsigned port, enum retro_rumble_effect effect, uint16_t strength); };

extern "C" {
bool core_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength);
}

// ═══════════════════════════════════════════════════════════════
// Libretro Log Callback — Routes core logs to Android Logcat
// ═══════════════════════════════════════════════════════════════
static void elysium_log_printf(enum retro_log_level level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO:  prio = ANDROID_LOG_INFO;  break;
        case RETRO_LOG_WARN:  prio = ANDROID_LOG_WARN;  break;
        case RETRO_LOG_ERROR: prio = ANDROID_LOG_ERROR; break;
        default: break;
    }
    __android_log_vprint(prio, "LibretroCore", fmt, args);
    va_end(args);
}

// ═══════════════════════════════════════════════════════════════
// Global State
// ═══════════════════════════════════════════════════════════════

namespace {
void* g_coreHandle = nullptr;
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

jmethodID g_onRumbleMethod = nullptr;
jobject g_bridgeObject = nullptr;
JavaVM* g_jvm = nullptr;

elysium::HardwareBufferRenderer g_renderer;
elysium::AudioEngine g_audioEngine;
elysium::InputManager g_inputManager;

std::atomic<float> g_cycleMultiplier{1.0f};
std::atomic<double> g_currentFps{0.0};
std::atomic<double> g_frameTimeMs{0.0};
std::atomic<double> g_targetFps{60.0};
struct timespec g_lastFrameTime = {0, 0};
double g_fpsAccumulator = 0.0;
int g_fpsSampleCount = 0;

unsigned g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
char g_systemDir[512] = "";
char g_saveDir[512] = "";

// ── Core Variable System ────────────────────────────────────
// Stores key/value pairs set by cores via SET_VARIABLES/GET_VARIABLE.
// This is CRITICAL for core compatibility — without it, cores cannot
// configure their internal settings and may produce black screens.
std::unordered_map<std::string, std::string> g_coreVariables;
std::unordered_map<std::string, std::string> g_coreVariableDefaults;
std::atomic<bool> g_variablesChanged{false};
std::mutex g_variablesMutex;

// ── Pixel Conversion Buffer ─────────────────────────────────
// Pre-allocated buffer for format conversion (RGB565/0RGB1555 → RGBA8888).
// This avoids per-frame heap allocation.
std::vector<uint32_t> g_conversionBuffer;
}

// ═══════════════════════════════════════════════════════════════
// Libretro Callbacks
// ═══════════════════════════════════════════════════════════════

static bool elysium_environment(unsigned cmd, void* data) {
    // Strip the EXPERIMENTAL flag bit that some cores set
    unsigned masked = cmd & 0xFF;

    switch (masked) {
        // ── Pixel Format Negotiation ────────────────────────
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            unsigned fmt = *static_cast<const unsigned*>(data);
            g_pixelFormat = fmt;
            LOGI("Core requested pixel format: %s",
                 fmt == RETRO_PIXEL_FORMAT_XRGB8888 ? "XRGB8888" :
                 fmt == RETRO_PIXEL_FORMAT_RGB565 ? "RGB565" : "0RGB1555");
            return true;
        }

        // ── Directory Queries ───────────────────────────────
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            *static_cast<const char**>(data) = g_systemDir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            *static_cast<const char**>(data) = g_saveDir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY: {
            *static_cast<const char**>(data) = g_systemDir;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LIBRETRO_PATH: {
            *static_cast<const char**>(data) = nullptr;
            return true;
        }

        // ── Core Capabilities ───────────────────────────────
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            // CRITICAL: Most cores REQUIRE this to be true.
            // Without it, they will refuse to dupe frames and
            // may send NULL data every other frame → flickering.
            *static_cast<bool*>(data) = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_OVERSCAN: {
            *static_cast<bool*>(data) = false;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME: {
            return true; // Acknowledge but don't need to act
        }
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS: {
            return false; // Not supported
        }
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: {
            return true; // We support input bitmasks
        }

        // ── Variable System (CRITICAL for compatibility) ────
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            // Core is registering its configuration options.
            // Parse the key/value pairs and store defaults.
            const struct retro_variable* vars = static_cast<const struct retro_variable*>(data);
            std::lock_guard<std::mutex> lock(g_variablesMutex);
            while (vars && vars->key) {
                std::string key = vars->key;
                std::string fullValue = vars->value ? vars->value : "";
                // Value format: "Description; option1|option2|option3"
                // Default is the first option after the semicolon
                size_t semicolon = fullValue.find(';');
                if (semicolon != std::string::npos) {
                    std::string options = fullValue.substr(semicolon + 2);
                    size_t pipe = options.find('|');
                    std::string defaultVal = (pipe != std::string::npos)
                        ? options.substr(0, pipe)
                        : options;
                    g_coreVariableDefaults[key] = defaultVal;
                    // Only set if not already overridden
                    if (g_coreVariables.find(key) == g_coreVariables.end()) {
                        g_coreVariables[key] = defaultVal;
                    }
                    LOGI("Core variable: %s = %s", key.c_str(), g_coreVariables[key].c_str());
                }
                vars++;
            }
            g_variablesChanged.store(true, std::memory_order_relaxed);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable* var = static_cast<struct retro_variable*>(data);
            if (!var || !var->key) return false;
            std::lock_guard<std::mutex> lock(g_variablesMutex);
            auto it = g_coreVariables.find(var->key);
            if (it != g_coreVariables.end()) {
                var->value = it->second.c_str();
                return true;
            }
            var->value = nullptr;
            return false;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            bool* updated = static_cast<bool*>(data);
            *updated = g_variablesChanged.exchange(false, std::memory_order_relaxed);
            return true;
        }

        // ── Logging Interface ───────────────────────────────
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback* cb = static_cast<struct retro_log_callback*>(data);
            cb->log = elysium_log_printf;
            return true;
        }

        // ── Input Descriptors ───────────────────────────────
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS: {
            // Acknowledge — we don't need to store these but must
            // return true so the core knows we received them.
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO: {
            return true; // Acknowledge
        }
        case RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES: {
            // Report that we support joypad only
            uint64_t* caps = static_cast<uint64_t*>(data);
            *caps = (1 << RETRO_DEVICE_JOYPAD);
            return true;
        }

        // ── Haptic Core ─────────────────────────────────────
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE: {
            auto* iface = static_cast<struct retro_rumble_interface*>(data);
            iface->set_rumble_state = core_set_rumble_state;
            return true;
        }

        // ── Language & Region ───────────────────────────────
        case RETRO_ENVIRONMENT_GET_LANGUAGE: {
            unsigned* lang = static_cast<unsigned*>(data);
            *lang = RETRO_LANGUAGE_SPANISH;
            return true;
        }

        // ── Geometry & AV Changes ───────────────────────────
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const struct retro_game_geometry {
                unsigned base_width, base_height, max_width, max_height;
                float aspect_ratio;
            }* geom = static_cast<const struct retro_game_geometry*>(data);
            LOGI("Core geometry change: %ux%u", geom->base_width, geom->base_height);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            return true; // Acknowledge mid-game AV changes
        }

        // ── Performance & Memory ────────────────────────────
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL: {
            return true; // Acknowledge
        }
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
            return true; // Acknowledge
        }
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS: {
            return true;
        }
        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE: {
            return false; // Performance counters not implemented
        }

        // ── Messages ────────────────────────────────────────
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            struct retro_message { const char* msg; unsigned frames; };
            const struct retro_message* msg = static_cast<const struct retro_message*>(data);
            if (msg && msg->msg) {
                LOGI("Core message: %s", msg->msg);
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_ROTATION: {
            return true;
        }
        case RETRO_ENVIRONMENT_SHUTDOWN: {
            LOGI("Core requested shutdown");
            return true;
        }

        default:
            LOGW("Unhandled environment cmd: %u (raw: %u)", masked, cmd);
            return false;
    }
}

// ═══════════════════════════════════════════════════════════════
// Pixel Format Conversion Engine
// ═══════════════════════════════════════════════════════════════
// Converts all libretro pixel formats to RGBA8888 which is what
// AHardwareBuffer expects. Without this, any core using RGB565
// (the default for most 8/16-bit cores) produces a BLACK SCREEN.

static const void* elysium_convert_frame(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        // XRGB8888 → RGBA8888: just need to set alpha to 0xFF
        size_t totalPixels = (size_t)width * height;
        if (g_conversionBuffer.size() < totalPixels) {
            g_conversionBuffer.resize(totalPixels);
        }
        const uint8_t* src = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            const uint32_t* srcRow = reinterpret_cast<const uint32_t*>(src + y * pitch);
            uint32_t* dstRow = &g_conversionBuffer[y * width];
            for (unsigned x = 0; x < width; ++x) {
                uint32_t px = srcRow[x]; // 0xXXRRGGBB
                uint8_t r = (px >> 16) & 0xFF;
                uint8_t g = (px >> 8)  & 0xFF;
                uint8_t b = px         & 0xFF;
                dstRow[x] = (0xFF << 24) | (b << 16) | (g << 8) | r; // ABGR for RGBA8888 layout
            }
        }
        return g_conversionBuffer.data();
    }
    else if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        // RGB565 → RGBA8888
        size_t totalPixels = (size_t)width * height;
        if (g_conversionBuffer.size() < totalPixels) {
            g_conversionBuffer.resize(totalPixels);
        }
        const uint8_t* src = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            const uint16_t* srcRow = reinterpret_cast<const uint16_t*>(src + y * pitch);
            uint32_t* dstRow = &g_conversionBuffer[y * width];
            for (unsigned x = 0; x < width; ++x) {
                uint16_t px = srcRow[x];
                uint8_t r = ((px >> 11) & 0x1F) << 3;
                uint8_t g = ((px >> 5)  & 0x3F) << 2;
                uint8_t b = (px         & 0x1F) << 3;
                // Fill lower bits for accurate color reproduction
                r |= r >> 5;
                g |= g >> 6;
                b |= b >> 5;
                dstRow[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
        }
        return g_conversionBuffer.data();
    }
    else { // RETRO_PIXEL_FORMAT_0RGB1555
        // 0RGB1555 → RGBA8888
        size_t totalPixels = (size_t)width * height;
        if (g_conversionBuffer.size() < totalPixels) {
            g_conversionBuffer.resize(totalPixels);
        }
        const uint8_t* src = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            const uint16_t* srcRow = reinterpret_cast<const uint16_t*>(src + y * pitch);
            uint32_t* dstRow = &g_conversionBuffer[y * width];
            for (unsigned x = 0; x < width; ++x) {
                uint16_t px = srcRow[x];
                uint8_t r = ((px >> 10) & 0x1F) << 3;
                uint8_t g = ((px >> 5)  & 0x1F) << 3;
                uint8_t b = (px         & 0x1F) << 3;
                r |= r >> 5;
                g |= g >> 5;
                b |= b >> 5;
                dstRow[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
        }
        return g_conversionBuffer.data();
    }
}

static void elysium_video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (data && g_renderer.isInitialized()) {
        // Convert pixel format to RGBA8888 before passing to renderer
        const void* rgbaData = elysium_convert_frame(data, width, height, pitch);
        // After conversion, pitch is always width * 4 (RGBA8888)
        g_renderer.updateFrame(rgbaData, width, height, width * 4);
    }

    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    if (g_lastFrameTime.tv_sec != 0) {
        double deltaMs = (now.tv_sec - g_lastFrameTime.tv_sec) * 1000.0 + (now.tv_nsec - g_lastFrameTime.tv_nsec) / 1000000.0;
        g_frameTimeMs.store(deltaMs, std::memory_order_relaxed);
        if (deltaMs > 0.0) {
            g_fpsAccumulator += (1000.0 / deltaMs);
            if (++g_fpsSampleCount >= 30) {
                g_currentFps.store(g_fpsAccumulator / 30.0, std::memory_order_relaxed);
                g_fpsAccumulator = 0.0; g_fpsSampleCount = 0;
            }
        }
    }
    g_lastFrameTime = now;
}

static void elysium_audio_sample(int16_t left, int16_t right) {
    if (g_audioEngine.isInitialized()) g_audioEngine.queueSample(left, right);
}

static size_t elysium_audio_sample_batch(const int16_t* data, size_t frames) {
    return g_audioEngine.isInitialized() ? g_audioEngine.queueSamples(data, frames) : frames;
}

static void elysium_input_poll() {}

static int16_t elysium_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    return g_inputManager.getInputState(port, device, index, id);
}

// ═══════════════════════════════════════════════════════════════
// JNI Exports
// ═══════════════════════════════════════════════════════════════

extern "C" {

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeInit(JNIEnv* env, jobject thiz, jstring systemPath, jstring savePath) {
    const char* sys = env->GetStringUTFChars(systemPath, nullptr);
    const char* sav = env->GetStringUTFChars(savePath, nullptr);
    strncpy(g_systemDir, sys, sizeof(g_systemDir) - 1);
    strncpy(g_saveDir, sav, sizeof(g_saveDir) - 1);
    env->ReleaseStringUTFChars(systemPath, sys);
    env->ReleaseStringUTFChars(savePath, sav);
    if (g_bridgeObject) env->DeleteGlobalRef(g_bridgeObject);
    g_bridgeObject = env->NewGlobalRef(thiz);
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadCore(JNIEnv* env, jobject thiz, jstring corePath) {
    if (g_coreHandle) dlclose(g_coreHandle);

    // Clear stale state from previous core session
    {
        std::lock_guard<std::mutex> lock(g_variablesMutex);
        g_coreVariables.clear();
        g_coreVariableDefaults.clear();
    }
    g_variablesChanged.store(false, std::memory_order_relaxed);
    g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565; // Reset to default
    g_conversionBuffer.clear();
    g_inputManager.reset();

    const char* path = env->GetStringUTFChars(corePath, nullptr);
    LOGI("Loading core: %s", path);
    g_coreHandle = dlopen(path, RTLD_LAZY);
    if (!g_coreHandle) {
        LOGE("dlopen failed: %s", dlerror());
    }
    env->ReleaseStringUTFChars(corePath, path);
    if (!g_coreHandle) return JNI_FALSE;

    bool ok = true;
    auto load = [&](auto& ptr, const char* name) {
        ptr = reinterpret_cast<std::remove_reference_t<decltype(ptr)>>(dlsym(g_coreHandle, name));
        if (!ptr) { LOGE("Missing symbol: %s", name); ok = false; }
    };

    load(g_retro_init, "retro_init");
    load(g_retro_deinit, "retro_deinit");
    load(g_retro_api_version, "retro_api_version");
    load(g_retro_get_system_info, "retro_get_system_info");
    load(g_retro_get_system_av_info, "retro_get_system_av_info");
    load(g_retro_set_environment, "retro_set_environment");
    load(g_retro_set_video_refresh, "retro_set_video_refresh");
    load(g_retro_set_audio_sample, "retro_set_audio_sample");
    load(g_retro_set_audio_sample_batch, "retro_set_audio_sample_batch");
    load(g_retro_set_input_poll, "retro_set_input_poll");
    load(g_retro_set_input_state, "retro_set_input_state");
    load(g_retro_load_game, "retro_load_game");
    load(g_retro_unload_game, "retro_unload_game");
    load(g_retro_run, "retro_run");
    load(g_retro_reset, "retro_reset");
    load(g_retro_serialize_size, "retro_serialize_size");
    load(g_retro_serialize, "retro_serialize");
    load(g_retro_unserialize, "retro_unserialize");

    if (!ok) return JNI_FALSE;

    // Set callbacks BEFORE init — some cores call environment during init
    g_retro_set_environment(elysium_environment);
    g_retro_set_video_refresh(elysium_video_refresh);
    g_retro_set_audio_sample(elysium_audio_sample);
    g_retro_set_audio_sample_batch(elysium_audio_sample_batch);
    g_retro_set_input_poll(elysium_input_poll);
    g_retro_set_input_state(elysium_input_state);
    g_retro_init();

    LOGI("Core loaded successfully. API version: %u", g_retro_api_version());
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadRom(JNIEnv* env, jobject thiz, jstring romPath, jint fd) {
    char finalPath[1024];
    if (fd >= 0) sprintf(finalPath, "/proc/self/fd/%d", fd);
    else {
        const char* path = env->GetStringUTFChars(romPath, nullptr);
        strncpy(finalPath, path, sizeof(finalPath) - 1);
        env->ReleaseStringUTFChars(romPath, path);
    }

    LOGI("Loading ROM: %s (fd: %d, pixel format: %u)", finalPath, fd, g_pixelFormat);

    retro_game_info gameInfo = { finalPath, nullptr, 0, nullptr };
    if (!g_retro_load_game(&gameInfo)) {
        LOGE("retro_load_game FAILED for: %s", finalPath);
        return JNI_FALSE;
    }

    retro_system_av_info av;
    g_retro_get_system_av_info(&av);

    LOGI("AV Info — Resolution: %ux%u (max %ux%u), FPS: %.2f, Sample Rate: %.0f",
         av.geometry.base_width, av.geometry.base_height,
         av.geometry.max_width, av.geometry.max_height,
         av.timing.fps, av.timing.sample_rate);

    // Store target FPS for frame pacing
    g_targetFps.store(av.timing.fps, std::memory_order_relaxed);

    g_renderer.initialize(av.geometry.max_width, av.geometry.max_height);
    g_audioEngine.initialize(static_cast<int>(av.timing.sample_rate));

    LOGI("ROM loaded. Pixel format: %s",
         g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888 ? "XRGB8888" :
         g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565 ? "RGB565" : "0RGB1555");

    return JNI_TRUE;
}

JNIEXPORT jdouble JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeGetTargetFps(JNIEnv* env, jobject thiz) {
    return g_targetFps.load(std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeRunFrame(JNIEnv* env, jobject thiz) {
    if (!g_retro_run) return;
    int count = static_cast<int>(g_cycleMultiplier.load(std::memory_order_relaxed));
    if (count < 1) count = 1;
    for (int i = 0; i < count; ++i) g_retro_run();
}

JNIEXPORT void JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeSetCycleMultiplier(JNIEnv* env, jobject thiz, jfloat m) {
    g_cycleMultiplier.store(m, std::memory_order_relaxed);
}

JNIEXPORT jdouble JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeGetFps(JNIEnv* env, jobject thiz) {
    return g_currentFps.load(std::memory_order_relaxed);
}

JNIEXPORT jdouble JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeGetFrameTime(JNIEnv* env, jobject thiz) {
    return g_frameTimeMs.load(std::memory_order_relaxed);
}

JNIEXPORT jboolean JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeSaveState(JNIEnv* env, jobject thiz, jstring path) {
    if (!g_retro_serialize || !g_retro_serialize_size) return JNI_FALSE;
    size_t size = g_retro_serialize_size();
    void* buf = malloc(size);
    if (!buf) return JNI_FALSE;
    bool ok = g_retro_serialize(buf, size);
    if (ok) {
        const char* p = env->GetStringUTFChars(path, nullptr);
        FILE* f = fopen(p, "wb");
        if (f) { fwrite(buf, 1, size, f); fclose(f); }
        env->ReleaseStringUTFChars(path, p);
    }
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeLoadState(JNIEnv* env, jobject thiz, jstring path) {
    if (!g_retro_unserialize) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(path, nullptr);
    FILE* f = fopen(p, "rb");
    if (!f) { env->ReleaseStringUTFChars(path, p); return JNI_FALSE; }
    fseek(f, 0, SEEK_END); size_t size = ftell(f); fseek(f, 0, SEEK_SET);
    void* buf = malloc(size); fread(buf, 1, size, f); fclose(f);
    bool ok = g_retro_unserialize(buf, size);
    free(buf); env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_elysium_console_bridge_ElysiumBridge_nativeReset(JNIEnv* env, jobject thiz) {
    if (g_retro_reset) g_retro_reset();
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetVisualEffect(JNIEnv* env, jobject thiz, jint effectId) {
    g_renderer.setVisualEffect(effectId);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeSetUpscaler(JNIEnv* env, jobject thiz, jint mode) {
    g_renderer.setUpscaleMode(mode);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeRenderFrame(JNIEnv* env, jobject thiz, jint width, jint height) {
    g_renderer.render(width, height);
}

JNIEXPORT void JNICALL
Java_com_elysium_console_bridge_ElysiumBridge_nativeShutdown(JNIEnv* env, jobject thiz) {
    if (g_retro_unload_game) g_retro_unload_game();
    if (g_retro_deinit) g_retro_deinit();
    if (g_coreHandle) { dlclose(g_coreHandle); g_coreHandle = nullptr; }
    g_renderer.release();
}

bool core_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    if (!g_jvm || !g_bridgeObject || !g_onRumbleMethod) return false;
    JNIEnv* env;
    bool detach = false;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return false;
        detach = true;
    }
    if (env) env->CallVoidMethod(g_bridgeObject, g_onRumbleMethod, (jint)strength);
    if (detach) g_jvm->DetachCurrentThread();
    return true;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass("com/elysium/console/bridge/ElysiumBridge");
    g_onRumbleMethod = env->GetMethodID(clazz, "onRumble", "(I)V");
    return JNI_VERSION_1_6;
}

} // extern "C"
