package com.elysium.console.data

import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.repository.RomRepository as RomRepositoryContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Repository for discovering and managing ROM files on the device.
 *
 * Scans specified directories for files matching known ROM extensions
 * and returns them as [RomFile] entities with auto-detected platforms.
 */
class RomRepositoryImpl : RomRepositoryContract {

    companion object {
        /**
         * Known ROM file extensions grouped by platform.
         */
        private val ROM_EXTENSIONS = setOf(
            "nes", "smc", "sfc", "z64", "n64", "v64",
            "gba", "gbc", "gb", "nds", "3ds", "cia", "cxi",
            "gcm", "gcz", "iso", "wbfs", "wad",
            "nsp", "xci",
            "bin", "cue", "pbp", "chd", "cso",
            "md", "gen",
            "zip"
        )

        /**
         * Default directories to scan for ROMs.
         */
        private val SCAN_DIRECTORIES = listOf(
            "/storage/emulated/0/Roms",
            "/storage/emulated/0/Download/Roms",
            "/storage/emulated/0/RetroArch/roms",
            "/storage/emulated/0/ElysiumConsole/roms"
        )
    }

    /**
     * Scans the default directories for ROM files.
     *
     * @return List of discovered ROM files with platform auto-detection
     */
    suspend fun scanForRoms(): List<RomFile> = withContext(Dispatchers.IO) {
        scanDirectory("/storage/emulated/0/Roms")
    }

    /**
     * Scans a directory path for ROM files.
     * Implements the domain RomRepository contract.
     */
    override suspend fun scanDirectory(path: String): List<RomFile> = withContext(Dispatchers.IO) {
        val roms = mutableListOf<RomFile>()

        SCAN_DIRECTORIES.forEach { dirPath ->
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, roms)
            }
        }

        roms.sortedBy { it.name.lowercase() }
    }

    /**
     * Scans a specific directory (recursively) for ROM files.
     *
     * @param directory Directory to scan
     * @param customDirectories Additional directories to include
     * @return List of discovered ROM files
     */
    suspend fun scanDirectory(
        directory: File,
        accumulator: MutableList<RomFile> = mutableListOf()
    ): List<RomFile> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext accumulator
        }

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, accumulator)
            } else {
                val ext = file.extension.lowercase()
                if (ext in ROM_EXTENSIONS) {
                    val platform = Platform.fromExtension(ext)
                    if (platform != null) {
                        accumulator.add(
                            RomFile(
                                id = generateId(file.absolutePath),
                                name = file.nameWithoutExtension
                                    .replace("_", " ")
                                    .replace("-", " "),
                                path = file.absolutePath,
                                platform = platform,
                                fileSizeBytes = file.length(),
                                lastPlayed = 0L,
                                playCount = 0
                            )
                        )
                    }
                }
            }
        }

        accumulator
    }

    /**
     * Generates a deterministic ID from the file path using MD5 hash.
     */
    private fun generateId(path: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(path.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}
