cmake_minimum_required(VERSION 3.22.1)
project(komikku_benchmark LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Optimization and visibility flags
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O2 -fvisibility=hidden -fdata-sections -ffunction-sections")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O2 -fvisibility=hidden -fdata-sections -ffunction-sections")

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

set(SOURCES
    common/benchmark.cpp
    common/image_buffer.cpp
    common/metrics.cpp
    jni/native_bridge.cpp
)

set(EXTRA_LIBS)

# Configure libwebp
set(LIBWEBP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/source/libwebp")
if (EXISTS "${LIBWEBP_DIR}/CMakeLists.txt")
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(WEBP_LINK_STATIC ON CACHE BOOL "" FORCE)
    set(WEBP_ENABLE_SIMD ON CACHE BOOL "" FORCE)
    set(WEBP_USE_THREAD ON CACHE BOOL "" FORCE)
    set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_LIBWEBPMUX ON CACHE BOOL "" FORCE)
    set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
    set(WEBP_BUILD_FUZZTEST OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LIBWEBP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/libwebp_build" EXCLUDE_FROM_ALL)

    include_directories("${LIBWEBP_DIR}/src")
    list(APPEND SOURCES
        decoders/webp_decoder.cpp
        encoders/webp_encoder.cpp
    )
    add_definitions(-DKOMIKKU_HAS_WEBP)
    list(APPEND EXTRA_LIBS webp webpdemux libwebpmux sharpyuv)
endif()

# Configure libjxl
set(LIBJXL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/source/libjxl")
if (EXISTS "${LIBJXL_DIR}/CMakeLists.txt")
    set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_TOOLS OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_DOXYGEN OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_MANPAGES OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_JNI OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_SJPEG OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_OPENEXR OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_SKCMS ON CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_VIEWERS OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_PLUGINS OFF CACHE BOOL "" FORCE)
    set(JPEGXL_ENABLE_DEVTOOLS OFF CACHE BOOL "" FORCE)
    set(JPEGXL_FORCE_SYSTEM_BROTLI OFF CACHE BOOL "" FORCE)
    set(JPEGXL_FORCE_SYSTEM_HWY OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LIBJXL_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/libjxl_build" EXCLUDE_FROM_ALL)

    include_directories("${LIBJXL_DIR}/lib/include")
    list(APPEND SOURCES
        decoders/jxl_decoder.cpp
        encoders/jxl_encoder.cpp
    )
    add_definitions(-DKOMIKKU_HAS_JXL)
    list(APPEND EXTRA_LIBS jxl jxl_cms jxl_threads hwy brotlicommon brotlidec brotlienc)
endif()

# Configure libavif with dav1d and aom
set(LIBAVIF_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/source/libavif")
if (EXISTS "${LIBAVIF_DIR}/CMakeLists.txt")
    set(AVIF_CODEC_DAV1D "LOCAL" CACHE STRING "" FORCE)
    set(AVIF_CODEC_AOM "LOCAL" CACHE STRING "" FORCE)
    set(AVIF_CODEC_AOM_DECODE OFF CACHE BOOL "" FORCE) # dav1d is mandatory decoder backend
    set(AVIF_CODEC_AOM_ENCODE ON CACHE BOOL "" FORCE)
    set(AVIF_LIBYUV OFF CACHE STRING "" FORCE)
    set(AVIF_ZLIBPNG OFF CACHE STRING "" FORCE)
    set(AVIF_JPEG OFF CACHE STRING "" FORCE)
    set(AVIF_BUILD_APPS OFF CACHE BOOL "" FORCE)
    set(AVIF_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(AVIF_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(AVIF_ENABLE_WERROR OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LIBAVIF_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/libavif_build" EXCLUDE_FROM_ALL)

    include_directories("${LIBAVIF_DIR}/include")
    list(APPEND SOURCES
        decoders/avif_decoder.cpp
        encoders/avif_encoder.cpp
    )
    add_definitions(-DKOMIKKU_HAS_AVIF)
    list(APPEND EXTRA_LIBS avif)
endif()

add_library(komikku_benchmark SHARED ${SOURCES})

target_link_libraries(
    komikku_benchmark
    android
    log
    jnigraphics
    ${EXTRA_LIBS}
)

target_compile_definitions(komikku_benchmark PRIVATE ANDROID_NDK)
