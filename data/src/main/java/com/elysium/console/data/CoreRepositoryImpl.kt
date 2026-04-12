package com.elysium.console.data

import com.elysium.console.domain.model.ExecutionType
import com.elysium.console.domain.model.EmulatorCore
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.repository.CoreRepository

/**
 * Concrete implementation of [CoreRepository] with a hardcoded registry
 * of known Libretro-compatible emulator cores.
 *
 * In production, this would scan a cores directory and read
 * retro_system_info from each .so file. For now, the registry
 * covers the most common platforms.
 */
class CoreRepositoryImpl : CoreRepository {

    /**
     * Hardcoded registry of known emulator cores.
     * Each entry maps to a .so file expected in the cores directory.
     */
    private val coreRegistry: Map<String, EmulatorCore> = mapOf(
        "pcsx2_libretro" to EmulatorCore(
            id = "pcsx2_libretro",
            name = "PCSX2",
            platform = Platform.PS2,
            executionType = ExecutionType.INTERNAL_LIBRETRO,
            libraryPath = "pcsx2_libretro_android.so",
            version = "1.7.5",
            supportedExtensions = listOf("iso", "bin", "chd", "cso", "gz")
        ),
        "armsx2_standalone" to EmulatorCore(
            id = "armsx2_standalone",
            name = "ARMSX2",
            platform = Platform.PS2,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://armsx2",
            version = "1.0",
            supportedExtensions = listOf("iso", "bin", "chd", "cso", "gz"),
            androidPackageName = "net.armsx2.armsx2",
            androidActivityName = "net.armsx2.armsx2.MainActivity"
        ),
        "uzuy_standalone" to EmulatorCore(
            id = "uzuy_standalone",
            name = "Uzuy Edge",
            platform = Platform.SWITCH,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://uzuy",
            version = "0.1",
            supportedExtensions = listOf("nsp", "xci"),
            androidPackageName = "org.uzuy.edge",
            androidActivityName = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        "ppsspp_libretro" to EmulatorCore(
            id = "ppsspp_libretro",
            name = "PPSSPP",
            platform = Platform.PSP,
            libraryPath = "ppsspp_libretro_android.so",
            version = "1.17.1",
            supportedExtensions = listOf("iso", "cso", "pbp", "elf", "prx")
        ),
        "dolphin_libretro" to EmulatorCore(
            id = "dolphin_libretro",
            name = "Dolphin",
            platform = Platform.GAMECUBE,
            libraryPath = "dolphin_libretro_android.so",
            version = "5.0",
            supportedExtensions = listOf("gcm", "iso", "gcz", "wbfs", "wad", "dol", "elf")
        ),
        "dolphin_standalone" to EmulatorCore(
            id = "dolphin_standalone",
            name = "Dolphin App",
            platform = Platform.GAMECUBE,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://dolphin",
            version = "5.0",
            supportedExtensions = listOf("gcm", "iso", "gcz", "wbfs", "wad", "dol", "elf"),
            androidPackageName = "org.dolphinemu.dolphinemu",
            androidActivityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity"
        ),
        "melonds_libretro" to EmulatorCore(
            id = "melonds_libretro",
            name = "melonDS",
            platform = Platform.NDS,
            executionType = ExecutionType.INTERNAL_LIBRETRO,
            libraryPath = "melonds_libretro_android.so",
            version = "0.9.5",
            supportedExtensions = listOf("nds", "dsi")
        ),
        "duckstation_libretro" to EmulatorCore(
            id = "duckstation_libretro",
            name = "DuckStation",
            platform = Platform.PS1,
            executionType = ExecutionType.INTERNAL_LIBRETRO,
            libraryPath = "duckstation_libretro_android.so",
            version = "0.1",
            supportedExtensions = listOf("bin", "cue", "chd", "pbp")
        ),
        "citra_libretro" to EmulatorCore(
            id = "citra_libretro",
            name = "Citra (Lemonade)",
            platform = Platform.N3DS,
            libraryPath = "citra_libretro_android.so",
            version = "2104",
            supportedExtensions = listOf("3ds", "3dsx", "cia", "cxi", "app")
        ),
        "mupen64plus_libretro" to EmulatorCore(
            id = "mupen64plus_libretro",
            name = "Mupen64Plus-Next",
            platform = Platform.N64,
            libraryPath = "mupen64plus_next_libretro_android.so",
            version = "2.5",
            supportedExtensions = listOf("z64", "n64", "v64")
        ),
        "snes9x_libretro" to EmulatorCore(
            id = "snes9x_libretro",
            name = "Snes9x",
            platform = Platform.SNES,
            libraryPath = "snes9x_libretro_android.so",
            version = "1.62.3",
            supportedExtensions = listOf("smc", "sfc", "swc", "fig")
        ),
        "genesis_plus_gx_libretro" to EmulatorCore(
            id = "genesis_plus_gx_libretro",
            name = "Genesis Plus GX",
            platform = Platform.GENESIS,
            libraryPath = "genesis_plus_gx_libretro_android.so",
            version = "1.7.4",
            supportedExtensions = listOf("md", "gen", "smd", "bin", "32x", "sms", "gg")
        ),
        "nestopia_libretro" to EmulatorCore(
            id = "nestopia_libretro",
            name = "Nestopia UE",
            platform = Platform.NES,
            libraryPath = "nestopia_libretro_android.so",
            version = "1.52.0",
            supportedExtensions = listOf("nes", "fds", "unf", "unif")
        ),
        "mgba_libretro" to EmulatorCore(
            id = "mgba_libretro",
            name = "mGBA",
            platform = Platform.GBA,
            libraryPath = "mgba_libretro_android.so",
            version = "0.10.3",
            supportedExtensions = listOf("gba", "gbc", "gb")
        ),
        "flycast_libretro" to EmulatorCore(
            id = "flycast_libretro",
            name = "Flycast",
            platform = Platform.DREAMCAST,
            libraryPath = "flycast_libretro_android.so",
            version = "2.3",
            supportedExtensions = listOf("cdi", "gdi", "chd", "cue", "bin")
        )
    )

    override suspend fun getInstalledCores(): List<EmulatorCore> =
        coreRegistry.values.toList()

    override suspend fun getCoreForRom(rom: RomFile): EmulatorCore? {
        val extension = rom.path.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null

        return coreRegistry.values.firstOrNull { core ->
            core.supportsExtension(extension)
        }
    }
}
