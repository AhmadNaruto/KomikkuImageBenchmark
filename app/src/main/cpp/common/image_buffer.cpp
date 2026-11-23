#include "benchmark.h"
#include <limits>
#include <cstring>

namespace komikku {

size_t CalculateBufferSize(uint32_t width, uint32_t height, PixelFormat fmt) {
    if (width == 0 || height == 0) return 0;
    uint64_t pixels = static_cast<uint64_t>(width) * height;
    uint64_t bpp = (fmt == PixelFormat::RGBA_8888) ? 4 : 2;
    uint64_t total = pixels * bpp;
    if (total > std::numeric_limits<size_t>::max()) {
        return 0; // Overflow
    }
    return static_cast<size_t>(total);
}

bool CheckBufferCapacity(uint32_t width, uint32_t height, PixelFormat fmt, size_t capacity) {
    size_t required = CalculateBufferSize(width, height, fmt);
    if (required == 0 || capacity < required) {
        return false;
    }
    return true;
}

bool ConvertRgba8888ToRgb565(const uint8_t* rgba, uint32_t width, uint32_t height, uint8_t* rgb565) {
    if (!rgba || !rgb565) return false;
    size_t pixel_count = static_cast<size_t>(width) * height;
    uint16_t* dst = reinterpret_cast<uint16_t*>(rgb565);

    for (size_t i = 0; i < pixel_count; ++i) {
        uint8_t r = rgba[i * 4 + 0];
        uint8_t g = rgba[i * 4 + 1];
        uint8_t b = rgba[i * 4 + 2];

        // 5-6-5 conversion
        uint16_t r5 = (r >> 3) & 0x1F;
        uint16_t g6 = (g >> 2) & 0x3F;
        uint16_t b5 = (b >> 3) & 0x1F;

        dst[i] = static_cast<uint16_t>((r5 << 11) | (g6 << 5) | b5);
    }
    return true;
}

bool CompareBuffersExact(const uint8_t* buf_a, const uint8_t* buf_b, size_t size, size_t* out_diff_count) {
    if (!buf_a || !buf_b) return false;
    size_t diffs = 0;
    for (size_t i = 0; i < size; ++i) {
        if (buf_a[i] != buf_b[i]) {
            diffs++;
        }
    }
    if (out_diff_count) {
        *out_diff_count = diffs;
    }
    return diffs == 0;
}

} // namespace komikku
