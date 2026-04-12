// ═══════════════════════════════════════════════════════════════
// Elysium Console — Input Manager
// ═══════════════════════════════════════════════════════════════
// Maps Android key events and touch inputs to Libretro joypad
// button states. Thread-safe for concurrent read/write.
// ═══════════════════════════════════════════════════════════════

#ifndef ELYSIUM_INPUT_MANAGER_H
#define ELYSIUM_INPUT_MANAGER_H

#include <atomic>
#include <android/log.h>
#include <android/keycodes.h>
#include "libretro.h"

#define INPUT_TAG "ElysiumInput"
#define INPUT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, INPUT_TAG, __VA_ARGS__)

namespace elysium {

/**
 * Manages input state mapping from Android keycodes to Libretro
 * joypad button IDs. Uses atomic bitmask for thread-safe access.
 */
class InputManager {
public:
    InputManager() : mButtonState(0) {}

    /**
     * Sets a button state (pressed or released).
     * @param retroId Libretro button ID (RETRO_DEVICE_ID_JOYPAD_*)
     * @param pressed true if pressed, false if released
     */
    void setButton(unsigned retroId, bool pressed) {
        if (retroId > 15) return;
        uint16_t mask = static_cast<uint16_t>(1u << retroId);
        if (pressed) {
            mButtonState.fetch_or(mask, std::memory_order_relaxed);
        } else {
            mButtonState.fetch_and(~mask, std::memory_order_relaxed);
        }
    }

    /**
     * Gets the state of a specific button.
     * @param retroId Libretro button ID
     * @return 1 if pressed, 0 if released
     */
    int16_t getButton(unsigned retroId) const {
        if (retroId > 15) return 0;
        uint16_t mask = static_cast<uint16_t>(1u << retroId);
        return (mButtonState.load(std::memory_order_relaxed) & mask) ? 1 : 0;
    }

    /**
     * Maps an Android keycode to a Libretro button press/release.
     * @param keycode Android keycode (AKEYCODE_*)
     * @param pressed true if key down, false if key up
     * @return true if the keycode was mapped
     */
    bool handleKeyEvent(int32_t keycode, bool pressed) {
        int retroId = mapAndroidKeyToRetro(keycode);
        if (retroId < 0) return false;
        setButton(static_cast<unsigned>(retroId), pressed);
        return true;
    }

    /**
     * Resets all button states to released.
     */
    void reset() {
        mButtonState.store(0, std::memory_order_relaxed);
    }

    /**
     * Libretro input_state callback implementation.
     * Returns the state of the requested input for port 0.
     */
    int16_t getInputState(unsigned port, unsigned device,
                           unsigned index, unsigned id) const {
        if (port != 0) return 0;
        if (device != RETRO_DEVICE_JOYPAD) return 0;
        return getButton(id);
    }

private:
    std::atomic<uint16_t> mButtonState;

    /**
     * Maps Android keycodes to Libretro joypad button IDs.
     * Supports both physical gamepad buttons and keyboard keys.
     */
    static int mapAndroidKeyToRetro(int32_t keycode) {
        switch (keycode) {
            // --- D-PAD ---
            case AKEYCODE_DPAD_UP:     return RETRO_DEVICE_ID_JOYPAD_UP;
            case AKEYCODE_DPAD_DOWN:   return RETRO_DEVICE_ID_JOYPAD_DOWN;
            case AKEYCODE_DPAD_LEFT:   return RETRO_DEVICE_ID_JOYPAD_LEFT;
            case AKEYCODE_DPAD_RIGHT:  return RETRO_DEVICE_ID_JOYPAD_RIGHT;

            // --- Face Buttons (Gamepad) ---
            case AKEYCODE_BUTTON_A:    return RETRO_DEVICE_ID_JOYPAD_B;
            case AKEYCODE_BUTTON_B:    return RETRO_DEVICE_ID_JOYPAD_A;
            case AKEYCODE_BUTTON_X:    return RETRO_DEVICE_ID_JOYPAD_Y;
            case AKEYCODE_BUTTON_Y:    return RETRO_DEVICE_ID_JOYPAD_X;

            // --- Shoulder Buttons ---
            case AKEYCODE_BUTTON_L1:   return RETRO_DEVICE_ID_JOYPAD_L;
            case AKEYCODE_BUTTON_R1:   return RETRO_DEVICE_ID_JOYPAD_R;
            case AKEYCODE_BUTTON_L2:   return RETRO_DEVICE_ID_JOYPAD_L2;
            case AKEYCODE_BUTTON_R2:   return RETRO_DEVICE_ID_JOYPAD_R2;

            // --- Menu Buttons ---
            case AKEYCODE_BUTTON_SELECT: return RETRO_DEVICE_ID_JOYPAD_SELECT;
            case AKEYCODE_BUTTON_START:  return RETRO_DEVICE_ID_JOYPAD_START;

            // --- Thumbstick Clicks ---
            case AKEYCODE_BUTTON_THUMBL: return RETRO_DEVICE_ID_JOYPAD_L3;
            case AKEYCODE_BUTTON_THUMBR: return RETRO_DEVICE_ID_JOYPAD_R3;

            // --- Keyboard fallback mappings ---
            case AKEYCODE_W:           return RETRO_DEVICE_ID_JOYPAD_UP;
            case AKEYCODE_S:           return RETRO_DEVICE_ID_JOYPAD_DOWN;
            case AKEYCODE_A:           return RETRO_DEVICE_ID_JOYPAD_LEFT;
            case AKEYCODE_D:           return RETRO_DEVICE_ID_JOYPAD_RIGHT;
            case AKEYCODE_K:           return RETRO_DEVICE_ID_JOYPAD_B;
            case AKEYCODE_L:           return RETRO_DEVICE_ID_JOYPAD_A;
            case AKEYCODE_J:           return RETRO_DEVICE_ID_JOYPAD_Y;
            case AKEYCODE_I:           return RETRO_DEVICE_ID_JOYPAD_X;
            case AKEYCODE_Q:           return RETRO_DEVICE_ID_JOYPAD_L;
            case AKEYCODE_E:           return RETRO_DEVICE_ID_JOYPAD_R;
            case AKEYCODE_ENTER:       return RETRO_DEVICE_ID_JOYPAD_START;
            case AKEYCODE_SPACE:       return RETRO_DEVICE_ID_JOYPAD_SELECT;

            default: return -1;
        }
    }
};

} // namespace elysium

#endif // ELYSIUM_INPUT_MANAGER_H
