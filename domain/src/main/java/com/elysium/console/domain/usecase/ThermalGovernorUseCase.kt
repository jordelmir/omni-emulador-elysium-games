package com.elysium.console.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the CPU frequency scaling governor via privileged shell commands.
 *
 * Uses the ShellCommandExecutor (implemented via Shizuku in :data) to write
 * directly to /sys/devices/system/cpu/ to force performance mode during
 * demanding emulation sessions and revert to schedutil on exit.
 *
 * Flow:
 * 1. On core start (exigent platform) → activatePerformanceMode()
 * 2. On core stop / app background    → deactivatePerformanceMode()
 */
class ThermalGovernorUseCase(
    private val shellExecutor: ShellCommandExecutor
) {
    companion object {
        private const val GOVERNOR_PATH = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor"
        private const val PERFORMANCE_GOVERNOR = "performance"
        private const val DEFAULT_GOVERNOR = "schedutil"
        private const val MAX_CORES = 8
    }

    /**
     * Detects the number of CPU cores available on the device.
     */
    private suspend fun detectCoreCount(): Int {
        val result = shellExecutor.execute("nproc")
        return result.getOrNull()?.trim()?.toIntOrNull() ?: MAX_CORES
    }

    /**
     * Sets the CPU frequency governor for all cores.
     *
     * @param governor Governor name (e.g., "performance", "schedutil")
     * @return Result with the number of cores successfully configured
     */
    private suspend fun setGovernor(governor: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val available = shellExecutor.isAvailable()
                if (!available) {
                    return@withContext Result.failure(
                        IllegalStateException("Shell executor not available (Shizuku not running?)")
                    )
                }

                val coreCount = detectCoreCount()
                var successCount = 0

                for (core in 0 until coreCount) {
                    val path = GOVERNOR_PATH.format(core)
                    val command = "echo $governor > $path"
                    val result = shellExecutor.execute(command)

                    if (result.isSuccess) {
                        successCount++
                    }
                    // Some cores may be offline — that's expected, continue
                }

                Result.success(successCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Activates the "performance" CPU governor on all available cores.
     * This forces maximum clock speed, eliminating frequency ramping latency.
     *
     * Should be called when starting emulation of demanding platforms
     * (PS2, GameCube, 3DS, Switch).
     *
     * @return Result with the count of cores set to performance mode
     */
    suspend fun activatePerformanceMode(): Result<Int> =
        setGovernor(PERFORMANCE_GOVERNOR)

    /**
     * Deactivates performance mode, reverting all cores to "schedutil".
     * The schedutil governor uses CPU utilization data from the scheduler
     * to make frequency scaling decisions — best for battery life.
     *
     * Should be called when:
     * - Emulation session ends
     * - App goes to background
     * - Thermal state reaches critical
     *
     * @return Result with the count of cores reverted
     */
    suspend fun deactivatePerformanceMode(): Result<Int> =
        setGovernor(DEFAULT_GOVERNOR)

    /**
     * Reads the current governor for a specific CPU core.
     *
     * @param coreIndex CPU core index (0-based)
     * @return The current governor name, or "unknown" on failure
     */
    suspend fun getCurrentGovernor(coreIndex: Int = 0): String {
        val path = GOVERNOR_PATH.format(coreIndex)
        val result = shellExecutor.execute("cat $path")
        return result.getOrNull()?.trim() ?: "unknown"
    }

    /**
     * Lists all available governors for a specific CPU core.
     *
     * @param coreIndex CPU core index (0-based)
     * @return List of governor names, or empty list on failure
     */
    suspend fun getAvailableGovernors(coreIndex: Int = 0): List<String> {
        val path = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_available_governors"
        val result = shellExecutor.execute("cat $path")
        return result.getOrNull()
            ?.trim()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}
