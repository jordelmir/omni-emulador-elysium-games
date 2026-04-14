package com.elysium.console.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists app-wide settings including ROM storage folders, BIOS paths,
 * and custom GPU driver selections.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("elysium_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROM_FOLDERS = "rom_folders"
        private const val KEY_BIOS_PATH = "bios_path"
        private const val KEY_SWITCH_KEYS = "switch_keys"
        private const val KEY_DRIVER_PATH = "gpu_driver_path"
    }

    /**
     * Set of directories to scan for games.
     */
    fun getRomFolders(): Set<String> {
        return prefs.getStringSet(KEY_ROM_FOLDERS, emptySet()) ?: emptySet()
    }

    fun addRomFolder(path: String) {
        val folders = getRomFolders().toMutableSet()
        folders.add(path)
        prefs.edit().putStringSet(KEY_ROM_FOLDERS, folders).apply()
    }

    fun removeRomFolder(path: String) {
        val folders = getRomFolders().toMutableSet()
        folders.remove(path)
        prefs.edit().putStringSet(KEY_ROM_FOLDERS, folders).apply()
    }

    /**
     * Path to the PS2/Retro BIOS directory.
     */
    fun getBiosPath(): String? = prefs.getString(KEY_BIOS_PATH, null)

    fun setBiosPath(path: String) {
        prefs.edit().putString(KEY_BIOS_PATH, path).apply()
    }

    /**
     * Path to Switch prod.keys.
     */
    fun getSwitchKeysPath(): String? = prefs.getString(KEY_SWITCH_KEYS, null)

    fun setSwitchKeysPath(path: String) {
        prefs.edit().putString(KEY_SWITCH_KEYS, path).apply()
    }

    /**
     * Path to custom GPU driver (.so).
     */
    fun getDriverPath(): String? = prefs.getString(KEY_DRIVER_PATH, null)

    fun setDriverPath(path: String) {
        prefs.edit().putString(KEY_DRIVER_PATH, path).apply()
    }

    fun getVisualEffectId(): Int = prefs.getInt(KEY_VISUAL_EFFECT_ID, 0)

    fun setVisualEffectId(id: Int) {
        prefs.edit().putInt(KEY_VISUAL_EFFECT_ID, id).apply()
    }
}
