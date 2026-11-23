# Pinned Native Dependencies Manifest

| Library | Version | Pinned Commit | License | Backend / Submodules | Optimization |
|---------|---------|---------------|---------|----------------------|--------------|
| **libwebp** | `v1.5.0` | `a4d7a715337ded4451fec90ff8ce79728e04126c` | BSD-3-Clause | libsharpyuv, libwebpmux | NEON SIMD, -O2 |
| **libjxl** | `v0.11.1` | `794a5dcf0d54f9f0b20d288a12e87afb91d20dfc` | BSD-3-Clause | Highway SIMD (`hwy`), Brotli, Skcms | C++17, -O2, LTO |
| **dav1d** | `1.5.1` | `42b2b24fb8819f1ed3643aa9cf2a62f03868e3aa` | BSD-2-Clause | ARM64 Assembly (`src/arm/64/*.S`) | NEON ASM, -O2 |
| **libavif** | `v1.2.0` | `a28899ac7df6b211dae7199cb723d8ce2cecff1b` | BSD-2-Clause | Decoder: `dav1d` (mandatory), Encoder: `libaom` | -O2, LTO |
| **libaom** | `v3.9.1` | `5d2fb504124e2a8fa283092c13d063fd995028ec` | BSD-2-Clause | AV1 Encoder backend for libavif | NEON SIMD, -O2 |
