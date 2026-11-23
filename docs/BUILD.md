# Build and Compilation Guide

## Prerequisites
- **JDK**: OpenJDK 21
- **Android SDK**: Compile SDK 35/37, Platform Tools, CMake 3.22.1+
- **Android NDK**: NDK r27c / r29.x
- **Target Architecture**: `arm64-v8a` only

## Local Environment (Termux)
> [!IMPORTANT]
> Running `./gradlew` or `gradle` directly inside Termux is strictly prohibited to protect device memory and CPU stability.
> All compilation and APK builds are automated through GitHub Actions CI.

## Building via GitHub Actions CI
1. Push branch or tag to GitHub repository:
   ```bash
   git push origin main
   ```
2. Or trigger manually via GitHub Actions (`workflow_dispatch`).
3. Download compiled debug or release APK from the Actions workflow run summary.

## Native C++ CMake Configuration
Native CMake builds use:
- C++17 (`-std=c++17`)
- Optimization: `-O2`
- Visibility: `-fvisibility=hidden`
- Stripping and LTO: `-fdata-sections -ffunction-sections -flto`
- STL: `c++_static`
