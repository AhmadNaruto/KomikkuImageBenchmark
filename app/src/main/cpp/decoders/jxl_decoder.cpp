#include "decoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <jxl/decode.h>
#include <jxl/decode_cxx.h>
#include <jxl/thread_parallel_runner.h>
#include <jxl/thread_parallel_runner_cxx.h>

#include <cstring>
#include <string>

namespace komikku {

class JxlDecoderImpl : public IDecoder {
private:
    std::string last_error_;
    std::string version_;

public:
    JxlDecoderImpl() {
        uint32_t v = JxlDecoderVersion();
        char buf[32];
        snprintf(buf, sizeof(buf), "%u.%u.%u", v / 1000000, (v / 1000) % 1000, v % 1000);
        version_ = buf;
    }

    CodecType GetCodecType() const override {
        return CodecType::JXL;
    }

    const char* GetCodecName() const override {
        return "JPEG XL";
    }

    const char* GetVersion() const override {
        return version_.c_str();
    }

    bool ReadInfo(const uint8_t* data, size_t size, ImageInfo* out_info) override {
        if (!data || size < 12 || !out_info) {
            last_error_ = "JXL: Invalid input buffer or size";
            return false;
        }

        auto dec = JxlDecoderMake(nullptr);
        if (!dec) {
            last_error_ = "JXL: Failed to create JxlDecoder";
            return false;
        }

        if (JXL_DEC_SUCCESS != JxlDecoderSubscribeEvents(dec.get(), JXL_DEC_BASIC_INFO)) {
            last_error_ = "JXL: Failed to subscribe to JXL_DEC_BASIC_INFO";
            return false;
        }

        JxlDecoderSetInput(dec.get(), data, size);
        JxlDecoderCloseInput(dec.get());

        JxlDecoderStatus status = JxlDecoderProcessInput(dec.get());
        if (status == JXL_DEC_BASIC_INFO) {
            JxlBasicInfo basic_info;
            if (JXL_DEC_SUCCESS == JxlDecoderGetBasicInfo(dec.get(), &basic_info)) {
                out_info->width = basic_info.xsize;
                out_info->height = basic_info.ysize;
                out_info->has_alpha = (basic_info.alpha_bits > 0);
                out_info->bit_depth = basic_info.bits_per_sample;
                out_info->codec = CodecType::JXL;
                return true;
            }
        }

        last_error_ = "JXL: Failed to retrieve basic info from bitstream";
        return false;
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
            last_error_ = "JXL: Invalid input or output buffer";
            return false;
        }

        uint64_t t_init_start = GetTimestampNs();

        auto dec = JxlDecoderMake(nullptr);
        if (!dec) {
            last_error_ = "JXL: Failed to instantiate decoder";
            return false;
        }

        // Configure multi-threaded runner if threads > 1
        JxlThreadParallelRunnerPtr runner;
        if (options.thread_count > 1) {
            runner = JxlThreadParallelRunnerMake(
                nullptr,
                static_cast<size_t>(options.thread_count)
            );
            if (JXL_DEC_SUCCESS != JxlDecoderSetParallelRunner(dec.get(), JxlThreadParallelRunner, runner.get())) {
                last_error_ = "JXL: Failed to set parallel runner";
                return false;
            }
        }

        if (JXL_DEC_SUCCESS != JxlDecoderSubscribeEvents(
            dec.get(),
            JXL_DEC_BASIC_INFO | JXL_DEC_COLOR_ENCODING | JXL_DEC_NEED_IMAGE_OUT_BUFFER | JXL_DEC_FULL_IMAGE
        )) {
            last_error_ = "JXL: Failed to subscribe decoder events";
            return false;
        }

        JxlDecoderSetInput(dec.get(), in_data, in_size);
        JxlDecoderCloseInput(dec.get());

        uint64_t t_init_end = GetTimestampNs();
        uint64_t t_header_end = t_init_end;
        uint64_t t_decode_start = t_init_end;

