package com.elysium.console.data
import com.elysium.console.data.util.MetadataScraper

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.repository.RomRepository as RomRepositoryContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Universal ROM Discovery Engine for Android 14 (Scoped Storage).
 * Uses DocumentFile for SAF tree traversal and legacy File API for internal data.
 */
class RomRepositoryImpl(private val context: Context) : RomRepositoryContract {

    companion object {
        private const val TAG = "RomRepository"
        private const val MIN_GAME_SIZE_BYTES = 8 * 1024L 

        private val ROM_EXTENSIONS = setOf(
            "nes", "smc", "sfc", "z64", "n64", "v64",
            "gba", "gbc", "gb", "nds", "3ds", "cia", "cxi",
            "gcm", "gcz", "iso", "wbfs", "wad",
            "nsp", "xci",
            "bin", "cue", "pbp", "chd", "cso",
            "md", "gen", "zip"
        )
    }

    /**
     * Scans multiple storage nodes (Directories or SAF URIs).
     */
    suspend fun scanFolders(paths: List<String>): List<RomFile> = withContext(Dispatchers.IO) {
        val allRoms = mutableListOf<RomFile>()
        paths.forEach { path ->
            if (path.startsWith("content://")) {
                scanDocumentTree(Uri.parse(path), allRoms)
            } else {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryRecursive(dir, allRoms)
                }
            }
        }
        
        // Final consolidation: Group Multi-Disc sets and unique entries
        groupMultiDiscRoms(allRoms.distinctBy { it.path }).sortedBy { it.name.lowercase() }
    }

    /**
     * Identifies games with multiple discs and collapses them into a single entry.
     */
    private fun groupMultiDiscRoms(roms: List<RomFile>): List<RomFile> {
        val grouped = mutableMapOf<String, MutableList<RomFile>>()
        val result = mutableListOf<RomFile>()

        roms.forEach { rom ->
            // Match patterns like "Game (Disc 1)", "Game (Disk A)", "Game (CD 1)"
            val baseName = rom.name.replace(Regex("\\((Disc|Disk|CD|Side)\\s*[\\w\\d]+\\)", RegexOption.IGNORE_CASE), "").trim()
            if (baseName != rom.name) {
                grouped.getOrPut(baseName) { mutableListOf() }.add(rom)
            } else {
                result.add(rom)
            }
        }

        grouped.forEach { (baseName, discs) ->
            val first = discs.sortedBy { it.name.lowercase() }.first()
            result.add(
                first.copy(
                    name = baseName,
                    isMultiDisc = true,
                    discPaths = discs.sortedBy { it.name.lowercase() }.map { it.path }
                )
            )
        }

        return result
    }

    /**
     * Contract implementation: Scans a single directory.
     */
    override suspend fun scanDirectory(path: String): List<RomFile> = withContext(Dispatchers.IO) {
        val roms = mutableListOf<RomFile>()
        if (path.startsWith("content://")) {
            scanDocumentTree(Uri.parse(path), roms)
        } else {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                scanDirectoryRecursive(dir, roms)
            }
        }
        groupMultiDiscRoms(roms).sortedBy { it.name.lowercase() }
    }

    /**
     * MODERN SCAN: Traverses SAF Document Trees for Scoped Storage.
     */
    private fun scanDocumentTree(uri: Uri, accumulator: MutableList<RomFile>) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return
        scanDocumentFileRecursive(root, accumulator)
    }

    private fun scanDocumentFileRecursive(directory: DocumentFile, accumulator: MutableList<RomFile>) {
        if (!directory.isDirectory) return
        
        for (doc in directory.listFiles()) {
            if (doc.isDirectory) {
                if (!doc.name.orEmpty().startsWith(".")) {
                    scanDocumentFileRecursive(doc, accumulator)
                }
            } else {
                val name = doc.name.orEmpty()
                val extension = name.substringAfterLast('.', "").lowercase()
                
                if (extension in ROM_EXTENSIONS) {
                    // Filter ghost files
                    if (doc.length() < MIN_GAME_SIZE_BYTES) continue

                    val platform = Platform.fromExtension(extension)
                    if (platform != null) {
                        val rom = RomFile(
                            id = generateId(doc.uri.toString()),
                            name = name.substringBeforeLast('.')
                                .replace("_", " ")
                                .replace("-", " ")
                                .trim(),
                            path = doc.uri.toString(),
                            platform = platform,
                            fileSizeBytes = doc.length(),
                            lastPlayed = 0L,
                            playCount = 0
                        )
                        accumulator.add(MetadataScraper.enrich(rom))
                    }
                }
            }
        }
    }

    /**
     * LEGACY SCAN: Traverses standard filesystem directories.
     */
    private fun scanDirectoryRecursive(directory: File, accumulator: MutableList<RomFile>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryRecursive(file, accumulator)
                }
            } else {
                val extension = file.extension.lowercase()
                if (extension in ROM_EXTENSIONS) {
                    if (file.length() < MIN_GAME_SIZE_BYTES) continue

                    val platform = Platform.fromExtension(extension)
                    if (platform != null) {
                    val rom = RomFile(
                        id = generateId(file.absolutePath),
                        name = file.nameWithoutExtension
                            .replace("_", " ")
                            .replace("-", " ")
                            .trim(),
                        path = file.absolutePath,
                        platform = platform,
                        fileSizeBytes = file.length(),
                        lastPlayed = 0L,
                        playCount = 0
                    )
                    accumulator.add(MetadataScraper.enrich(rom))
                    }
                }
            }
        }
    }

    private fun generateId(path: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(path.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}
