package com.elysium.console.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.console.data.RomRepositoryImpl
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.model.TelemetryData
import com.elysium.console.domain.repository.HardwareMonitor
import com.elysium.console.domain.repository.RomRepository
import com.elysium.console.data.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color

/**
 * ViewModel for the Dashboard screen.
 * Manages the ROM library and provides simulated telemetry data
 * for the real-time monitoring bar.
 */
class DashboardViewModel(
    private val hardwareMonitor: HardwareMonitor,
    private val settingsManager: SettingsManager,
    private val romRepository: RomRepository
) : ViewModel() {

    private val _roms = MutableStateFlow<List<RomFile>>(emptyList())
    val roms: StateFlow<List<RomFile>> = _roms.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _coreActive = MutableStateFlow(false)
    val coreActive: StateFlow<Boolean> = _coreActive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _accentColor = MutableStateFlow(com.elysium.console.ui.theme.NeonGreen)
    val accentColor: StateFlow<androidx.compose.ui.graphics.Color> = _accentColor.asStateFlow()

    init {
        refreshLibrary()
        startRealTelemetryMonitoring()
    }

    /**
     * Updates the dynamic accent color based on the provided ROM's boxart.
     */
    fun updateAccentColor(context: android.content.Context, rom: RomFile) {
        if (rom.coverArtPath.isBlank()) {
            _accentColor.value = com.elysium.console.ui.theme.NeonGreen
            return
        }

        viewModelScope.launch {
            try {
                // Use Coil to get the bitmap directly
                val loader = ImageLoader.Builder(context).build()
                val request = ImageRequest.Builder(context)
                    .data(rom.coverArtPath)
                    .allowHardware(false) // Required for Palette
                    .build()
                
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap

                bitmap?.let {
                    Palette.from(it).generate { palette ->
                        val vibrant = palette?.vibrantSwatch?.rgb
                        val lightVibrant = palette?.lightVibrantSwatch?.rgb
                        if (vibrant != null) {
                            _accentColor.value = Color(vibrant)
                        } else if (lightVibrant != null) {
                            _accentColor.value = Color(lightVibrant)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to default
                _accentColor.value = com.elysium.console.ui.theme.NeonGreen
            }
        }
    }

    /**
     * Scans the internal device storage natively for games and updates the grid.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val folders = settingsManager.getRomFolders().toList()
                val scannedRoms = if (folders.isEmpty()) {
                    emptyList()
                } else {
                    // Try to cast to Impl for scanFolders or use contract scanDirectory
                    (romRepository as? com.elysium.console.data.RomRepositoryImpl)?.scanFolders(folders) 
                        ?: folders.flatMap { romRepository.scanDirectory(it) }
                }
                _roms.value = scannedRoms
            } catch (e: Exception) {
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
