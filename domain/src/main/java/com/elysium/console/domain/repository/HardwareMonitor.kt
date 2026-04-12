package com.elysium.console.domain.repository

/**
 * Interface for accessing real-time hardware telemetry.
 */
interface HardwareMonitor {
    /**
     * Returns the current overall CPU usage percentage (0.0 - 100.0).
     */
    fun getCpuUsage(): Float

    /**
     * Returns the current used RAM in MB.
     */
    fun getUsedRamMb(): Float

    /**
     * Returns the current thermal state (0=Nominal, 1=Warm, 2=Hot, 3=Critical).
     */
    fun getThermalState(cpuUsage: Float): Int
}
