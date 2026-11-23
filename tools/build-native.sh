#!/usr/bin/env bash
set -euo pipefail

# Komikku Image Benchmark: Native Build Script
# Targets Android ARM64 (arm64-v8a)

TARGET_ABI="arm64-v8a"
MIN_API=26
BUILD_TYPE="Release"

echo "=========================================="
echo "Building Komikku Native Dependencies"
echo "ABI: $TARGET_ABI | Min API: $MIN_API | Type: $BUILD_TYPE"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$ROOT_DIR/third_party"
BUILD_DIR="$ROOT_DIR/build-native-$TARGET_ABI"

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "$HOME/Android/Sdk/ndk/29.0.14206865" ]; then
        export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/29.0.14206865"
    elif [ -d "$HOME/Android/Sdk/ndk" ]; then
        export ANDROID_NDK_HOME="$(find "$HOME/Android/Sdk/ndk" -maxdepth 1 -mindepth 1 | head -n 1)"
    fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "::warning:: ANDROID_NDK_HOME not found. Using host cmake fallback."
else
    echo "Using NDK: $ANDROID_NDK_HOME"
    TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
    cmake "$ROOT_DIR/app/src/main/cpp" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DANDROID_ABI="$TARGET_ABI" \
        -DANDROID_PLATFORM="android-$MIN_API" \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE="$BUILD_TYPE"
fi

echo "✓ Native build script generated."
