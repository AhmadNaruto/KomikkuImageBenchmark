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

# Milestone 2-4 integrations will link third_party libraries here
# e.g.:
# if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/source/libwebp/CMakeLists.txt")
#     add_subdirectory(...)
# endif()

add_library(komikku_benchmark SHARED ${SOURCES})

target_link_libraries(
    komikku_benchmark
    android
    log
    jnigraphics
)

target_compile_definitions(komikku_benchmark PRIVATE ANDROID_NDK)
