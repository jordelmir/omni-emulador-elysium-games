// ═══════════════════════════════════════════════════════════════
// Elysium Console — JNI Bridge (bridge.cpp)
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
#include <type_traits>

#include "libretro.h"
#include "thread_utils.h"
#include "hardware_buffer_renderer.h"
#include "audio_engine.h"
#include "input_manager.h"

#define TAG "ElysiumBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Forward declarations for Haptic Core (Libretro Rumble)
#define RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE 38
enum retro_rumble_effect { RETRO_RUMBLE_STRONG = 0, RETRO_RUMBLE_WEAK = 1, RETRO_RUMBLE_DUMMY = 0x7fffffff };
struct retro_rumble_interface { bool (*set_rumble_state)(unsigned port, enum retro_rumble_effect effect, uint16_t strength); };

extern "C" {
bool core_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength);
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
struct timespec g_lastFrameTime = {0, 0};
double g_fpsAccumulator = 0.0;
int g_fpsSampleCount = 0;

unsigned g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
char g_systemDir[512] = "";
char g_saveDir[512] = "";
}

// ═══════════════════════════════════════════════════════════════
// Libretro Callbacks
// ═══════════════════════════════════════════════════════════════

static bool elysium_environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            g_pixelFormat = *static_cast<const unsigned*>(data);
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
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE: {
            auto* interface = (struct retro_rumble_interface*)data;
            interface->set_rumble_state = core_set_rumble_state;
            return true;
        }
        default: return false;
    }
}

static void elysium_video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (data && g_renderer.isInitialized()) {
        g_renderer.updateFrame(data, width, height, pitch);
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
    const char* path = env->GetStringUTFChars(corePath, nullptr);
    g_coreHandle = dlopen(path, RTLD_LAZY);
    env->ReleaseStringUTFChars(corePath, path);
    if (!g_coreHandle) return JNI_FALSE;

    bool ok = true;
    auto load = [&](auto& ptr, const char* name) {
        ptr = reinterpret_cast<std::remove_reference_t<decltype(ptr)>>(dlsym(g_coreHandle, name));
        if (!ptr) ok = false;
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

    g_retro_set_environment(elysium_environment);
    g_retro_set_video_refresh(elysium_video_refresh);
    g_retro_set_audio_sample(elysium_audio_sample);
    g_retro_set_audio_sample_batch(elysium_audio_sample_batch);
    g_retro_set_input_poll(elysium_input_poll);
    g_retro_set_input_state(elysium_input_state);
    g_retro_init();

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

    retro_game_info gameInfo = { finalPath, nullptr, 0, nullptr };
    if (!g_retro_load_game(&gameInfo)) return JNI_FALSE;

    retro_system_av_info av;
    g_retro_get_system_av_info(&av);
    g_renderer.initialize(av.geometry.max_width, av.geometry.max_height);
    g_audioEngine.initialize(static_cast<int>(av.timing.sample_rate));

    return JNI_TRUE;
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
