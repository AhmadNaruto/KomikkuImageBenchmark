#ifndef KOMIKKU_BENCHMARK_H
#define KOMIKKU_BENCHMARK_H

#include "image_types.h"
#include <string>
#include <vector>

namespace komikku {

// High-resolution monotonic timing
uint64_t GetTimestampNs();
double ElapsedMs(uint64_t start_ns, uint64_t end_ns);

// Memory collection via /proc/self/status
MemoryMetrics ReadMemoryMetrics();

// Safe buffer validation & conversion
bool CheckBufferCapacity(uint32_t width, uint32_t height, PixelFormat fmt, size_t capacity);
size_t CalculateBufferSize(uint32_t width, uint32_t height, PixelFormat fmt);
bool ConvertRgba8888ToRgb565(const uint8_t* rgba, uint32_t width, uint32_t height, uint8_t* rgb565);

// Exact pixel equality validation
bool CompareBuffersExact(const uint8_t* buf_a, const uint8_t* buf_b, size_t size, size_t* out_diff_count);

// SSIM / PSNR basic evaluation for metrics
double ComputePSNR(const uint8_t* orig, const uint8_t* recon, size_t size);

} // namespace komikku

#endif // KOMIKKU_BENCHMARK_H
