#include "encoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <webp/encode.h>
#include <cstring>
#include <string>

namespace komikku {

class WebPEncoderImpl : public IEncoder {
private:
    std::string last_error_;
    std::string version_;

public:
    WebPEncoderImpl() {
        int v = WebPGetEncoderVersion();
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

    bool Encode(
        const uint8_t* rgba_data,
        uint32_t width,
        uint32_t height,
        const EncodeOptions& options,
        std::vector<uint8_t>& out_data,
        BenchmarkTiming* out_timing
    ) override {
        uint64_t t_start = GetTimestampNs();

        if (!rgba_data || width == 0 || height == 0) {
            last_error_ = "WebP: Invalid input buffer or dimensions";
            return false;
        }

        uint64_t t_init_start = GetTimestampNs();

        WebPConfig config;
        if (!WebPConfigInit(&config)) {
            last_error_ = "WebP: Failed to initialize WebPConfig";
            return false;
        }

        // Configure encoding mode
        config.lossless = options.lossless ? 1 : 0;
        config.quality = static_cast<float>(options.quality);
        config.method = options.effort_or_method; // 4 or 6
        config.thread_level = (options.thread_count > 1) ? 1 : 0;

        if (!WebPValidateConfig(&config)) {
            last_error_ = "WebP: Invalid encoder configuration";
            return false;
        }

        WebPPicture pic;
        if (!WebPPictureInit(&pic)) {
            last_error_ = "WebP: Failed to initialize WebPPicture";
            return false;
        }

        pic.use_argb = config.lossless;
        pic.width = static_cast<int>(width);
        pic.height = static_cast<int>(height);

        WebPMemoryWriter writer;
        WebPMemoryWriterInit(&writer);
        pic.writer = WebPMemoryWrite;
        pic.custom_ptr = &writer;

        uint64_t t_init_end = GetTimestampNs();

        // Import RGBA buffer
        if (!WebPPictureImportRGBA(&pic, rgba_data, width * 4)) {
            last_error_ = "WebP: Failed to import RGBA image pixels";
            WebPPictureFree(&pic);
            WebPMemoryWriterClear(&writer);
            return false;
        }

        uint64_t t_encode_start = GetTimestampNs();
        int ok = WebPEncode(&config, &pic);
        uint64_t t_encode_end = GetTimestampNs();

        if (!ok) {
            last_error_ = "WebP: Encode failed (error code " + std::to_string(pic.error_code) + ")";
            WebPPictureFree(&pic);
            WebPMemoryWriterClear(&writer);
            return false;
        }

        out_data.assign(writer.mem, writer.mem + writer.size);

        WebPPictureFree(&pic);
        WebPMemoryWriterClear(&writer);

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t_start, t_init_end);
            out_timing->header_ms = ElapsedMs(t_init_end, t_encode_start);
            out_timing->decode_ms = ElapsedMs(t_encode_start, t_encode_end); // Encode duration
            out_timing->color_convert_ms = 0.0;
            out_timing->total_ms = ElapsedMs(t_start, t_encode_end);
        }

        return true;
    }

    const std::string& GetLastError() const override {
        return last_error_;
    }
};

std::unique_ptr<IEncoder> CreateWebPEncoder() {
    return std::make_unique<WebPEncoderImpl>();
}

} // namespace komikku
