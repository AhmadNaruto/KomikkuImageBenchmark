# Komikku Image Codec Benchmark: Architecture Specification

## 1. Overview
The benchmark evaluates WebP, JPEG XL (libjxl), and AVIF (libavif + dav1d) for high-resolution manga and comic reading on Android ARM64 devices (Target: POCO M4 Pro 4G, MediaTek Helio G96).

## 2. Multi-tier Architectural Separation

```
+-------------------------------------------------------+
|                     Kotlin Layer                      |
|  - MainActivity & Compose UI                          |
|  - BenchmarkRunner (Coroutines dispatcher)            |
|  - DatasetManager (Masters, Encoded, SHA-256)         |
|  - CsvExporter & JsonExporter                         |
+-------------------------------------------------------+
                           |
                           v (JNI Call Boundary)
+-------------------------------------------------------+
|                      JNI Layer                        |
|  - native_bridge.cpp                                  |
|  - DirectByteBuffer vs AndroidBitmap_lockPixels       |
|  - Sub-millisecond timing boundaries                  |
|  - Error propagation & exception safety               |
+-------------------------------------------------------+
                           |
                           v (C++17 Native Layer)
+-------------------------------------------------------+
|                  Unified Codec API                    |
|  - IDecoder / IEncoder interfaces                     |
|  - ImageBuffer & metrics (procfs VmHWM, VmRSS)        |
+-------------------------------------------------------+
       |                   |                   |
       v                   v                   v
+--------------+    +--------------+    +--------------+
|   libwebp    |    |   libjxl     |    |   libavif    |
| (WebP v1.5.0)|    | (JXL v0.11.1)|    | (dav1d 1.5.1)|
+--------------+    +--------------+    +--------------+
```

## 3. Two Output Paths Under Test
- **Path A (DirectByteBuffer)**:
  `Storage/Memory -> Native Decoder -> DirectByteBuffer (zero copy to native engine)`
- **Path B (Android Bitmap)**:
  `Storage/Memory -> Native Decoder -> AndroidBitmap_lockPixels (direct surface write)`

## 4. Concurrency Model
- Decoupled application worker count ($1, 2, 3$) and codec internal thread count ($1, 2, 4$).
- Throttled concurrency prevents thread pool explosion and memory exhaustion on the Helio G96 (8 cores: 2x A76 + 6x A55).
