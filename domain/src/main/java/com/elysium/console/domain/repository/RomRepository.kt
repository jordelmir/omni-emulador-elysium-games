package com.elysium.console.domain.repository

import com.elysium.console.domain.model.RomFile

/**
 * Contract for scanning device storage for ROM files.
 * Implemented in :data module.
 */
interface RomRepository {
    /**
     * Recursively scans the given directory path for ROM files
     * matching known extensions.
     *
     * @param path Absolute path to the directory to scan
     * @return List of discovered ROM files with auto-detected platforms
     */
    suspend fun scanDirectory(path: String): List<RomFile>
}
