package com.elysium.console.data

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.elysium.console.domain.repository.HardwareMonitor
import java.io.RandomAccessFile

/**
 * Real-time system telemetry provider.
 * Reads hardware state directly from the Linux kernel and Android system services.
 */
class SystemTelemetryProvider(private val context: Context) : HardwareMonitor {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var lastTotalTick = 0L
    private var lastIdleTick = 0L

    /**
     * Returns the current overall CPU usage (0.0 to 100.0).
     * Calculates the delta since the last call to ensure accurate sampling.
     */
    override fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val tokens = load.split("\\s+".toRegex())
            // /proc/stat format: cpu  user nice system idle iowait irq softirq steal guest guest_nice
            // tokens[0] is "cpu"
            val idle = tokens[4].toLong()
            val total = tokens.subList(1, 11).map { it.toLong() }.sum()

            val totalDelta = total - lastTotalTick
            val idleDelta = idle - lastIdleTick

            lastTotalTick = total
            lastIdleTick = idle

            if (totalDelta == 0L) return 0f
            
            val cpuLoad = (100f * (totalDelta - idleDelta) / totalDelta)
            cpuLoad.coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.e("Telemetry", "Failed to read /proc/stat", e)
            0f
        }
    }

    /**
     * Returns the current used RAM in MB.
     */
    override fun getUsedRamMb(): Float {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem / (1024 * 1024).toFloat()
        val avail = memInfo.availMem / (1024 * 1024).toFloat()
        return (total - avail).coerceAtLeast(0f)
    }

    /**
     * Derives the thermal state from CPU usage and device specifics.
     */
    override fun getThermalState(cpuUsage: Float): Int {
        return when {
            cpuUsage > 92f -> 3 // Critical
            cpuUsage > 80f -> 2 // Hot
            cpuUsage > 60f -> 1 // Warm
            else -> 0           // Nominal
        }
    }
}
