package com.elysium.console.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.console.data.RomRepositoryImpl
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.model.TelemetryData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * ViewModel for the Dashboard screen.
 * Manages the ROM library and provides simulated telemetry data
 * for the real-time monitoring bar.
 */
class DashboardViewModel : ViewModel() {

    private val _roms = MutableStateFlow<List<RomFile>>(emptyList())
    val roms: StateFlow<List<RomFile>> = _roms.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _coreActive = MutableStateFlow(false)
    val coreActive: StateFlow<Boolean> = _coreActive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val romRepository = RomRepositoryImpl()

    init {
        refreshLibrary()
        startTelemetrySimulation()
    }

    /**
     * Scans the internal device storage natively for games and updates the grid.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val scannedRoms = romRepository.scanForRoms()
                _roms.value = scannedRoms
            } catch (e: Exception) {
                // Return empty library on storage exceptions
                _roms.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }


    /**
     * Starts a simulated telemetry data stream for the UI.
     * Generates realistic, smoothly varying values for FPS, CPU, and RAM.
     */
    private fun startTelemetrySimulation() {
        viewModelScope.launch {
            var tick = 0L
            while (isActive) {
                val t = tick * 0.05

                // Simulated FPS: oscillates around 58-60 with occasional dips
                val fpsDip = if (tick % 120 in 40L..55L) -15.0 else 0.0
                val fps = 59.0 + sin(t) * 1.5 + fpsDip +
                    Random.nextDouble(-0.5, 0.5)

                // Frame time derived from FPS
                val frameTime = if (fps > 0) 1000.0 / fps else 16.67

                // CPU usage: correlated with frame time
                val cpuBase = 35f + (frameTime.toFloat() - 16f) * 2f
                val cpuNoise = Random.nextFloat() * 5f - 2.5f
                val cpu = (cpuBase + cpuNoise).coerceIn(15f, 95f)

                // RAM: slowly climbs and stabilizes
                val ramBase = 280f + kotlin.math.sin(t * 0.3).toFloat() * 30f
                val ram = (ramBase + kotlin.random.Random.nextFloat() * 10f).coerceIn(200f, 512f)

                // Thermal state
                val thermal = when {
                    cpu > 85f -> 3
                    cpu > 70f -> 2
                    cpu > 50f -> 1
                    else -> 0
                }

                _telemetry.value = TelemetryData(
                    fps = fps.toDouble().coerceIn(0.0, 120.0),
                    frameTimeMs = frameTime.toDouble(),
                    cpuUsage = cpu,
                    ramUsageMb = ram,
                    thermalState = thermal
                )

                tick++
                delay(100L)
            }
        }
    }
}
