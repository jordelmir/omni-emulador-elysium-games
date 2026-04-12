package com.elysium.console.domain.model

/**
 * Represents a Libretro-compatible emulator core library.
 *
 * @property id                 Unique identifier
 * @property name               Human-readable core name (e.g., "mupen64plus")
 * @property platform           Primary platform this core emulates
 * @property libraryPath        Absolute path to the .so file
 * @property version            Core version string
 * @property supportedExtensions Comma-separated list of ROM extensions
 */
enum class ExecutionType {
    INTERNAL_LIBRETRO,
    EXTERNAL_INTENT
}

/**
 * Represents a Libretro-compatible emulator core library or an external standalone app.
 *
 * @property id                 Unique identifier
 * @property name               Human-readable core name (e.g., "mupen64plus" or "Uzuy")
 * @property platform           Primary platform this core emulates
 * @property executionType      Whether this runs internally via JNI or via Android Intent
 * @property libraryPath        Path to .so file OR the Intent URI signature
 * @property version            Core version string
 * @property supportedExtensions Comma-separated list of ROM extensions
 * @property androidPackageName Package name for intent launching (e.g., "org.uzuy.edge")
 * @property androidActivityName Optional explicit activity class name to launch
 */
data class EmulatorCore(
    val id: String,
    val name: String,
    val platform: Platform,
    val executionType: ExecutionType = ExecutionType.INTERNAL_LIBRETRO,
    val libraryPath: String,
    val version: String,
    val supportedExtensions: List<String>,
    val androidPackageName: String? = null,
    val androidActivityName: String? = null
) {
    /**
     * Returns true if this core can handle the given file extension.
     */
    fun supportsExtension(ext: String): Boolean =
        supportedExtensions.any { it.equals(ext, ignoreCase = true) }

    /**
     * Returns a display-friendly name with version.
     */
    val displayLabel: String
        get() = "$name v$version"
}
