package com.elysium.console.data

import com.elysium.console.domain.model.ExecutionType
import com.elysium.console.domain.model.EmulatorCore
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.repository.CoreRepository

/**
 * Concrete implementation of [CoreRepository] synchronized with actual
 * bundled binaries and supported external standalone emulators.
 */
class CoreRepositoryImpl : CoreRepository {

    private val coreRegistry: Map<String, EmulatorCore> = mapOf(
        // Internal Libretro Cores (Actual bundled binaries)
        "snes9x_libretro" to EmulatorCore(
            id = "snes9x_libretro",
            name = "Snes9x",
            platform = Platform.SNES,
            libraryPath = "snes9x_libretro_android.so",
            version = "1.62.3",
            supportedExtensions = listOf("smc", "sfc", "swc")
        ),
        "mgba_libretro" to EmulatorCore(
            id = "mgba_libretro",
            name = "mGBA",
            platform = Platform.GBA,
            libraryPath = "mgba_libretro_android.so",
            version = "0.10.3",
            supportedExtensions = listOf("gba", "gbc", "gb")
        ),
        "melonds_libretro" to EmulatorCore(
            id = "melonds_libretro",
            name = "melonDS",
            platform = Platform.NDS,
            libraryPath = "melonds_libretro_android.so",
            version = "0.9.5",
            supportedExtensions = listOf("nds", "dsi")
        ),
        "ppsspp_libretro" to EmulatorCore(
            id = "ppsspp_libretro",
            name = "PPSSPP (Core)",
            platform = Platform.PSP,
            libraryPath = "ppsspp_libretro_android.so",
            version = "1.17.1",
            supportedExtensions = listOf("iso", "cso", "pbp")
        ),
        "nestopia_libretro" to EmulatorCore(
            id = "nestopia_libretro",
            name = "Nestopia UE",
            platform = Platform.NES,
            libraryPath = "nestopia_libretro_android.so",
            version = "1.52.0",
            supportedExtensions = listOf("nes", "fds")
        ),
        "genesis_plus_gx_libretro" to EmulatorCore(
            id = "genesis_plus_gx_libretro",
            name = "Genesis Plus GX",
            platform = Platform.GENESIS,
            libraryPath = "genesis_plus_gx_libretro_android.so",
            version = "1.7.4",
            supportedExtensions = listOf("md", "gen", "smd")
        ),

        // External Standalone Emulators (Intent-based Hybrid Orchestration)
        "armsx2_standalone" to EmulatorCore(
            id = "armsx2_standalone",
            name = "AetherSX2 / NetherSX2",
            platform = Platform.PS2,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://armsx2",
            version = "v1.5",
            supportedExtensions = listOf("iso", "chd", "gz"),
            androidPackageName = "xyz.aethersx2.android",
            androidActivityName = "xyz.aethersx2.android.MainActivity"
        ),
        "uzuy_standalone" to EmulatorCore(
            id = "uzuy_standalone",
            name = "Uzuy Edge (Switch)",
            platform = Platform.SWITCH,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://uzuy",
            version = "Early Access",
            supportedExtensions = listOf("nsp", "xci"),
            androidPackageName = "org.uzuy.edge",
            androidActivityName = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        ),
        "dolphin_standalone" to EmulatorCore(
            id = "dolphin_standalone",
            name = "Dolphin (GameCube/Wii)",
            platform = Platform.GAMECUBE,
            executionType = ExecutionType.EXTERNAL_INTENT,
            libraryPath = "intent://dolphin",
            version = "5.0-2026",
            supportedExtensions = listOf("gcm", "iso", "wbfs"),
            androidPackageName = "org.dolphinemu.dolphinemu",
            androidActivityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity"
        )
    )

    override suspend fun getInstalledCores(): List<EmulatorCore> =
        coreRegistry.values.toList()

    override suspend fun getCoreForRom(rom: RomFile): EmulatorCore? {
        val extension = rom.path.substringAfterLast('.', "").lowercase()
        return coreRegistry.values.firstOrNull { it.supportedExtensions.contains(extension) }
    }
}
