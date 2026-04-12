package com.elysium.console.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.console.data.RomRepositoryImpl
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.model.TelemetryData
import com.elysium.console.domain.repository.HardwareMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Dashboard screen.
 * Manages the ROM library and provides simulated telemetry data
 * for the real-time monitoring bar.
 */
class DashboardViewModel(
    private val hardwareMonitor: HardwareMonitor
) : ViewModel() {

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
        startRealTelemetryMonitoring()
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
     * Periodically queries the hardware monitor for real system stats.
     */
    private fun startRealTelemetryMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                val cpu = hardwareMonitor.getCpuUsage()
                val ram = hardwareMonitor.getUsedRamMb()
                val thermal = hardwareMonitor.getThermalState(cpu)

                _telemetry.value = TelemetryData(
                    fps = 0.0, // Dashboard doesn't have FPS
                    frameTimeMs = 0.0,
                    cpuUsage = cpu,
                    ramUsageMb = ram,
                    thermalState = thermal
                )

                delay(500L) // Slower poll for UI in background
            }
        }
    }
}
