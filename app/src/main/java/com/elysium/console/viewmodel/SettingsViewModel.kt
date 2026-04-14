package com.elysium.console.viewmodel

import androidx.lifecycle.ViewModel
import com.elysium.console.data.SettingsManager
import com.elysium.console.data.util.NucleusFileProvisioner
import com.elysium.console.bridge.ElysiumBridge
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the persistent configuration and hardware dependencies 
 * (BIOS, Drivers, Keys) for Omni Elysium.
 */
class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val provisioner: NucleusFileProvisioner
) : ViewModel() {

    private val _romFolders = MutableStateFlow(settingsManager.getRomFolders())
    val romFolders: StateFlow<Set<String>> = _romFolders.asStateFlow()

    private val _biosPath = MutableStateFlow(settingsManager.getBiosPath())
    val biosPath: StateFlow<String?> = _biosPath.asStateFlow()

    private val _keysPath = MutableStateFlow(settingsManager.getSwitchKeysPath())
    val keysPath: StateFlow<String?> = _keysPath.asStateFlow()

    private val _driverPath = MutableStateFlow(settingsManager.getDriverPath())
    val driverPath: StateFlow<String?> = _driverPath.asStateFlow()

    private val _visualEffectId = MutableStateFlow(settingsManager.getVisualEffectId())
    val visualEffectId: StateFlow<Int> = _visualEffectId.asStateFlow()

    private val _upscaleMode = MutableStateFlow(settingsManager.getUpscaleMode())
    val upscaleMode: StateFlow<Int> = _upscaleMode.asStateFlow()

    init {
        // Apply existing settings on startup
        _driverPath.value?.let { ElysiumBridge.nativeSetGpuDriver(it) }
        ElysiumBridge.nativeSetVisualEffect(_visualEffectId.value)
        ElysiumBridge.nativeSetUpscaler(_upscaleMode.value)
    }

    fun setUpscaleMode(mode: Int) {
        settingsManager.setUpscaleMode(mode)
        _upscaleMode.value = mode
        ElysiumBridge.nativeSetUpscaler(mode)
    }

    fun setVisualEffectId(id: Int) {
        settingsManager.setVisualEffectId(id)
        _visualEffectId.value = id
        ElysiumBridge.nativeSetVisualEffect(id)
    }

    fun addRomFolder(path: String) {
        settingsManager.addRomFolder(path)
        _romFolders.value = settingsManager.getRomFolders()
    }

    fun removeRomFolder(path: String) {
        settingsManager.removeRomFolder(path)
        _romFolders.value = settingsManager.getRomFolders()
    }

    fun setBiosPath(uriStr: String) {
        val uri = Uri.parse(uriStr)
        val shadowPath = provisioner.provisionSystemFile(uri, null) // Use original filename
        shadowPath?.let {
            settingsManager.setBiosPath(it)
            _biosPath.value = it
        }
    }

    fun setKeysPath(uriStr: String) {
        val uri = Uri.parse(uriStr)
        val shadowPath = provisioner.provisionSystemFile(uri, null) // Use original filename
        shadowPath?.let {
            settingsManager.setSwitchKeysPath(it)
            _keysPath.value = it
        }
    }

    fun setDriverPath(uriStr: String) {
        val uri = Uri.parse(uriStr)
        val shadowPath = provisioner.provisionSystemFile(uri, "custom_gpu.so")
        shadowPath?.let {
            settingsManager.setDriverPath(it)
            _driverPath.value = it
            // Activate the driver via native environment hook
            ElysiumBridge.nativeSetGpuDriver(it)
        }
    }
}
