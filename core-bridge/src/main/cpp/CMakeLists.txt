cmake_minimum_required(VERSION 3.22.1)

project("elysium-bridge"
    VERSION 1.0.0
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ══════════════════════════════════════════
# Source Files
# ══════════════════════════════════════════
add_library(elysium-bridge SHARED
    bridge.cpp
)

# ══════════════════════════════════════════
# Aggressive Optimization Flags
# ══════════════════════════════════════════
    target_compile_options(elysium-bridge PRIVATE
        -O3
        -ffast-math
        -fno-exceptions
    -fno-rtti
    -Wall
    -Werror
)

# ══════════════════════════════════════════
# Link-Time Optimization
# ══════════════════════════════════════════
set_target_properties(elysium-bridge PROPERTIES
    INTERPROCEDURAL_OPTIMIZATION FALSE
)

# ══════════════════════════════════════════
# NDK Libraries
# ══════════════════════════════════════════
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(elysium-bridge
    ${log-lib}
    ${android-lib}
    EGL
    GLESv3
    nativewindow
    jnigraphics
    OpenSLES
)

# ══════════════════════════════════════════
# Include Directories
# ══════════════════════════════════════════
target_include_directories(elysium-bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# ══════════════════════════════════════════
# PCSX2 Libretro Core — ARM64 Build Instructions
# ══════════════════════════════════════════
# To compile pcsx2_libretro_android.so from source for arm64-v8a:
#
# 1. Clone the PCSX2 repository:
#    git clone --recursive https://github.com/PCSX2/pcsx2.git
#    cd pcsx2
#
# 2. Set up the Android NDK toolchain:
#    export NDK=/path/to/android-ndk-r26c
#    export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
#    export CC=$TOOLCHAIN/bin/aarch64-linux-android28-clang
#    export CXX=$TOOLCHAIN/bin/aarch64-linux-android28-clang++
#    export AR=$TOOLCHAIN/bin/llvm-ar
#    export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
#
# 3. Configure with CMake for Libretro target:
#    cmake -B build-libretro \
#      -DCMAKE_SYSTEM_NAME=Android \
#      -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
#      -DCMAKE_ANDROID_NDK=$NDK \
#      -DCMAKE_ANDROID_API=28 \
#      -DCMAKE_BUILD_TYPE=Release \
#      -DLIBRETRO=ON \
#      -DCMAKE_C_FLAGS="-O3 -march=armv8-a+simd -flto" \
#      -DCMAKE_CXX_FLAGS="-O3 -march=armv8-a+simd -flto" \
#      -DDISABLE_ADVANCE_SIMD=OFF \
#      .
#
# 4. Build:
#    cmake --build build-libretro --parallel $(nproc)
#
# 5. Output: build-libretro/pcsx2_libretro_android.so
#    Copy to: core-bridge/src/main/jniLibs/arm64-v8a/
# ══════════════════════════════════════════

