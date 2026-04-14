package com.elysium.console.bridge

import android.util.Log

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
        try {
            System.loadLibrary("elysium-bridge")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("ElysiumBridge", "Failed to load native library: ${e.message}")
        }
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
     * @param romPath Absolute path (or virtual /proc/self/fd path) to the ROM file
     * @param fd      Open file descriptor for SAF files, or -1 for standard paths
     * @return true if the ROM loaded successfully
     */
    external fun nativeLoadRom(romPath: String, fd: Int): Boolean

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
     * Returns the core's declared target FPS (e.g. 60.0 for NTSC, 50.0 for PAL).
     * Used for accurate frame pacing in the emulation loop.
     */
    external fun nativeGetTargetFps(): Double

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

    /**
     * Sets a custom GPU driver (Vulkan ICD) path.
     * This environment hook directs the Vulkan loader to use the specified .so file.
     * 
     * @param driverPath Absolute path to the custom .so driver, or null for system default
     */
    external fun nativeSetGpuDriver(driverPath: String?)

    /**
     * Saves the current game state to a file.
     * @param path Absolute path to save the state file
     * @return true if save succeeded
     */
    external fun nativeSaveState(path: String): Boolean

    /**
     * Loads a previously saved game state.
     * @param path Absolute path to the state file
     * @return true if load succeeded
     */
    external fun nativeLoadState(path: String): Boolean

    /**
     * Sets a visual effect for the renderer.
     * @param effectId 0 for None, 1 for CRT Scanlines
     */
    external fun nativeSetVisualEffect(effectId: Int)

    /**
     * Sets an upscaling mode.
     * @param mode 0 for Original, 1 for Scale2x (Omni-Scale Ultra)
     */
    external fun nativeSetUpscaler(mode: Int)

    /**
     * Renders the current frame to the GL surface using the native shader engine.
     * @param width  Current viewport width
     * @param height Current viewport height
     */
    external fun nativeRenderFrame(width: Int, height: Int)

    var rumbleListener: ((intensity: Int) -> Unit)? = null

    /**
     * Called by JNI when the core requests rumble.
     */
    @JvmStatic
    fun onRumble(intensity: Int) {
        rumbleListener?.invoke(intensity)
    }
}