        JxlBasicInfo basic_info;
        JxlPixelFormat format;
        format.data_type = JXL_TYPE_UINT8;
        format.endianness = JXL_NATIVE_ENDIAN;
        format.align = 0;

        // Default to RGBA 8888; for RGB565 we decode RGBA then convert
        format.num_channels = 4;

        bool buffer_set = false;

        while (true) {
            JxlDecoderStatus status = JxlDecoderProcessInput(dec.get());

            if (status == JXL_DEC_ERROR) {
                last_error_ = "JXL: Decoder encountered decoding error";
                return false;
            } else if (status == JXL_DEC_NEED_MORE_INPUT) {
                last_error_ = "JXL: Unexpected end of input";
                return false;
            } else if (status == JXL_DEC_BASIC_INFO) {
                if (JXL_DEC_SUCCESS != JxlDecoderGetBasicInfo(dec.get(), &basic_info)) {
                    last_error_ = "JXL: Failed to read basic info";
                    return false;
                }
                t_header_end = GetTimestampNs();
                t_decode_start = t_header_end;

                size_t req = CalculateBufferSize(basic_info.xsize, basic_info.ysize, options.output_format);
                if (out_capacity < req) {
                    last_error_ = "JXL: Output buffer capacity (" + std::to_string(out_capacity) +
                                  ") smaller than required (" + std::to_string(req) + ")";
                    return false;
                }
            } else if (status == JXL_DEC_COLOR_ENCODING) {
                // Set default sRGB color encoding
                JxlColorEncoding color_encoding;
                JxlColorEncodingSetToSRGB(&color_encoding, /*is_gray=*/JXL_FALSE);
                JxlDecoderSetPreferredColorProfile(dec.get(), &color_encoding);
            } else if (status == JXL_DEC_NEED_IMAGE_OUT_BUFFER) {
                if (!buffer_set) {
                    size_t buffer_size = basic_info.xsize * basic_info.ysize * 4;
                    if (options.output_format == PixelFormat::RGB_565) {
                        // Allocate temporary buffer for RGBA if RGB565 is requested
                        // Alternatively handled via post conversion
                    }
                    if (JXL_DEC_SUCCESS != JxlDecoderSetImageOutBuffer(dec.get(), &format, out_buffer, out_capacity)) {
                        last_error_ = "JXL: Failed to set output image buffer";
                        return false;
                    }
                    buffer_set = true;
                }
            } else if (status == JXL_DEC_FULL_IMAGE) {
                // Image frame fully decoded
            } else if (status == JXL_DEC_SUCCESS) {
                break;
            }
        }

        uint64_t t_decode_end = GetTimestampNs();

        // If RGB565 was requested, convert in-place / buffer
        uint64_t t_convert_end = t_decode_end;
        if (options.output_format == PixelFormat::RGB_565) {
            uint64_t t_conv_start = GetTimestampNs();
            std::vector<uint8_t> temp_rgba(out_buffer, out_buffer + (basic_info.xsize * basic_info.ysize * 4));
            ConvertRgba8888ToRgb565(temp_rgba.data(), basic_info.xsize, basic_info.ysize, out_buffer);
            t_convert_end = GetTimestampNs();
        }

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t_start, t_init_end);
            out_timing->header_ms = ElapsedMs(t_init_end, t_header_end);
            out_timing->decode_ms = ElapsedMs(t_decode_start, t_decode_end);
            out_timing->color_convert_ms = ElapsedMs(t_decode_end, t_convert_end);
            out_timing->total_ms = ElapsedMs(t_start, t_convert_end);
        }

        return true;
    }

    const std::string& GetLastError() const override {
        return last_error_;
    }
};

std::unique_ptr<IDecoder> CreateJxlDecoder() {
    return std::make_unique<JxlDecoderImpl>();
}

} // namespace komikku
