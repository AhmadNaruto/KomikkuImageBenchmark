#include "benchmark.h"
#include "decoder.h"
#include "encoder.h"
#include <vector>
#include <string>

namespace komikku {

// Base fallback/stub decoder for Milestone 1 sanity check
class StubDecoder : public IDecoder {
private:
    CodecType type_;
    std::string name_;
    std::string last_error_;
public:
    StubDecoder(CodecType type, const char* name) : type_(type), name_(name) {}

    CodecType GetCodecType() const override { return type_; }
    const char* GetCodecName() const override { return name_.c_str(); }
    const char* GetVersion() const override { return "1.0.0-stub"; }

    bool ReadInfo(const uint8_t* data, size_t size, ImageInfo* out_info) override {
        if (!data || size < 16 || !out_info) {
            last_error_ = "Invalid data or buffer";
            return false;
        }
        out_info->width = 1600;
        out_info->height = 2400;
        out_info->has_alpha = true;
        out_info->bit_depth = 8;
        out_info->codec = type_;
        return true;
    }

    bool Decode(
        const uint8_t* in_data,
        size_t in_size,
        const DecodeOptions& options,
        uint8_t* out_buffer,
        size_t out_capacity,
        BenchmarkTiming* out_timing
    ) override {
        uint64_t t0 = GetTimestampNs();
        ImageInfo info;
        if (!ReadInfo(in_data, in_size, &info)) return false;
        uint64_t t1 = GetTimestampNs();

        size_t required = CalculateBufferSize(info.width, info.height, options.output_format);
        if (out_capacity < required) {
            last_error_ = "Output buffer too small";
            return false;
        }

        // Fill test pattern (RGBA solid / checkerboard)
        memset(out_buffer, 0x7F, required);
        uint64_t t2 = GetTimestampNs();

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t0, t0);
            out_timing->header_ms = ElapsedMs(t0, t1);
            out_timing->decode_ms = ElapsedMs(t1, t2);
            out_timing->color_convert_ms = 0.0;
            out_timing->total_ms = ElapsedMs(t0, t2);
        }
        return true;
    }

    const std::string& GetLastError() const override { return last_error_; }
};

// Base fallback/stub encoder for Milestone 1 sanity check
class StubEncoder : public IEncoder {
private:
    CodecType type_;
    std::string name_;
    std::string last_error_;
public:
    StubEncoder(CodecType type, const char* name) : type_(type), name_(name) {}

    CodecType GetCodecType() const override { return type_; }
    const char* GetCodecName() const override { return name_.c_str(); }
    const char* GetVersion() const override { return "1.0.0-stub"; }

    bool Encode(
        const uint8_t* rgba_data,
        uint32_t width,
        uint32_t height,
        const EncodeOptions& options,
        std::vector<uint8_t>& out_data,
        BenchmarkTiming* out_timing
    ) override {
        uint64_t t0 = GetTimestampNs();
        if (!rgba_data || width == 0 || height == 0) {
            last_error_ = "Invalid input buffer";
            return false;
        }

        // Mock compressed payload
        size_t mock_size = (width * height * 4) / 10;
        out_data.resize(mock_size, 0xAB);
        uint64_t t1 = GetTimestampNs();

        if (out_timing) {
            out_timing->init_ms = 0.0;
            out_timing->header_ms = 0.0;
            out_timing->decode_ms = 0.0;
            out_timing->color_convert_ms = 0.0;
            out_timing->total_ms = ElapsedMs(t0, t1);
        }
        return true;
    }

    const std::string& GetLastError() const override { return last_error_; }
};

#if !defined(KOMIKKU_HAS_WEBP)
std::unique_ptr<IDecoder> CreateWebPDecoder() {
    return std::make_unique<StubDecoder>(CodecType::WEBP, "WebP-Stub");
}
std::unique_ptr<IEncoder> CreateWebPEncoder() {
    return std::make_unique<StubEncoder>(CodecType::WEBP, "WebP-Stub");
}
#endif

#if !defined(KOMIKKU_HAS_JXL)
std::unique_ptr<IDecoder> CreateJxlDecoder() {
    return std::make_unique<StubDecoder>(CodecType::JXL, "JXL-Stub");
}
std::unique_ptr<IEncoder> CreateJxlEncoder() {
    return std::make_unique<StubEncoder>(CodecType::JXL, "JXL-Stub");
}
#endif

#if !defined(KOMIKKU_HAS_AVIF)
std::unique_ptr<IDecoder> CreateAvifDecoder() {
    return std::make_unique<StubDecoder>(CodecType::AVIF, "AVIF-Stub");
}
std::unique_ptr<IEncoder> CreateAvifEncoder() {
    return std::make_unique<StubEncoder>(CodecType::AVIF, "AVIF-Stub");
}
#endif

std::unique_ptr<IDecoder> IDecoder::Create(CodecType type) {
    switch (type) {
        case CodecType::WEBP: return CreateWebPDecoder();
        case CodecType::JXL:  return CreateJxlDecoder();
        case CodecType::AVIF: return CreateAvifDecoder();
        default: return nullptr;
    }
}

std::unique_ptr<IEncoder> IEncoder::Create(CodecType type) {
    switch (type) {
        case CodecType::WEBP: return CreateWebPEncoder();
        case CodecType::JXL:  return CreateJxlEncoder();
        case CodecType::AVIF: return CreateAvifEncoder();
        default: return nullptr;
    }
}

} // namespace komikku
