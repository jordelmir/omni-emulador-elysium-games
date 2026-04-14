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

/**
 * ViewModel orchestrating the emulation session lifecycle.
 * Bridges the UI layer with the native JNI bridge and domain use cases.
 *
 * Handles:
 * - Core and ROM loading
 * - Emulation loop execution on a dedicated thread
 * - Telemetry collection via TelemetryAgentUseCase
 * - Thread pinning to prime CPU cores
 * - Graceful shutdown and resource cleanup
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

    /**
     * Starts an emulation session for the given ROM.
     * This initializes the bridge, loads the first available core,
     * pins the emulation thread to prime cores, and enters the run loop.
     *
     * @param romPath Absolute path to the ROM file
     */
    fun startEmulation(romPath: String) {
        if (_isRunning.value) return

        _isRunning.value = true

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
            
            // Construct full path to the bundled core (assuming they are in the native library path)
            // Wait, we need the app to pass the path to the extracted core. By default, JNI libraries 
            // from src/main/jniLibs are unpacked to the app's nativeLibraryDir. We can just pass the name
            // if we use a helper, but nativeLoadCore expects an absolute path.
            // Let's assume MainActivity copies / extracts them, or we pass the nativeLibraryDir.
            // Actually, we can get the nativeLibraryDir from the Application context!
            // But for now, we'll assume they're in the default lib dir. Wait, nativeLoadCore uses dlopen.
            // We can pass just the name to dlopen, and the dynamic linker will find it in the app's lib directory!
            val loadedCore = ElysiumBridge.nativeLoadCore(coreName)
            if (!loadedCore) {
                _isRunning.value = false
                return@launch
            }

            loadRom(romPath)
        }
    }

    fun loadRom(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Find the ROM in our library to check for multi-disc status
                // (In a real app, we'd pass the RomFile object or use a repository lookup)
                
                var actualPath = path
                // Multi-disc support: Generate .m3u for grouped discs
                if (path.contains("content://") && path.endsWith(".m3u")) {
                    // Logic to handle virtual m3u if needed
                }

                val uri = Uri.parse(path)
                val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                
                if (pfd != null) {
                    val fd = pfd.detachFd()
                    val success = ElysiumBridge.nativeLoadRom(path, fd)
                    if (success) {
                        _isRunning.value = true
                        
                        // QUICKSTATE: Attempt to auto-load the last session
                        val saveFile = File(context.filesDir, "saves/auto_${generateSafeName(path)}.state")
                        if (saveFile.exists()) {
                            ElysiumBridge.nativeLoadState(saveFile.absolutePath)
                        }
                        
                        // Enter the emulation loop
                        while (isActive && _isRunning.value) {
                            ElysiumBridge.nativeRunFrame()
                            delay(1L)
                        }
                    }

                // Yield to prevent thread starvation
                // The actual frame pacing is handled by the core's vsync
                delay(1L)
            }
        }
    }

    /**
     * Stops the emulation session and cleans up all resources.
     */
    fun stopEmulation() {
        _isRunning.value = false
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
