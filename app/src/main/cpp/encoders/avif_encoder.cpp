#include "encoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <avif/avif.h>
#include <cstring>
#include <string>
#include <vector>

namespace komikku {

class AvifEncoderImpl : public IEncoder {
private:
    std::string last_error_;
    std::string version_;

public:
    AvifEncoderImpl() {
        const char* v = avifVersion();
        version_ = v ? v : "1.2.0";
    }

    CodecType GetCodecType() const override {
        return CodecType::AVIF;
    }

    const char* GetCodecName() const override {
        return "AVIF";
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
            last_error_ = "AVIF: Invalid input buffer or dimensions";
            return false;
        }

        uint64_t t_init_start = GetTimestampNs();

        avifEncoder* encoder = avifEncoderCreate();
        if (!encoder) {
            last_error_ = "AVIF: Failed to create encoder instance";
            return false;
        }

        encoder->maxThreads = (options.thread_count > 0) ? options.thread_count : 1;
        encoder->speed = options.effort_or_method; // Speed setting (0-10: fast ~6-8, balanced ~4, slow ~1-2)
        if (encoder->speed < 0) encoder->speed = 0;
        if (encoder->speed > 10) encoder->speed = 10;

        if (options.lossless) {
            encoder->quality = AVIF_QUALITY_LOSSLESS;
            encoder->qualityAlpha = AVIF_QUALITY_LOSSLESS;
        } else {
            encoder->quality = options.quality;
            encoder->qualityAlpha = options.quality;
        }

        avifPixelFormat yuv_format = options.use_yuv420 ? AVIF_PIXEL_FORMAT_YUV420 : AVIF_PIXEL_FORMAT_YUV444;
        avifImage* image = avifImageCreate(width, height, 8, yuv_format);
        if (!image) {
            last_error_ = "AVIF: Failed to create image surface";
            avifEncoderDestroy(encoder);
            return false;
        }

        // Color and transfer configuration: full range sRGB
        image->colorPrimaries = AVIF_COLOR_PRIMARIES_BT709;
        image->transferCharacteristics = AVIF_TRANSFER_CHARACTERISTICS_SRGB;
        image->matrixCoefficients = (yuv_format == AVIF_PIXEL_FORMAT_YUV444 && options.lossless) ?
                                    AVIF_MATRIX_COEFFICIENTS_IDENTITY : AVIF_MATRIX_COEFFICIENTS_BT709;
        image->yuvRange = AVIF_RANGE_FULL;

        avifRGBImage rgb;
        avifRGBImageSetDefaults(&rgb, image);
        rgb.depth = 8;
        rgb.format = AVIF_RGB_FORMAT_RGBA;
        rgb.pixels = const_cast<uint8_t*>(rgba_data);
        rgb.rowBytes = width * 4;

        uint64_t t_init_end = GetTimestampNs();

        avifResult res = avifImageRGBToYUV(image, &rgb);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: RGBToYUV conversion failed: " + std::string(avifResultToString(res));
            avifImageDestroy(image);
            avifEncoderDestroy(encoder);
            return false;
        }

        uint64_t t_encode_start = GetTimestampNs();

        res = avifEncoderAddImage(encoder, image, 1, AVIF_ADD_IMAGE_FLAG_SINGLE);
        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: AddImage failed: " + std::string(avifResultToString(res));
            avifImageDestroy(image);
            avifEncoderDestroy(encoder);
            return false;
        }

        avifRWData raw = AVIF_DATA_EMPTY;
        res = avifEncoderFinish(encoder, &raw);
        uint64_t t_encode_end = GetTimestampNs();

        if (res != AVIF_RESULT_OK) {
            last_error_ = "AVIF: Finish encoding failed: " + std::string(avifResultToString(res));
            avifRWDataFree(&raw);
            avifImageDestroy(image);
            avifEncoderDestroy(encoder);
            return false;
        }

        out_data.assign(raw.data, raw.data + raw.size);

        avifRWDataFree(&raw);
        avifImageDestroy(image);
        avifEncoderDestroy(encoder);

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

std::unique_ptr<IEncoder> CreateAvifEncoder() {
    return std::make_unique<AvifEncoderImpl>();
}

} // namespace komikku
