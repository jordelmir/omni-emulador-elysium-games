# Elysium Console — Emulator Core Submodules

This directory is the designated location for cloning open-source emulator
cores as Git submodules. Each core is compiled independently and its shared
library (`.so`) is loaded at runtime by the `:core-bridge` module via the
Libretro API.

## Supported Cores

| # | Core          | Platform(s)       | Repository                                          |
|---|---------------|-------------------|-----------------------------------------------------|
| 1 | Libretro Super| Multi-platform    | https://github.com/libretro/libretro-super           |
| 2 | PPSSPP        | PSP               | https://github.com/hrydgard/ppsspp                   |
| 3 | Dolphin       | GameCube / Wii    | https://github.com/dolphin-emu/dolphin               |
| 4 | PCSX2         | PlayStation 2     | https://github.com/PCSX2/pcsx2                       |
| 5 | MelonDS       | Nintendo DS       | https://github.com/melonDS-emu/melonDS               |
| 6 | Lemonade      | Nintendo 3DS      | https://github.com/Lemonade-emu/Lemonade             |
| 7 | Suyu          | Nintendo Switch   | https://git.suyu.dev/suyu/suyu                       |

## Integration Steps

```bash
# From the project root:
cd modules-src

# Clone each core as a submodule
git submodule add https://github.com/libretro/libretro-super.git libretro-super
git submodule add https://github.com/hrydgard/ppsspp.git ppsspp
git submodule add https://github.com/dolphin-emu/dolphin.git dolphin
git submodule add https://github.com/PCSX2/pcsx2.git pcsx2
git submodule add https://github.com/melonDS-emu/melonDS.git melonDS
git submodule add https://github.com/Lemonade-emu/Lemonade.git lemonade
git submodule add https://git.suyu.dev/suyu/suyu.git suyu

# Initialize recursive submodules
git submodule update --init --recursive
```

## Build Notes

Each core has its own CMake/Makefile build system. The compiled `.so` files
should be placed in `core-bridge/src/main/jniLibs/arm64-v8a/` for packaging
into the final APK.

For Libretro-compatible cores, the output is a single `.so` file that
implements the standard Libretro API (`retro_init`, `retro_run`, etc.).
The `:core-bridge` module's `bridge.cpp` dynamically loads these libraries
via `dlopen()` at runtime.
