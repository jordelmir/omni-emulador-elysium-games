package com.elysium.console.viewmodel

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.console.bridge.ElysiumBridge
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.TelemetryData
import com.elysium.console.domain.usecase.TelemetryAgentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.elysium.console.domain.repository.HardwareMonitor
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.usecase.SelectCoreUseCase
import java.io.File

/**
 * ViewModel orchestrating the emulation session lifecycle.
 * Bridges the UI layer with the native JNI bridge and domain use cases.
 */
class EmulationViewModel(
    private val context: Context,
    private val hardwareMonitor: HardwareMonitor,
    private val selectCoreUseCase: SelectCoreUseCase
) : ViewModel() {

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val vibrator = context.getSystemService(android.os.Vibrator::class.java)!!

    init {
        // Haptic Core: Synchronize native rumble with Android hardware
        ElysiumBridge.rumbleListener = { intensity ->
            if (intensity > 0) {
                val amplitude = (intensity / 256).coerceIn(1, 255)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(16, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(16)
                }
            }
        }
    }

    private var emulationJob: Job? = null
    private var romFileDescriptor: ParcelFileDescriptor? = null

    private val telemetryAgent = TelemetryAgentUseCase(
        hardwareMonitor = hardwareMonitor,
        fpsProvider = { ElysiumBridge.nativeGetFps() },
        frameTimeProvider = { ElysiumBridge.nativeGetFrameTime() },
        cycleMultiplierSetter = { multiplier ->
            ElysiumBridge.nativeSetCycleMultiplier(multiplier)
        }
    )

    fun startEmulation(romPath: String) {
        if (_isRunning.value) return

        _isRunning.value = true
        _isLoading.value = true

        // Start telemetry monitoring
        telemetryAgent.start(viewModelScope)

        // Collect telemetry updates into our StateFlow
        viewModelScope.launch {
            telemetryAgent.telemetry.collect { data ->
                _telemetry.value = data
            }
        }

        // Launch emulation on a background thread
        emulationJob = viewModelScope.launch(Dispatchers.Default) {
            // Pin to prime cores for maximum performance
            ElysiumBridge.nativePinThreads(null)

            // Resolve the core to use based on the ROM file metadata
            val dummyRom = RomFile(
                id = "current", name = "current", path = romPath,
                platform = Platform.ARCADE, fileSizeBytes = 0L, playCount = 0
            )
            val core = selectCoreUseCase(dummyRom)
            val coreName = core?.libraryPath ?: "snes9x_libretro_android.so" // Default fallback
            
            val loadedCore = ElysiumBridge.nativeLoadCore(coreName)
            if (!loadedCore) {
                _isRunning.value = false
                _isLoading.value = false
                return@launch
            }

            loadRomInternal(romPath)
        }
    }

    private suspend fun loadRomInternal(path: String) {
        _isLoading.value = true
        try {
            val uri = Uri.parse(path)
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            
            if (pfd != null) {
                romFileDescriptor = pfd
                val fd = pfd.detachFd()
                val success = ElysiumBridge.nativeLoadRom(path, fd)
                if (success) {
                    _isLoading.value = false
                    _isRunning.value = true
                    
                    // QUICKSTATE: Attempt to auto-load the last session
                    val saveDir = File(context.filesDir, "saves")
                    if (!saveDir.exists()) saveDir.mkdirs()
                    val saveFile = File(saveDir, "auto_${generateSafeName(path)}.state")
                    if (saveFile.exists()) {
                        ElysiumBridge.nativeLoadState(saveFile.absolutePath)
                    }
                    
                    // Enter the emulation loop
                    while (kotlin.coroutines.coroutineContext.isActive && _isRunning.value) {
                        ElysiumBridge.nativeRunFrame()
                        // Yield to prevent thread starvation
                        delay(1L)
                    }
                } else {
                    _isRunning.value = false
                    _isLoading.value = false
                }
            }
        } catch (e: Exception) {
            _isRunning.value = false
            _isLoading.value = false
        }
    }

    private fun generateSafeName(path: String): String {
        return path.replace(Regex("[^a-zA-Z0-9]"), "_")
    }

    fun stopEmulation() {
        _isRunning.value = false
        _isLoading.value = false
        telemetryAgent.stop()
        emulationJob?.cancel()
        emulationJob = null

        viewModelScope.launch(Dispatchers.IO) {
            ElysiumBridge.nativeShutdown()
            romFileDescriptor?.close()
            romFileDescriptor = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopEmulation()
    }
}
