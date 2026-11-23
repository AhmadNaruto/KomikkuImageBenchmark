package io.anaruto.benchmark

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

class BenchmarkRunner(private val datasetManager: DatasetManager) {

    sealed class BenchmarkProgress {
        data class Running(val currentIteration: Int, val totalIterations: Int, val message: String) : BenchmarkProgress()
        data class Completed(val result: BenchmarkResult) : BenchmarkProgress()
        data class Error(val message: String) : BenchmarkProgress()
    }

    suspend fun runDecoderBenchmark(
        config: DecoderBenchmarkConfig,
        imageFile: File,
        onProgress: (BenchmarkProgress) -> Unit
    ): BenchmarkResult? = withContext(Dispatchers.Default) {
        try {
            if (!imageFile.exists()) {
                onProgress(BenchmarkProgress.Error("File not found: ${imageFile.name}"))
                return@withContext null
            }

            val inBytes = imageFile.readBytes()
            val info = IntArray(4)
            val infoOk = NativeBenchmark.nativeGetInfo(config.codec.id, inBytes, info)

            val width = if (infoOk && info[0] > 0) info[0] else 1600
            val height = if (infoOk && info[1] > 0) info[1] else 2400

            val version = NativeBenchmark.nativeGetVersion(config.codec.id)
            val timingDetail = DoubleArray(5)

            when (config.outputTarget) {
                OutputTarget.NATIVE_BUFFER -> {
                    val capacity = width * height * (if (config.pixelLayout == PixelLayout.RGBA_8888) 4 else 2)
                    val directBuf = ByteBuffer.allocateDirect(capacity)

                    // Warmup
                    for (i in 0 until config.warmupIterations) {
                        if (!coroutineContext.isActive) return@withContext null
                        NativeBenchmark.nativeDecodeToBuffer(
                            config.codec.id, inBytes, directBuf, config.threadCount, config.pixelLayout.id, null
                        )
                        onProgress(BenchmarkProgress.Running(i + 1, config.warmupIterations + config.testIterations, "Warming up..."))
                    }

                    // Measured
                    val samples = mutableListOf<Double>()
                    for (i in 0 until config.testIterations) {
                        if (!coroutineContext.isActive) return@withContext null
                        directBuf.clear()
                        val ok = NativeBenchmark.nativeDecodeToBuffer(
                            config.codec.id, inBytes, directBuf, config.threadCount, config.pixelLayout.id, timingDetail
                        )
                        if (ok) {
                            samples.add(timingDetail[4]) // totalMs
                        }
                        onProgress(BenchmarkProgress.Running(config.warmupIterations + i + 1, config.warmupIterations + config.testIterations, "Measuring iteration ${i + 1}/${config.testIterations}"))
                    }

                    val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
                    val result = BenchmarkResult.computeStats(
                        codec = config.codec,
                        codecVersion = version,
                        mode = config.mode,
                        outputTarget = config.outputTarget,
                        imageName = imageFile.name,
                        width = width,
                        height = height,
                        sourceBytes = imageFile.length(),
                        encodedBytes = inBytes.size.toLong(),
                        threadCount = config.threadCount,
                        rawSamples = samples,
                        timing = BenchmarkTimingDetail(
                            initMs = timingDetail[0],
                            headerMs = timingDetail[1],
                            decodeMs = timingDetail[2],
                            colorConvertMs = timingDetail[3],
                            totalMs = timingDetail[4]
                        ),
                        peakRssKb = mem[1],
                        heapKb = mem[2]
                    )

                    onProgress(BenchmarkProgress.Completed(result))
                    return@withContext result
                }

                OutputTarget.ANDROID_BITMAP -> {
                    val configBitmap = if (config.pixelLayout == PixelLayout.RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                    val bitmap = Bitmap.createBitmap(width, height, configBitmap)

                    // Warmup
                    for (i in 0 until config.warmupIterations) {
                        if (!coroutineContext.isActive) {
                            bitmap.recycle()
                            return@withContext null
                        }
                        NativeBenchmark.nativeDecodeToBitmap(
                            config.codec.id, inBytes, bitmap, config.threadCount, null
                        )
                        onProgress(BenchmarkProgress.Running(i + 1, config.warmupIterations + config.testIterations, "Warming up..."))
                    }

                    // Measured
                    val samples = mutableListOf<Double>()
                    for (i in 0 until config.testIterations) {
                        if (!coroutineContext.isActive) {
                            bitmap.recycle()
                            return@withContext null
                        }
                        val ok = NativeBenchmark.nativeDecodeToBitmap(
                            config.codec.id, inBytes, bitmap, config.threadCount, timingDetail
                        )
                        if (ok) {
                            samples.add(timingDetail[4])
                        }
                        onProgress(BenchmarkProgress.Running(config.warmupIterations + i + 1, config.warmupIterations + config.testIterations, "Measuring iteration ${i + 1}/${config.testIterations}"))
                    }

                    bitmap.recycle()
                    val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
                    val result = BenchmarkResult.computeStats(
                        codec = config.codec,
                        codecVersion = version,
                        mode = config.mode,
                        outputTarget = config.outputTarget,
                        imageName = imageFile.name,
                        width = width,
                        height = height,
                        sourceBytes = imageFile.length(),
                        encodedBytes = inBytes.size.toLong(),
                        threadCount = config.threadCount,
                        rawSamples = samples,
                        timing = BenchmarkTimingDetail(
                            initMs = timingDetail[0],
                            headerMs = timingDetail[1],
                            decodeMs = timingDetail[2],
                            colorConvertMs = timingDetail[3],
                            totalMs = timingDetail[4]
                        ),
                        peakRssKb = mem[1],
                        heapKb = mem[2]
                    )

                    onProgress(BenchmarkProgress.Completed(result))
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            onProgress(BenchmarkProgress.Error("Exception in benchmark: ${e.message}"))
            return@withContext null
        }
    }
}
