package com.elysium.console.domain.model

/**
 * Real-time telemetry snapshot from the emulation engine.
 *
 * @property fps          Current frames per second (rolling average)
 * @property frameTimeMs  Time to render last frame in milliseconds
 * @property cpuUsage     CPU utilization percentage (0.0 — 100.0)
 * @property ramUsageMb   RAM usage in megabytes
 * @property thermalState Device thermal state (0=nominal, 1=fair, 2=serious, 3=critical)
 */
data class TelemetryData(
    val fps: Double = 0.0,
    val frameTimeMs: Double = 0.0,
    val cpuUsage: Float = 0f,
    val ramUsageMb: Float = 0f,
    val thermalState: Int = 0
) {
    /**
     * Returns the FPS as a percentage of the target (typically 60).
     */
    fun fpsPercentage(targetFps: Double = 60.0): Float =
        if (targetFps > 0) (fps / targetFps * 100.0).toFloat().coerceIn(0f, 200f) else 0f

    /**
     * Returns true if the emulation is running below target performance.
     */
    fun isBelowTarget(targetFps: Double = 60.0, threshold: Double = 0.90): Boolean =
        fps < (targetFps * threshold)

    /**
     * Returns a human-readable thermal state label.
     */
    val thermalLabel: String
        get() = when (thermalState) {
            0 -> "Nominal"
            1 -> "Warm"
            2 -> "Hot"
            3 -> "Critical"
            else -> "Unknown"
        }
}
