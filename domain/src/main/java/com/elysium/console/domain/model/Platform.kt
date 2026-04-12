package com.elysium.console.domain.model

/**
 * Supported emulation platforms.
 * Each entry maps to a specific emulator core library.
 */
enum class Platform(
    val displayName: String,
    val abbreviation: String,
    val manufacturer: String,
    val generation: Int
) {
    NES("Nintendo Entertainment System", "NES", "Nintendo", 3),
    SNES("Super Nintendo", "SNES", "Nintendo", 4),
    N64("Nintendo 64", "N64", "Nintendo", 5),
    GBA("Game Boy Advance", "GBA", "Nintendo", 6),
    NDS("Nintendo DS", "NDS", "Nintendo", 7),
    N3DS("Nintendo 3DS", "3DS", "Nintendo", 8),
    GAMECUBE("Nintendo GameCube", "GC", "Nintendo", 6),
    WII("Nintendo Wii", "Wii", "Nintendo", 7),
    SWITCH("Nintendo Switch", "NSW", "Nintendo", 9),
    PS1("PlayStation", "PS1", "Sony", 5),
    PS2("PlayStation 2", "PS2", "Sony", 6),
    PSP("PlayStation Portable", "PSP", "Sony", 7),
    GENESIS("Sega Genesis", "GEN", "Sega", 4),
    SATURN("Sega Saturn", "SAT", "Sega", 5),
    DREAMCAST("Sega Dreamcast", "DC", "Sega", 6),
    ARCADE("Arcade", "ARC", "Various", 0);

    companion object {
        /**
         * Resolves a Platform from a ROM file extension.
         */
        fun fromExtension(ext: String): Platform? = when (ext.lowercase()) {
            "nes" -> NES
            "smc", "sfc" -> SNES
            "z64", "n64", "v64" -> N64
            "gba" -> GBA
            "nds" -> NDS
            "3ds", "cia", "cxi" -> N3DS
            "gcm", "iso", "gcz" -> GAMECUBE
            "wbfs", "wad" -> WII
            "nsp", "xci" -> SWITCH
            "bin", "cue", "pbp" -> PS1
            "chd" -> PS2
            "cso" -> PSP
            "md", "gen" -> GENESIS
            "zip" -> ARCADE
            else -> null
        }
    }
}
