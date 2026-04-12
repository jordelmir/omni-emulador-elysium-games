package com.elysium.console.bridge

/**
 * Elysium Bridge — Kotlin interface to the native C++ emulation engine.
 *
 * This singleton manages the JNI boundary between the Android application
 * and the Libretro-based emulation core. All native calls are routed
 * through this object, providing a clean, type-safe API for the upper layers.
 *
 * Critical features exposed:
 * - Core loading via dlopen (supports any Libretro-compatible .so)
 * - Thread pinning to high-performance CPU cores (big.LITTLE aware)
 * - Zero-copy frame rendering via AHardwareBuffer
 * - Dynamic cycle multiplier for telemetry-driven performance tuning
 * - Real-time FPS and frame-time metrics
 */
object ElysiumBridge {

    init {
        System.loadLibrary("elysium-bridge")
    }

    /**
     * Initializes the native bridge with system and save directory paths.
     * Must be called before any other native method.
     *
     * @param systemPath Path for system BIOS/firmware files
     * @param savePath   Path for save states and SRAM
     */
    external fun nativeInit(systemPath: String, savePath: String)

    /**
     * Loads a Libretro-compatible emulator core (.so file).
     * This resolves all Libretro API symbols via dlsym and calls retro_init.
     *
     * @param corePath Absolute path to the core shared library
     * @return true if the core loaded successfully
     */
    external fun nativeLoadCore(corePath: String): Boolean

    /**
     * Loads a ROM file into the currently active core.
     * Initializes the AHardwareBuffer renderer based on the core's AV info.
     *
     * @param romPath Absolute path to the ROM file
     * @return true if the ROM loaded successfully
     */
    external fun nativeLoadRom(romPath: String): Boolean

    /**
     * Runs one (or more) emulation frames.
     * The number of frames is controlled by the cycle multiplier.
     * Frame data is written to the AHardwareBuffer for zero-copy GPU access.
     */
    external fun nativeRunFrame()

    /**
     * Pins the emulation thread to specific CPU cores using sched_setaffinity.
     * Pass null to auto-detect and pin to the highest-performance cores.
     *
     * @param coreIds Array of CPU core indices, or null for auto-detect
     * @return true if thread pinning succeeded
     */
    external fun nativePinThreads(coreIds: IntArray?): Boolean

    /**
     * Sets the dynamic cycle multiplier for frame pacing.
     * Values > 1.0 run multiple frames per call (fast-forward).
     * Values < 1.0 reduce frame rate (throttle). Clamped to [0.25, 4.0].
     *
     * @param multiplier Frame multiplier value
     */
    external fun nativeSetCycleMultiplier(multiplier: Float)

    /**
     * Returns the current frames-per-second as a rolling average.
     * Averaged over 30 frame samples for smoothness.
     */
    external fun nativeGetFps(): Double

    /**
     * Returns the time taken to render the last frame in milliseconds.
     */
    external fun nativeGetFrameTime(): Double

    /**
     * Shuts down the emulation engine.
     * Releases the AHardwareBuffer, unloads the game, deinitializes
     * the core, and closes the dynamic library.
     */
    external fun nativeShutdown()

    /**
     * Sends an Android keycode to the input manager.
     */
    external fun nativeKeyEvent(keycode: Int, pressed: Boolean): Boolean

    /**
     * Sets a Libretro button state directly.
     * @param retroId Libretro button ID (RETRO_DEVICE_ID_JOYPAD_*)
     */
    external fun nativeSetButton(retroId: Int, pressed: Boolean)
}
