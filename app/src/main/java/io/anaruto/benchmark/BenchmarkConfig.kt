package io.anaruto.benchmark

import kotlinx.serialization.Serializable

@Serializable
enum class Codec(val id: Int, val label: String) {
    WEBP(NativeBenchmark.CODEC_WEBP, "WebP"),
    JXL(NativeBenchmark.CODEC_JXL, "JPEG XL"),
    AVIF(NativeBenchmark.CODEC_AVIF, "AVIF")
}

@Serializable
enum class OutputTarget {
    NATIVE_BUFFER,
    ANDROID_BITMAP
}

@Serializable
enum class PixelLayout(val id: Int) {
    RGBA_8888(NativeBenchmark.FORMAT_RGBA_8888),
    RGB_565(NativeBenchmark.FORMAT_RGB_565)
}

@Serializable
enum class BenchmarkMode {
    SINGLE_LATENCY,
    SEQUENTIAL_READING,
    PARALLEL_DECODING,
    MEMORY_CONSTRAINED,
    COLD_VS_WARM
}

@Serializable
data class DecoderBenchmarkConfig(
    val codec: Codec = Codec.WEBP,
    val outputTarget: OutputTarget = OutputTarget.NATIVE_BUFFER,
    val pixelLayout: PixelLayout = PixelLayout.RGBA_8888,
    val threadCount: Int = 1,
    val warmupIterations: Int = 30,
    val testIterations: Int = 100,
    val mode: BenchmarkMode = BenchmarkMode.SINGLE_LATENCY
)

@Serializable
data class EncoderBenchmarkConfig(
    val codec: Codec = Codec.WEBP,
    val lossless: Boolean = true,
    val quality: Int = 90,
    val effort: Int = 4,
    val threadCount: Int = 1,
    val warmupIterations: Int = 5,
    val testIterations: Int = 20
)
