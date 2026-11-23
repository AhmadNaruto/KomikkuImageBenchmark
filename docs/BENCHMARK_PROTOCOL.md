# Benchmark Protocol and Execution Methodology

## 1. Test Matrix
Every image codec (WebP, JPEG XL, AVIF) is benchmarked across 5 dedicated tests:

1. **Test A: Single-Image Latency**
   - 30 warm-up runs, 100 measured iterations.
   - Measures init, header parsing, decode, color conversion, total latency.
2. **Test B: Sequential Manga Reading**
   - Simulates 30 and 100 consecutive pages.
   - Measures median, P95, P99 latency, throughput (img/s, MP/s).
3. **Test C: Parallel Decoding**
   - App workers ($1, 2, 3$) $\times$ Codec threads ($1, 2, 4$).
   - Measures peak RSS, scaling efficiency, and latency degradation.
4. **Test D: Memory-Constrained Decoding**
   - 1600x2400, 2500x3600, 4000x6000 pages.
   - Tracks RSS, peak RSS (VmHWM), and native heap allocations.
5. **Test E: Cold vs Warm Decoding**
   - Cold process decode vs repeated in-memory cache decode.

## 2. Balanced Scoring Model (0–100)
- **Decode Performance**: 30%
- **Storage Efficiency (Compression Ratio)**: 25%
- **Memory Efficiency (Peak RSS)**: 20%
- **Visual Quality (SSIMULACRA2 / PSNR)**: 15%
- **Energy Efficiency**: 10%
