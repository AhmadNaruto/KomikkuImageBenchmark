#include "decoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <webp/decode.h>
#include <webp/demux.h>
#include <cstring>
#include <string>

namespace komikku {

class WebPDecoderImpl : public IDecoder {
private:
    std::string last_error_;
    std::string version_;

public:
    WebPDecoderImpl() {
        int v = WebPGetDecoderVersion();
        char buf[32];
        snprintf(buf, sizeof(buf), "%d.%d.%d", (v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff);
        version_ = buf;
    }

    CodecType GetCodecType() const override {
        return CodecType::WEBP;
    }

    const char* GetCodecName() const override {
        return "WebP";
    }

    const char* GetVersion() const override {
        return version_.c_str();
    }

    bool ReadInfo(const uint8_t* data, size_t size, ImageInfo* out_info) override {
        if (!data || size < 12 || !out_info) {
            last_error_ = "WebP: Invalid data pointer or size";
            return false;
        }

        WebPBitstreamFeatures features;
        VP8StatusCode status = WebPGetFeatures(data, size, &features);
        if (status != VP8_STATUS_OK) {
            last_error_ = "WebP: Failed to parse header (code " + std::to_string(status) + ")";
            return false;
        }

        if (features.width <= 0 || features.height <= 0) {
            last_error_ = "WebP: Invalid image dimensions";
            return false;
        }

        // Check for integer overflow in dimensions
        uint64_t total_pixels = static_cast<uint64_t>(features.width) * features.height;
        if (total_pixels > 100000000ULL) { // 100 Megapixel safety limit
            last_error_ = "WebP: Dimensions exceed maximum allowed size";
            return false;
        }

        out_info->width = static_cast<uint32_t>(features.width);
        out_info->height = static_cast<uint32_t>(features.height);
        out_info->has_alpha = (features.has_alpha != 0);
        out_info->bit_depth = 8;
        out_info->codec = CodecType::WEBP;
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
        uint64_t t_start = GetTimestampNs();

        if (!in_data || in_size < 12 || !out_buffer) {
            last_error_ = "WebP: Invalid input or output pointer";
            return false;
        }

        uint64_t t_init = GetTimestampNs();

        WebPDecoderConfig config;
        if (!WebPInitDecoderConfig(&config)) {
            last_error_ = "WebP: Failed to initialize decoder config";
            return false;
        }

        // Header parsing
        VP8StatusCode status = WebPGetFeatures(in_data, in_size, &config.input);
        if (status != VP8_STATUS_OK) {
            last_error_ = "WebP: Failed to get features (status " + std::to_string(status) + ")";
            return false;
        }

        uint64_t t_header = GetTimestampNs();

        uint32_t width = static_cast<uint32_t>(config.input.width);
        uint32_t height = static_cast<uint32_t>(config.input.height);

        size_t required_capacity = CalculateBufferSize(width, height, options.output_format);
        if (out_capacity < required_capacity) {
            last_error_ = "WebP: Output buffer capacity (" + std::to_string(out_capacity) +
                          ") smaller than required (" + std::to_string(required_capacity) + ")";
            return false;
        }

        // Configure output color mode and buffer
        config.output.is_external_memory = 1;
        if (options.output_format == PixelFormat::RGB_565) {
            config.output.colorspace = MODE_RGB_565;
            config.output.u.RGBA.rgba = out_buffer;
            config.output.u.RGBA.stride = width * 2;
            config.output.u.RGBA.size = out_capacity;
        } else {
            config.output.colorspace = MODE_RGBA;
            config.output.u.RGBA.rgba = out_buffer;
            config.output.u.RGBA.stride = width * 4;
            config.output.u.RGBA.size = out_capacity;
        }

        // Multithread option
        config.options.use_threads = (options.thread_count > 1) ? 1 : 0;

        uint64_t t_decode_start = GetTimestampNs();
        status = WebPDecode(in_data, in_size, &config);
        uint64_t t_decode_end = GetTimestampNs();

        WebPFreeDecBuffer(&config.output);

        if (status != VP8_STATUS_OK) {
            last_error_ = "WebP: Decode failed with status " + std::to_string(status);
            return false;
        }

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t_start, t_init);
            out_timing->header_ms = ElapsedMs(t_init, t_header);
            out_timing->decode_ms = ElapsedMs(t_decode_start, t_decode_end);
            out_timing->color_convert_ms = 0.0; // Handled directly inside WebP decode pipeline
            out_timing->total_ms = ElapsedMs(t_start, t_decode_end);
        }

        return true;
    }

    const std::string& GetLastError() const override {
        return last_error_;
    }
};

std::unique_ptr<IDecoder> CreateWebPDecoder() {
    return std::make_unique<WebPDecoderImpl>();
}

} // namespace komikku
