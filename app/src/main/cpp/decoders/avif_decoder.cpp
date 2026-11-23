#include "decoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <avif/avif.h>
#include <cstring>
#include <string>

namespace komikku {

class AvifDecoderImpl : public IDecoder {
private:
    std::string last_error_;
    std::string version_;

public:
    AvifDecoderImpl() {
        const char* v = avifVersion();
        const char* backend = avifCodecName(AVIF_CODEC_CHOICE_DAV1D, AVIF_CODEC_FLAG_CAN_DECODE);
        version_ = std::string(v ? v : "1.2.0") + " (" + (backend ? backend : "dav1d") + ")";
    }

    CodecType GetCodecType() const override {
        return CodecType::AVIF;
    }

    const char* GetCodecName() const override {
        return "AVIF (dav1d)";
    }

    const char* GetVersion() const override {
        return version_.c_str();
    }

    bool ReadInfo(const uint8_t* data, size_t size, ImageInfo* out_info) override {
        if (!data || size < 12 || !out_info) {
            last_error_ = "AVIF: Invalid data pointer or size";
            return false;
        }

        avifDecoder* decoder = avifDecoderCreate();
        if (!decoder) {
            last_error_ = "AVIF: Failed to allocate decoder instance";
            return false;
        }

        // Force dav1d decoder backend strictly
        decoder->codecChoice = AVIF_CODEC_CHOICE_DAV1D;

        avifResult res = avifDecoderSetIOMemory(decoder, data, size);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Failed to set IO buffer: " + std::string(avifResultToString(res));
            avifDecoderDestroy(decoder);
            return false;
        }

        res = avifDecoderParse(decoder);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Parse failed: " + std::string(avifResultToString(res));
            avifDecoderDestroy(decoder);
            return false;
        }

        out_info->width = decoder->image->width;
        out_info->height = decoder->image->height;
        out_info->has_alpha = (decoder->alphaPresent == AVIF_TRUE);
        out_info->bit_depth = decoder->image->depth;
        out_info->codec = CodecType::AVIF;

        avifDecoderDestroy(decoder);
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
            last_error_ = "AVIF: Invalid input or output pointer";
            return false;
        }

        uint64_t t_init_start = GetTimestampNs();

        avifDecoder* decoder = avifDecoderCreate();
        if (!decoder) {
            last_error_ = "AVIF: Failed to instantiate decoder";
            return false;
        }

        // Enforce mandatory dav1d backend
        decoder->codecChoice = AVIF_CODEC_CHOICE_DAV1D;
        decoder->maxThreads = (options.thread_count > 0) ? options.thread_count : 1;

        avifResult res = avifDecoderSetIOMemory(decoder, in_data, in_size);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: SetIOMemory error: " + std::string(avifResultToString(res));
            avifDecoderDestroy(decoder);
            return false;
        }

        uint64_t t_init_end = GetTimestampNs();

        res = avifDecoderParse(decoder);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Header parse error: " + std::string(avifResultToString(res));
            avifDecoderDestroy(decoder);
            return false;
        }

        uint64_t t_header_end = GetTimestampNs();

        uint32_t width = decoder->image->width;
        uint32_t height = decoder->image->height;

        size_t req = CalculateBufferSize(width, height, options.output_format);
        if (out_capacity < req) {
            last_error_ = "AVIF: Output buffer (" + std::to_string(out_capacity) +
                          ") smaller than required (" + std::to_string(req) + ")";
            avifDecoderDestroy(decoder);
            return false;
        }

        uint64_t t_decode_start = GetTimestampNs();
        res = avifDecoderNextImage(decoder);
        uint64_t t_decode_end = GetTimestampNs();

        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Decode next image error: " + std::string(avifResultToString(res));
            avifDecoderDestroy(decoder);
            return false;
        }

        // Convert YUV to RGB output
        uint64_t t_convert_start = GetTimestampNs();

        avifRGBImage rgb;
        avifRGBImageSetDefaults(&rgb, decoder->image);
        rgb.depth = 8;
        rgb.format = AVIF_RGB_FORMAT_RGBA;

        if (options.output_format == PixelFormat::RGB_565) {
            std::vector<uint8_t> temp_rgba(width * height * 4);
            rgb.pixels = temp_rgba.data();
            rgb.rowBytes = width * 4;
            res = avifImageYUVToRGB(decoder->image, &rgb);
            if (res == AVIF_RESULT_OK) {
                ConvertRgba8888ToRgb565(temp_rgba.data(), width, height, out_buffer);
            }
        } else {
            rgb.pixels = out_buffer;
            rgb.rowBytes = width * 4;
            res = avifImageYUVToRGB(decoder->image, &rgb);
        }

        uint64_t t_convert_end = GetTimestampNs();

        avifDecoderDestroy(decoder);

        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Color conversion error: " + std::string(avifResultToString(res));
            return false;
        }

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t_start, t_init_end);
            out_timing->header_ms = ElapsedMs(t_init_end, t_header_end);
            out_timing->decode_ms = ElapsedMs(t_decode_start, t_decode_end);
            out_timing->color_convert_ms = ElapsedMs(t_convert_start, t_convert_end);
            out_timing->total_ms = ElapsedMs(t_start, t_convert_end);
        }

        return true;
    }

    const std::string& GetLastError() const override {
        return last_error_;
    }
};

std::unique_ptr<IDecoder> CreateAvifDecoder() {
    return std::make_unique<AvifDecoderImpl>();
}

} // namespace komikku
