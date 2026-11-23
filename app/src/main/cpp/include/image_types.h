#ifndef KOMIKKU_IMAGE_TYPES_H
#define KOMIKKU_IMAGE_TYPES_H

#include <cstdint>
#include <string>
#include <vector>

namespace komikku {

enum class CodecType : int32_t {
    WEBP = 0,
    JXL = 1,
    AVIF = 2,
    UNKNOWN = -1
};

enum class PixelFormat : int32_t {
    RGBA_8888 = 0,
    RGB_565 = 1,
    UNKNOWN = -1
};

struct ImageInfo {
    uint32_t width{0};
    uint32_t height{0};
    bool has_alpha{false};
    uint32_t bit_depth{8};
    CodecType codec{CodecType::UNKNOWN};
};

struct DecodeOptions {
    PixelFormat output_format{PixelFormat::RGBA_8888};
    int32_t thread_count{1};
    bool verify_lossless{false};
};

struct EncodeOptions {
    CodecType codec{CodecType::WEBP};
    bool lossless{true};
    int32_t quality{90};          // 0-100 or codec-mapped
    int32_t effort_or_method{4};  // WebP method 4/6, JXL effort 1-9, AVIF speed 0-10
    int32_t thread_count{1};
    bool use_yuv420{false};       // AVIF YUV420 vs YUV444
};

struct BenchmarkTiming {
    double init_ms{0.0};
    double header_ms{0.0};
    double decode_ms{0.0};
    double color_convert_ms{0.0};
    double total_ms{0.0};
};

struct MemoryMetrics {
    int64_t rss_kb{0};
    int64_t peak_rss_kb{0};
    int64_t native_heap_kb{0};
};

struct BenchmarkSample {
    double decode_ms{0.0};
    double encode_ms{0.0};
    int64_t peak_rss_kb{0};
    size_t file_size{0};
    bool success{false};
};

inline const char* CodecToString(CodecType codec) {
    switch (codec) {
        case CodecType::WEBP: return "WebP";
        case CodecType::JXL:  return "JPEG XL";
        case CodecType::AVIF: return "AVIF";
        default: return "Unknown";
    }
}

inline const char* PixelFormatToString(PixelFormat fmt) {
    switch (fmt) {
        case PixelFormat::RGBA_8888: return "RGBA_8888";
        case PixelFormat::RGB_565:   return "RGB_565";
        default: return "Unknown";
    }
}

} // namespace komikku

#endif // KOMIKKU_IMAGE_TYPES_H
