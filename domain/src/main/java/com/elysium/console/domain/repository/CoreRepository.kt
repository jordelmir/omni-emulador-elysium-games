package com.elysium.console.domain.repository

import com.elysium.console.domain.model.EmulatorCore
import com.elysium.console.domain.model.RomFile

/**
 * Contract for managing installed Libretro emulator cores.
 * Implemented in :data module with hardcoded core registry.
 */
interface CoreRepository {
    /**
     * Returns all registered emulator cores available on the device.
     */
    suspend fun getInstalledCores(): List<EmulatorCore>

    /**
     * Finds the best matching core for a given ROM file.
     * Priority: exact extension match → first compatible core → null.
     *
     * @param rom The ROM file to find a core for
     * @return The matching EmulatorCore, or null if none found
     */
    suspend fun getCoreForRom(rom: RomFile): EmulatorCore?
}
