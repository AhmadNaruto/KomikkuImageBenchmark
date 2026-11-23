#include "benchmark.h"
#include <time.h>
#include <cstdio>
#include <cmath>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "KomikkuBenchmark"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace komikku {

uint64_t GetTimestampNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<uint64_t>(ts.tv_sec) * 1000000000ULL + static_cast<uint64_t>(ts.tv_nsec);
}

double ElapsedMs(uint64_t start_ns, uint64_t end_ns) {
    if (end_ns < start_ns) return 0.0;
    return static_cast<double>(end_ns - start_ns) / 1000000.0;
}

MemoryMetrics ReadMemoryMetrics() {
    MemoryMetrics metrics;
    FILE* fp = fopen("/proc/self/status", "r");
    if (!fp) {
        return metrics;
    }

    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line + 6, "%ld", &metrics.rss_kb);
        } else if (strncmp(line, "VmHWM:", 6) == 0) {
            sscanf(line + 6, "%ld", &metrics.peak_rss_kb);
        } else if (strncmp(line, "VmData:", 7) == 0) {
            sscanf(line + 7, "%ld", &metrics.native_heap_kb);
        }
    }
    fclose(fp);
    return metrics;
}

double ComputePSNR(const uint8_t* orig, const uint8_t* recon, size_t size) {
    if (size == 0) return 0.0;
    double mse = 0.0;
    for (size_t i = 0; i < size; ++i) {
        double diff = static_cast<double>(orig[i]) - static_cast<double>(recon[i]);
        mse += diff * diff;
    }
    mse /= static_cast<double>(size);
    if (mse <= 1e-10) {
        return 100.0; // Identical
    }
    return 10.0 * log10((255.0 * 255.0) / mse);
}

} // namespace komikku
