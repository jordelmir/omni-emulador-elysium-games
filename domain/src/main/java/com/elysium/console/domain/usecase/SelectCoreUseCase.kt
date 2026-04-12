package com.elysium.console.domain.usecase

import com.elysium.console.domain.model.EmulatorCore
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.repository.CoreRepository

/**
 * Selects the optimal emulator core for a given ROM file.
 *
 * Resolution strategy:
 * 1. Query CoreRepository for all installed cores
 * 2. Find cores whose supportedExtensions contain the ROM's extension
 * 3. Return the first match (exact extension priority)
 * 4. Return null if no compatible core is found
 */
class SelectCoreUseCase(
    private val coreRepository: CoreRepository
) {
    /**
     * Finds the best emulator core for the given ROM.
     *
     * @param rom The ROM file to match
     * @return The matching EmulatorCore, or null if none is compatible
     */
    suspend operator fun invoke(rom: RomFile): EmulatorCore? {
        val extension = rom.path.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null

        val cores = coreRepository.getInstalledCores()

        // Priority 1: Exact extension match
        val exactMatch = cores.firstOrNull { core ->
            core.supportsExtension(extension)
        }
        if (exactMatch != null) return exactMatch

        // Priority 2: Fallback via CoreRepository's own matching logic
        return coreRepository.getCoreForRom(rom)
    }
}
