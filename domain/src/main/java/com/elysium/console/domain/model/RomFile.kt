package com.elysium.console.domain.model

/**
 * Represents a ROM file discovered on the device.
 *
 * @property id         Unique identifier (hash of the file path)
 * @property name       Display name (filename without extension)
 * @property path       Absolute file path on the device
 * @property platform   Detected platform based on file extension
 * @property coverArtPath Optional path to cover art image
 * @property lastPlayed  Epoch millis of last play session, or 0 if never played
 * @property playCount   Total number of times this ROM has been launched
 * @property fileSizeBytes File size for display purposes
 */
data class RomFile(
    val id: String,
    val name: String,
    val path: String,
    val platform: Platform,
    val coverArtPath: String = "",
    val lastPlayed: Long = 0L,
    val playCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isMultiDisc: Boolean = false,
    val discPaths: List<String> = emptyList(),
    val year: String = "",
    val genre: String = "",
    val description: String = ""
) {
    /**
     * Returns a human-readable file size string (e.g., "256 MB").
     */
    val formattedSize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.0f KB".format(kb)
                else -> "$fileSizeBytes B"
            }
        }

    /**
     * Returns true if this ROM has cover art available.
     */
    val hasCoverArt: Boolean
        get() = coverArtPath.isNotBlank()
}
