package com.elysium.console.domain.usecase

import com.elysium.console.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reactive telemetry agent that monitors emulation performance in real-time.
 *
 * Implements a feedback loop:
 * 1. Polls FPS and frame-time from the emulation engine
 * 2. Detects performance degradation with hysteresis
 * 3. Emits CycleAdjustment signals to dynamically tune the JNI bridge
 *
 * Hysteresis prevents oscillation: the multiplier only changes when
 * performance stays below/above thresholds for consecutive samples.
 */
class TelemetryAgentUseCase(
    private val fpsProvider: () -> Double,
    private val frameTimeProvider: () -> Double,
    private val cycleMultiplierSetter: (Float) -> Unit
) {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _cycleAdjustment = MutableStateFlow(1.0f)
    val cycleAdjustment: StateFlow<Float> = _cycleAdjustment.asStateFlow()

    private var monitorJob: Job? = null
    private var targetFps: Double = 60.0

    // Hysteresis state
    private var consecutiveLowFrames: Int = 0
    private var consecutiveHighFrames: Int = 0
    private var currentMultiplier: Float = 1.0f

    companion object {
        private const val POLL_INTERVAL_MS = 100L
        private const val LOW_FPS_THRESHOLD_RATIO = 0.85
        private const val HIGH_FPS_THRESHOLD_RATIO = 0.95
        private const val HYSTERESIS_SAMPLES = 5
        private const val MULTIPLIER_STEP_UP = 0.1f
        private const val MULTIPLIER_STEP_DOWN = 0.05f
        private const val MIN_MULTIPLIER = 0.5f
        private const val MAX_MULTIPLIER = 2.0f
    }

    /**
     * Starts the telemetry monitoring loop.
     *
     * @param scope Coroutine scope to launch the monitor in
     * @param targetFps Target FPS for the current core (usually 60 or 30)
     */
    fun start(scope: CoroutineScope, targetFps: Double = 60.0) {
        stop()
        this.targetFps = targetFps
        consecutiveLowFrames = 0
        consecutiveHighFrames = 0
        currentMultiplier = 1.0f

        monitorJob = scope.launch(Dispatchers.Default) {
            var cpuSimulated = 0f
            var ramSimulated = 0f

            while (isActive) {
                val fps = fpsProvider()
                val frameTime = frameTimeProvider()

                // Simulated CPU/RAM telemetry (realistic reactive values)
                // In production, these would come from /proc/stat and ActivityManager
                cpuSimulated = calculateSimulatedCpu(fps, frameTime, cpuSimulated)
                ramSimulated = calculateSimulatedRam(ramSimulated)

                val data = TelemetryData(
                    fps = fps,
                    frameTimeMs = frameTime,
                    cpuUsage = cpuSimulated,
                    ramUsageMb = ramSimulated,
                    thermalState = deriveThermalState(cpuSimulated)
                )

                _telemetry.value = data

                // Feedback loop: adjust cycle multiplier based on performance
                evaluatePerformance(fps)

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops the telemetry monitoring loop.
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Evaluates current FPS against thresholds with hysteresis.
     * Only adjusts the multiplier after sustained deviation.
     */
    private fun evaluatePerformance(currentFps: Double) {
        val lowThreshold = targetFps * LOW_FPS_THRESHOLD_RATIO
        val highThreshold = targetFps * HIGH_FPS_THRESHOLD_RATIO

        when {
            currentFps < lowThreshold -> {
                consecutiveLowFrames++
                consecutiveHighFrames = 0

                if (consecutiveLowFrames >= HYSTERESIS_SAMPLES) {
                    // Performance sustained low — reduce multiplier to lighten load
                    currentMultiplier = (currentMultiplier - MULTIPLIER_STEP_DOWN)
                        .coerceAtLeast(MIN_MULTIPLIER)
                    applyMultiplier()
                    consecutiveLowFrames = 0
                }
            }
            currentFps >= highThreshold -> {
                consecutiveHighFrames++
                consecutiveLowFrames = 0

                if (consecutiveHighFrames >= HYSTERESIS_SAMPLES && currentMultiplier < 1.0f) {
                    // Performance recovered — restore multiplier toward 1.0
                    currentMultiplier = (currentMultiplier + MULTIPLIER_STEP_UP)
                        .coerceAtMost(MAX_MULTIPLIER)
                    applyMultiplier()
                    consecutiveHighFrames = 0
                }
            }
            else -> {
                // In acceptable range — reset hysteresis counters
                consecutiveLowFrames = 0
                consecutiveHighFrames = 0
            }
        }
    }

    private fun applyMultiplier() {
        _cycleAdjustment.value = currentMultiplier
        cycleMultiplierSetter(currentMultiplier)
    }

    private fun calculateSimulatedCpu(fps: Double, frameTime: Double, previous: Float): Float {
        // CPU usage correlates inversely with headroom (frameTime vs budget)
        val budget = if (targetFps > 0) 1000.0 / targetFps else 16.67
        val load = if (budget > 0) (frameTime / budget * 100.0).toFloat() else 50f
        // Smooth with EMA
        return (previous * 0.7f + load.coerceIn(5f, 98f) * 0.3f)
    }

    private fun calculateSimulatedRam(previous: Float): Float {
        // Simulate gradual RAM usage with slight variance
        val base = 256f + (System.nanoTime() % 100) * 0.5f
        return (previous * 0.95f + base * 0.05f)
    }

    private fun deriveThermalState(cpuUsage: Float): Int = when {
        cpuUsage > 90f -> 3 // Critical
        cpuUsage > 75f -> 2 // Hot
        cpuUsage > 55f -> 1 // Warm
        else -> 0           // Nominal
    }
}
