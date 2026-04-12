# Elysium Console — Google IDX Development Environment
# Nix flake for reproducible Android NDK + Emulator development
{ pkgs, ... }: {
  channel = "stable-23.11";

  packages = [
    pkgs.git
    pkgs.cmake
    pkgs.ninja
    pkgs.temurin-jre-bin-17
  ];

  env = {
    JAVA_HOME = "${pkgs.temurin-jre-bin-17}/lib/openjdk";
  };

  idx = {
    extensions = [
      "fwcd.kotlin"
      "mathiasfrohlich.Kotlin"
    ];

    workspace = {
      onCreate = {
        setup-android-sdk = ''
          echo "══════════════════════════════════════════"
          echo "  ELYSIUM CONSOLE — Environment Setup"
          echo "══════════════════════════════════════════"

          # Android SDK Configuration
          # The following are managed by IDX's android extension:
          # - platforms;android-34
          # - build-tools;34.0.0
          # - ndk;26.3.11579264
          # - cmake;3.22.1

          echo ""
          echo "══════════════════════════════════════════"
          echo "  Emulator Core Submodules"
          echo "══════════════════════════════════════════"
          echo ""
          echo "To integrate emulator cores as submodules,"
          echo "run the following commands from the project root:"
          echo ""
          echo "mkdir -p modules-src && cd modules-src"
          echo ""
          echo "# 1. Libretro Super (multi-core build system)"
          echo "git submodule add https://github.com/libretro/libretro-super.git libretro-super"
          echo ""
          echo "# 2. PPSSPP (PlayStation Portable)"
          echo "git submodule add https://github.com/hrydgard/ppsspp.git ppsspp"
          echo ""
          echo "# 3. Dolphin (GameCube / Wii)"
          echo "git submodule add https://github.com/dolphin-emu/dolphin.git dolphin"
          echo ""
          echo "# 4. PCSX2 (PlayStation 2)"
          echo "git submodule add https://github.com/PCSX2/pcsx2.git pcsx2"
          echo ""
          echo "# 5. MelonDS (Nintendo DS)"
          echo "git submodule add https://github.com/melonDS-emu/melonDS.git melonDS"
          echo ""
          echo "# 6. Lemonade (Nintendo 3DS — Citra fork)"
          echo "git submodule add https://github.com/Lemonade-emu/Lemonade.git lemonade"
          echo ""
          echo "# 7. Suyu (Nintendo Switch — Yuzu successor)"
          echo "git submodule add https://git.suyu.dev/suyu/suyu.git suyu"
          echo ""
          echo "══════════════════════════════════════════"
          echo "  Environment Ready — Starting Build"
          echo "══════════════════════════════════════════"

          # Trigger initial build to validate environment
          ./gradlew assembleDebug --no-daemon || echo "Initial build skipped (expected on first setup)"
        '';
      };
    };

    previews = {
      enable = false;
    };
  };

  android = {
    enable = true;
    flutter.enable = false;
    buildTools = [ "34.0.0" ];
    platformVersions = [ "34" ];
    ndkVersions = [ "26.3.11579264" ];
    cmakeVersions = [ "3.22.1" ];
  };
}
