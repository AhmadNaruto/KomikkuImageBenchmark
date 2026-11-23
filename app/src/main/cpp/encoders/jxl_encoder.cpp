#include "encoder.h"
#include "benchmark.h"
#include "image_types.h"

#include <jxl/encode.h>
#include <jxl/encode_cxx.h>
#include <jxl/thread_parallel_runner.h>
#include <jxl/thread_parallel_runner_cxx.h>

#include <cstring>
#include <string>
#include <vector>

namespace komikku {

class JxlEncoderImpl : public IEncoder {
private:
    std::string last_error_;
    std::string version_;

public:
    JxlEncoderImpl() {
        uint32_t v = JxlEncoderVersion();
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
            last_error_ = "JXL: Invalid input buffer or dimensions";
            return false;
        }

        uint64_t t_init_start = GetTimestampNs();

        auto enc = JxlEncoderMake(nullptr);
        if (!enc) {
            last_error_ = "JXL: Failed to create JxlEncoder";
            return false;
        }

        JxlThreadParallelRunnerPtr runner;
        if (options.thread_count > 1) {
            runner = JxlThreadParallelRunnerMake(
                nullptr,
                static_cast<size_t>(options.thread_count)
            );
            if (JXL_ENC_SUCCESS != JxlEncoderSetParallelRunner(enc.get(), JxlThreadParallelRunner, runner.get())) {
                last_error_ = "JXL: Failed to configure thread parallel runner";
                return false;
            }
        }

        JxlPixelFormat pixel_format;
        pixel_format.num_channels = 4;
        pixel_format.data_type = JXL_TYPE_UINT8;
        pixel_format.endianness = JXL_NATIVE_ENDIAN;
        pixel_format.align = 0;

        JxlBasicInfo basic_info;
        JxlEncoderInitBasicInfo(&basic_info);
        basic_info.xsize = width;
        basic_info.ysize = height;
        basic_info.bits_per_sample = 8;
        basic_info.uses_original_profile = options.lossless ? JXL_TRUE : JXL_FALSE;
        basic_info.num_color_channels = 3;
        basic_info.alpha_bits = 8;

        if (JXL_ENC_SUCCESS != JxlEncoderSetBasicInfo(enc.get(), &basic_info)) {
            last_error_ = "JXL: Failed to set basic info";
            return false;
        }

        JxlColorEncoding color_encoding;
        JxlColorEncodingSetToSRGB(&color_encoding, /*is_gray=*/JXL_FALSE);
        if (JXL_ENC_SUCCESS != JxlEncoderSetColorEncoding(enc.get(), &color_encoding)) {
            last_error_ = "JXL: Failed to set color encoding";
            return false;
        }

        JxlEncoderFrameSettings* settings = JxlEncoderFrameSettingsCreate(enc.get(), nullptr);
        if (!settings) {
            last_error_ = "JXL: Failed to create frame settings";
            return false;
        }

        // Configure effort (1 to 9, default 7)
        int effort = options.effort_or_method;
        if (effort < 1) effort = 1;
        if (effort > 9) effort = 9;
        JxlEncoderFrameSettingsSetOption(settings, JXL_ENC_FRAME_SETTING_EFFORT, effort);

        if (options.lossless) {
            // Modular lossless
            JxlEncoderSetFrameDistance(settings, 0.0f);
            JxlEncoderSetFrameLossless(settings, JXL_TRUE);
        } else {
            // VarDCT lossy
            // Map quality 0-100 to Butteraugli distance (quality 100 = 0.0, 90 = 1.0, 85 = 1.5, etc.)
            float distance = 1.0f;
            if (options.quality >= 100) {
                distance = 0.0f;
            } else if (options.quality >= 90) {
                distance = 1.0f - (options.quality - 90) * 0.05f; // 90 -> 1.0, 95 -> 0.75
            } else {
                distance = 1.0f + (90 - options.quality) * 0.1f;  // 85 -> 1.5
            }
            JxlEncoderSetFrameDistance(settings, distance);
            JxlEncoderSetFrameLossless(settings, JXL_FALSE);
        }

        uint64_t t_init_end = GetTimestampNs();

        size_t buffer_size = static_cast<size_t>(width) * height * 4;
        if (JXL_ENC_SUCCESS != JxlEncoderAddImageFrame(settings, &pixel_format, rgba_data, buffer_size)) {
            last_error_ = "JXL: Failed to add image frame";
            return false;
        }
        JxlEncoderCloseInput(enc.get());

        uint64_t t_encode_start = GetTimestampNs();

        // Process output chunks
        out_data.resize(65536);
        uint8_t* next_out = out_data.data();
        size_t avail_out = out_data.size();

        while (true) {
            JxlEncoderStatus status = JxlEncoderProcessOutput(enc.get(), &next_out, &avail_out);
            if (status == JXL_ENC_ERROR) {
                last_error_ = "JXL: Encoder encountered an internal error";
                return false;
            } else if (status == JXL_ENC_NEED_MORE_OUTPUT) {
                size_t offset = next_out - out_data.data();
                out_data.resize(out_data.size() * 2);
                next_out = out_data.data() + offset;
                avail_out = out_data.size() - offset;
            } else if (status == JXL_ENC_SUCCESS) {
                size_t final_size = next_out - out_data.data();
                out_data.resize(final_size);
                break;
            }
        }

        uint64_t t_encode_end = GetTimestampNs();

        if (out_timing) {
            out_timing->init_ms = ElapsedMs(t_start, t_init_end);
            out_timing->header_ms = ElapsedMs(t_init_end, t_encode_start);
            out_timing->decode_ms = ElapsedMs(t_encode_start, t_encode_end); // Encode latency
            out_timing->color_convert_ms = 0.0;
            out_timing->total_ms = ElapsedMs(t_start, t_encode_end);
        }

        return true;
    }

    const std::string& GetLastError() const override {
        return last_error_;
    }
};

std::unique_ptr<IEncoder> CreateJxlEncoder() {
    return std::make_unique<JxlEncoderImpl>();
}

} // namespace komikku
