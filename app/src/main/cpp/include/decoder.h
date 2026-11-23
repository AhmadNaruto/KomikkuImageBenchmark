#ifndef KOMIKKU_DECODER_H
#define KOMIKKU_DECODER_H

#include "image_types.h"
#include <memory>
#include <string>

namespace komikku {

class IDecoder {
public:
    virtual ~IDecoder() = default;

    virtual CodecType GetCodecType() const = 0;
    virtual const char* GetCodecName() const = 0;
    virtual const char* GetVersion() const = 0;

    virtual bool ReadInfo(const uint8_t* data, size_t size, ImageInfo* out_info) = 0;

    virtual bool Decode(
        const uint8_t* in_data,
        size_t in_size,
        const DecodeOptions& options,
        uint8_t* out_buffer,
        size_t out_capacity,
        BenchmarkTiming* out_timing
    ) = 0;

    virtual const std::string& GetLastError() const = 0;

    static std::unique_ptr<IDecoder> Create(CodecType type);
};

} // namespace komikku

#endif // KOMIKKU_DECODER_H
