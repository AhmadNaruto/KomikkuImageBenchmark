# Komikku Image Codec Benchmark

A production-quality Android native image codec benchmarking suite designed to evaluate **WebP**, **JPEG XL (libjxl)**, and **AVIF (libavif + dav1d)** for high-resolution manga and comic rendering on ARM64 Android devices (POCO M4 Pro 4G, MediaTek Helio G96).

## Key Features
- **Unified C++17 Codec API**: Evaluates WebP, JXL, and AVIF decoders and encoders under identical benchmark boundaries.
- **Dual Pipeline Benchmarking**:
  - **Path A**: Native DirectByteBuffer (zero copy to native engine).
  - **Path B**: Android Bitmap (`AndroidBitmap_lockPixels` direct surface output).
- **Comprehensive Metrics**:
  - Sub-millisecond timing breakdowns (initialization, header parsing, decode latency, color conversion).
  - RSS, peak RSS (`/proc/self/status` VmHWM), and native heap memory monitoring.
  - Multi-threaded worker ($1, 2, 3$) vs codec internal thread ($1, 2, 4$) scalability.
- **Lossless Bit-Exact Validation**: Byte-by-byte exact verification tool.
- **Exporting**: Complete raw samples and aggregates exported to CSV and JSON.

## Repository Structure
```
KomikkuImageBenchmark/
├── app/
│   ├── src/main/java/io/anaruto/benchmark/  # Kotlin UI, Runners, Exporters
│   └── src/main/cpp/                        # C++17 unified codec and JNI bridge
├── docs/                                    # Architecture, Build, Protocol, Results
├── dataset/                                 # Masters, encoded variants, export results
├── third_party/                             # Pinned native dependencies
└── tools/                                   # Build and dataset matrix scripts
```

## Building
Per development rules on Termux, Android builds and compilations are managed through GitHub Actions CI (`.github/workflows/ci.yml`).
