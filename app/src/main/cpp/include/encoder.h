#ifndef KOMIKKU_ENCODER_H
#define KOMIKKU_ENCODER_H

#include "image_types.h"
#include <memory>
#include <string>
#include <vector>

namespace komikku {

class IEncoder {
public:
    virtual ~IEncoder() = default;

    virtual CodecType GetCodecType() const = 0;
    virtual const char* GetCodecName() const = 0;
    virtual const char* GetVersion() const = 0;

    virtual bool Encode(
        const uint8_t* rgba_data,
        uint32_t width,
        uint32_t height,
        const EncodeOptions& options,
        std::vector<uint8_t>& out_data,
        BenchmarkTiming* out_timing
    ) = 0;

    virtual const std::string& GetLastError() const = 0;

    static std::unique_ptr<IEncoder> Create(CodecType type);
};

} // namespace komikku

#endif // KOMIKKU_ENCODER_H
