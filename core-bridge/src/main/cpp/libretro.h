// ═══════════════════════════════════════════════════════════════
// Elysium Console — Libretro API Header (v1 Specification)
// ═══════════════════════════════════════════════════════════════
// This header defines the Libretro API function signatures used
// by the bridge to interface with dynamically loaded emulator
// cores (.so files). The actual symbols are resolved at runtime
// via dlopen/dlsym.
// ═══════════════════════════════════════════════════════════════

#ifndef ELYSIUM_LIBRETRO_H
#define ELYSIUM_LIBRETRO_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ─── Pixel Formats ───────────────────────────────────────────
#define RETRO_PIXEL_FORMAT_0RGB1555  0
#define RETRO_PIXEL_FORMAT_XRGB8888  1
#define RETRO_PIXEL_FORMAT_RGB565    2

// ─── Device Types ────────────────────────────────────────────
#define RETRO_DEVICE_NONE       0
#define RETRO_DEVICE_JOYPAD     1
#define RETRO_DEVICE_MOUSE      2
#define RETRO_DEVICE_KEYBOARD   3
#define RETRO_DEVICE_ANALOG     5

// ─── Joypad Buttons ─────────────────────────────────────────
#define RETRO_DEVICE_ID_JOYPAD_B        0
#define RETRO_DEVICE_ID_JOYPAD_Y        1
#define RETRO_DEVICE_ID_JOYPAD_SELECT   2
#define RETRO_DEVICE_ID_JOYPAD_START    3
#define RETRO_DEVICE_ID_JOYPAD_UP       4
#define RETRO_DEVICE_ID_JOYPAD_DOWN     5
#define RETRO_DEVICE_ID_JOYPAD_LEFT     6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT    7
#define RETRO_DEVICE_ID_JOYPAD_A        8
#define RETRO_DEVICE_ID_JOYPAD_X        9
#define RETRO_DEVICE_ID_JOYPAD_L       10
#define RETRO_DEVICE_ID_JOYPAD_R       11
#define RETRO_DEVICE_ID_JOYPAD_L2      12
#define RETRO_DEVICE_ID_JOYPAD_R2      13
#define RETRO_DEVICE_ID_JOYPAD_L3      14
#define RETRO_DEVICE_ID_JOYPAD_R3      15

// ─── Environment Commands ───────────────────────────────────
#define RETRO_ENVIRONMENT_SET_ROTATION                1
#define RETRO_ENVIRONMENT_GET_OVERSCAN                2
#define RETRO_ENVIRONMENT_GET_CAN_DUPE                3
#define RETRO_ENVIRONMENT_SET_MESSAGE                 6
#define RETRO_ENVIRONMENT_SHUTDOWN                    7
#define RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL       8
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY        9
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT           10
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS      11
#define RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK      12
#define RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE 13
#define RETRO_ENVIRONMENT_SET_HW_RENDER             14
#define RETRO_ENVIRONMENT_GET_VARIABLE              15
#define RETRO_ENVIRONMENT_SET_VARIABLES             16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE        17
#define RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME       18
#define RETRO_ENVIRONMENT_GET_LIBRETRO_PATH         19
#define RETRO_ENVIRONMENT_SET_FRAME_TIME_CALLBACK   21
#define RETRO_ENVIRONMENT_SET_AUDIO_CALLBACK        22
#define RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE_ENV   23
#define RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES 24
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE          27
#define RETRO_ENVIRONMENT_GET_PERF_INTERFACE         28
#define RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY  30
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY         31
#define RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO         32
#define RETRO_ENVIRONMENT_SET_CONTROLLER_INFO        35
#define RETRO_ENVIRONMENT_SET_MEMORY_MAPS            36
#define RETRO_ENVIRONMENT_SET_GEOMETRY               37
#define RETRO_ENVIRONMENT_GET_LANGUAGE               39
#define RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS   42
#define RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS   44
#define RETRO_ENVIRONMENT_GET_INPUT_BITMASKS         52

// ─── Log Levels ─────────────────────────────────────────────
enum retro_log_level {
    RETRO_LOG_DEBUG = 0,
    RETRO_LOG_INFO,
    RETRO_LOG_WARN,
    RETRO_LOG_ERROR,
    RETRO_LOG_DUMMY = INT_MAX
};

typedef void (*retro_log_printf_t)(enum retro_log_level level, const char *fmt, ...);

struct retro_log_callback {
    retro_log_printf_t log;
};

// ─── Input Descriptors ─────────────────────────────────────
struct retro_input_descriptor {
    unsigned port;
    unsigned device;
    unsigned index;
    unsigned id;
    const char *description;
};

// ─── Language Enum ──────────────────────────────────────────
#define RETRO_LANGUAGE_ENGLISH              0
#define RETRO_LANGUAGE_SPANISH              6

// ─── Structures ─────────────────────────────────────────────

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

struct retro_system_av_info {
    struct {
        unsigned base_width;
        unsigned base_height;
        unsigned max_width;
        unsigned max_height;
        float aspect_ratio;
    } geometry;
    struct {
        double fps;
        double sample_rate;
    } timing;
};

struct retro_game_info {
    const char *path;
    const void *data;
    size_t size;
    const char *meta;
};

struct retro_variable {
    const char *key;
    const char *value;
};

// ─── Callback Typedefs ──────────────────────────────────────

typedef bool (*retro_environment_t)(unsigned cmd, void *data);
typedef void (*retro_video_refresh_t)(const void *data, unsigned width, unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t *data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device, unsigned index, unsigned id);

// ─── Core Function Typedefs (resolved via dlsym) ───────────

typedef void (*retro_init_t)(void);
typedef void (*retro_deinit_t)(void);
typedef unsigned (*retro_api_version_t)(void);
typedef void (*retro_get_system_info_t)(struct retro_system_info *info);
typedef void (*retro_get_system_av_info_t)(struct retro_system_av_info *info);
typedef void (*retro_set_environment_t)(retro_environment_t);
typedef void (*retro_set_video_refresh_t)(retro_video_refresh_t);
typedef void (*retro_set_audio_sample_t)(retro_audio_sample_t);
typedef void (*retro_set_audio_sample_batch_t)(retro_audio_sample_batch_t);
typedef void (*retro_set_input_poll_t)(retro_input_poll_t);
typedef void (*retro_set_input_state_t)(retro_input_state_t);
typedef bool (*retro_load_game_t)(const struct retro_game_info *game);
typedef void (*retro_unload_game_t)(void);
typedef void (*retro_run_t)(void);
typedef void (*retro_reset_t)(void);
typedef size_t (*retro_serialize_size_t)(void);
typedef bool (*retro_serialize_t)(void *data, size_t size);
typedef bool (*retro_unserialize_t)(const void *data, size_t size);

#ifdef __cplusplus
}
#endif

#endif // ELYSIUM_LIBRETRO_H
