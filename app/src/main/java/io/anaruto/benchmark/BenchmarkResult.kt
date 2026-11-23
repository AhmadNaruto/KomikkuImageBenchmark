package io.anaruto.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkTimingDetail(
    val initMs: Double = 0.0,
    val headerMs: Double = 0.0,
    val decodeMs: Double = 0.0,
    val colorConvertMs: Double = 0.0,
    val totalMs: Double = 0.0
)

@Serializable
data class DeviceMetadata(
    val deviceModel: String = android.os.Build.MODEL,
    val manufacturer: String = android.os.Build.MANUFACTURER,
    val soc: String = android.os.Build.HARDWARE,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
    val abi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
)

@Serializable
data class BenchmarkResult(
    val codec: Codec,
    val codecVersion: String,
    val mode: BenchmarkMode,
    val outputTarget: OutputTarget,
    val imageName: String,
    val width: Int,
    val height: Int,
    val sourceFileSizeBytes: Long,
    val encodedFileSizeBytes: Long,
    val threadCount: Int,
    val rawSamplesMs: List<Double>,
    val timingDetail: BenchmarkTimingDetail,
    val medianMs: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val meanMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val imagesPerSecond: Double,
    val megaPixelsPerSecond: Double,
    val peakRssKb: Long,
    val nativeHeapKb: Long,
    val balancedScore: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceMetadata: DeviceMetadata = DeviceMetadata()
) {
    companion object {
        fun computeStats(
            codec: Codec,
            codecVersion: String,
            mode: BenchmarkMode,
            outputTarget: OutputTarget,
            imageName: String,
            width: Int,
            height: Int,
            sourceBytes: Long,
            encodedBytes: Long,
            threadCount: Int,
            rawSamples: List<Double>,
            timing: BenchmarkTimingDetail,
            peakRssKb: Long,
            heapKb: Long
        ): BenchmarkResult {
            val valid = rawSamples.filter { it > 0.0 }.sorted()
            if (valid.isEmpty()) {
                return BenchmarkResult(
                    codec = codec,
                    codecVersion = codecVersion,
                    mode = mode,
                    outputTarget = outputTarget,
                    imageName = imageName,
                    width = width,
                    height = height,
                    sourceFileSizeBytes = sourceBytes,
                    encodedFileSizeBytes = encodedBytes,
                    threadCount = threadCount,
                    rawSamplesMs = rawSamples,
                    timingDetail = timing,
                    medianMs = 0.0,
                    p95Ms = 0.0,
                    p99Ms = 0.0,
                    meanMs = 0.0,
                    minMs = 0.0,
                    maxMs = 0.0,
                    imagesPerSecond = 0.0,
                    megaPixelsPerSecond = 0.0,
                    peakRssKb = peakRssKb,
                    nativeHeapKb = heapKb
                )
            }

            val median = percentile(valid, 50.0)
            val p95 = percentile(valid, 95.0)
            val p99 = percentile(valid, 99.0)
            val mean = valid.average()
            val min = valid.first()
            val max = valid.last()

            val ips = if (median > 0.0) 1000.0 / median else 0.0
            val megapixels = (width.toLong() * height) / 1_000_000.0
            val mpps = ips * megapixels

            return BenchmarkResult(
                codec = codec,
                codecVersion = codecVersion,
                mode = mode,
                outputTarget = outputTarget,
                imageName = imageName,
                width = width,
                height = height,
                sourceFileSizeBytes = sourceBytes,
                encodedFileSizeBytes = encodedBytes,
                threadCount = threadCount,
                rawSamplesMs = rawSamples,
                timingDetail = timing,
                medianMs = median,
                p95Ms = p95,
                p99Ms = p99,
                meanMs = mean,
                minMs = min,
                maxMs = max,
                imagesPerSecond = ips,
                megaPixelsPerSecond = mpps,
                peakRssKb = peakRssKb,
                nativeHeapKb = heapKb
            )
        }

        private fun percentile(sorted: List<Double>, percentile: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val index = (percentile / 100.0 * (sorted.size - 1)).toInt()
            return sorted[index.coerceIn(0, sorted.size - 1)]
        }
    }
}
