package io.anaruto.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

class BenchmarkRunner(private val datasetManager: DatasetManager) {

    sealed class BenchmarkProgress {
        data class Running(val currentIteration: Int, val totalIterations: Int, val message: String) : BenchmarkProgress()
        data class Completed(val result: BenchmarkResult) : BenchmarkProgress()
        data class Error(val message: String) : BenchmarkProgress()
    }

    private suspend fun sendProgress(
        onProgress: (BenchmarkProgress) -> Unit,
        progress: BenchmarkProgress
    ) {
        withContext(Dispatchers.Main) {
            onProgress(progress)
        }
    }

    suspend fun runDecoderBenchmark(
        config: DecoderBenchmarkConfig,
        imageFile: File,
        onProgress: (BenchmarkProgress) -> Unit
    ): BenchmarkResult? = withContext(Dispatchers.Default) {
        try {
            if (!imageFile.exists()) {
                sendProgress(onProgress, BenchmarkProgress.Error("File not found: ${imageFile.name}"))
                return@withContext null
            }

            // Ensure we are decoding the proper codec file. If a PNG master is passed, encode it first.
            val targetFile = if (imageFile.extension.equals("png", ignoreCase = true)) {
                sendProgress(onProgress, BenchmarkProgress.Running(0, config.warmupIterations + config.testIterations, "Preparing ${config.codec.label} bitstream..."))
                datasetManager.getOrEncodeSample(imageFile, config.codec)
            } else {
                imageFile
            }

            val inBytes = targetFile.readBytes()
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
                        sendProgress(onProgress, BenchmarkProgress.Running(i + 1, config.warmupIterations + config.testIterations, "Warming up (${i + 1}/${config.warmupIterations})..."))
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
                        sendProgress(onProgress, BenchmarkProgress.Running(config.warmupIterations + i + 1, config.warmupIterations + config.testIterations, "Measuring iteration ${i + 1}/${config.testIterations}"))
                    }

                    if (samples.isEmpty()) {
                        val err = NativeBenchmark.nativeGetLastError() ?: "Unknown decoding error"
                        sendProgress(onProgress, BenchmarkProgress.Error("Decoder failed for ${config.codec.label}: $err"))
                        return@withContext null
                    }

                    val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
                    val result = BenchmarkResult.computeStats(
                        codec = config.codec,
                        codecVersion = version,
                        mode = config.mode,
                        outputTarget = config.outputTarget,
                        imageName = targetFile.name,
                        width = width,
                        height = height,
                        sourceBytes = targetFile.length(),
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

                    sendProgress(onProgress, BenchmarkProgress.Completed(result))
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
                        sendProgress(onProgress, BenchmarkProgress.Running(i + 1, config.warmupIterations + config.testIterations, "Warming up (${i + 1}/${config.warmupIterations})..."))
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
                        sendProgress(onProgress, BenchmarkProgress.Running(config.warmupIterations + i + 1, config.warmupIterations + config.testIterations, "Measuring iteration ${i + 1}/${config.testIterations}"))
                    }

                    bitmap.recycle()

                    if (samples.isEmpty()) {
                        val err = NativeBenchmark.nativeGetLastError() ?: "Unknown decoding error"
                        sendProgress(onProgress, BenchmarkProgress.Error("Decoder failed for ${config.codec.label}: $err"))
                        return@withContext null
                    }

                    val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
                    val result = BenchmarkResult.computeStats(
                        codec = config.codec,
                        codecVersion = version,
                        mode = config.mode,
                        outputTarget = config.outputTarget,
                        imageName = targetFile.name,
                        width = width,
                        height = height,
                        sourceBytes = targetFile.length(),
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

                    sendProgress(onProgress, BenchmarkProgress.Completed(result))
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            sendProgress(onProgress, BenchmarkProgress.Error("Exception in benchmark: ${e.message}"))
            return@withContext null
        }
    }

    suspend fun runEncoderBenchmark(
        config: EncoderBenchmarkConfig,
        masterFile: File,
        onProgress: (BenchmarkProgress) -> Unit
    ): BenchmarkResult? = withContext(Dispatchers.Default) {
        try {
            if (!masterFile.exists()) {
                sendProgress(onProgress, BenchmarkProgress.Error("Master file not found: ${masterFile.name}"))
                return@withContext null
            }

            val bitmap = BitmapFactory.decodeFile(masterFile.absolutePath)
            if (bitmap == null) {
                sendProgress(onProgress, BenchmarkProgress.Error("Failed to decode master image: ${masterFile.name}"))
                return@withContext null
            }

            val width = bitmap.width
            val height = bitmap.height
            val version = NativeBenchmark.nativeGetVersion(config.codec.id)
            val timingDetail = DoubleArray(5)

            // Warmup
            for (i in 0 until config.warmupIterations) {
                if (!coroutineContext.isActive) {
                    bitmap.recycle()
                    return@withContext null
                }
                NativeBenchmark.nativeEncodeFromBitmap(
                    config.codec.id, bitmap, config.lossless, config.quality, config.effort, config.threadCount, null
                )
                sendProgress(onProgress, BenchmarkProgress.Running(i + 1, config.warmupIterations + config.testIterations, "Warming up encoder (${i + 1}/${config.warmupIterations})..."))
            }

            // Measured
            val samples = mutableListOf<Double>()
            var lastEncodedSize = 0L
            for (i in 0 until config.testIterations) {
                if (!coroutineContext.isActive) {
                    bitmap.recycle()
                    return@withContext null
                }
                val encodedBytes = NativeBenchmark.nativeEncodeFromBitmap(
                    config.codec.id, bitmap, config.lossless, config.quality, config.effort, config.threadCount, timingDetail
                )
                if (encodedBytes != null && encodedBytes.isNotEmpty()) {
                    samples.add(timingDetail[4])
                    lastEncodedSize = encodedBytes.size.toLong()
                }
                sendProgress(onProgress, BenchmarkProgress.Running(
                    config.warmupIterations + i + 1,
                    config.warmupIterations + config.testIterations,
                    "Measuring encode iteration ${i + 1}/${config.testIterations}"
                ))
            }

            bitmap.recycle()
            val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
            val result = BenchmarkResult.computeStats(
                codec = config.codec,
                codecVersion = version,
                mode = BenchmarkMode.SINGLE_LATENCY,
                outputTarget = OutputTarget.NATIVE_BUFFER,
                imageName = masterFile.name,
                width = width,
                height = height,
                sourceBytes = masterFile.length(),
                encodedBytes = lastEncodedSize,
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

            sendProgress(onProgress, BenchmarkProgress.Completed(result))
            return@withContext result
        } catch (e: Exception) {
            sendProgress(onProgress, BenchmarkProgress.Error("Exception in encoder benchmark: ${e.message}"))
            return@withContext null
        }
    }

    /**
     * Sequential manga reading simulation: decodes consecutive pages sequentially
     */
    suspend fun runSequentialReadingBenchmark(
        codec: Codec,
        pageFiles: List<File>,
        target: OutputTarget,
        threadCount: Int,
        onProgress: (BenchmarkProgress) -> Unit
    ): BenchmarkResult? = withContext(Dispatchers.Default) {
        val samples = mutableListOf<Double>()
        var lastTiming = BenchmarkTimingDetail()
        val totalPages = pageFiles.size
        var width = 1600
        var height = 2400

        for ((idx, file) in pageFiles.withIndex()) {
            if (!coroutineContext.isActive) return@withContext null
            val targetFile = if (file.extension.equals("png", ignoreCase = true)) {
                datasetManager.getOrEncodeSample(file, codec)
            } else {
                file
            }
            val bytes = targetFile.readBytes()
            val timingDetail = DoubleArray(5)

            if (target == OutputTarget.NATIVE_BUFFER) {
                val buf = ByteBuffer.allocateDirect(width * height * 4)
                if (NativeBenchmark.nativeDecodeToBuffer(codec.id, bytes, buf, threadCount, 0, timingDetail)) {
                    samples.add(timingDetail[4])
                }
            } else {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                if (NativeBenchmark.nativeDecodeToBitmap(codec.id, bytes, bmp, threadCount, timingDetail)) {
                    samples.add(timingDetail[4])
                }
                bmp.recycle()
            }
            lastTiming = BenchmarkTimingDetail(
                initMs = timingDetail[0],
                headerMs = timingDetail[1],
                decodeMs = timingDetail[2],
                colorConvertMs = timingDetail[3],
                totalMs = timingDetail[4]
            )
            sendProgress(onProgress, BenchmarkProgress.Running(idx + 1, totalPages, "Sequential page ${idx + 1}/$totalPages"))
        }

        val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
        val result = BenchmarkResult.computeStats(
            codec = codec,
            codecVersion = NativeBenchmark.nativeGetVersion(codec.id),
            mode = BenchmarkMode.SEQUENTIAL_READING,
            outputTarget = target,
            imageName = "Sequential_${pageFiles.size}_Pages",
            width = width,
            height = height,
            sourceBytes = pageFiles.sumOf { it.length() },
            encodedBytes = pageFiles.sumOf { it.length() },
            threadCount = threadCount,
            rawSamples = samples,
            timing = lastTiming,
            peakRssKb = mem[1],
            heapKb = mem[2]
        )
        sendProgress(onProgress, BenchmarkProgress.Completed(result))
        return@withContext result
    }

    /**
     * Parallel reading benchmark: workerCount concurrent workers decoding simultaneously
     */
    suspend fun runParallelBenchmark(
        codec: Codec,
        imageFile: File,
        workerCount: Int,
        codecThreadCount: Int,
        totalTasks: Int,
        onProgress: (BenchmarkProgress) -> Unit
    ): BenchmarkResult? = withContext(Dispatchers.Default) {
        val targetFile = if (imageFile.extension.equals("png", ignoreCase = true)) {
            datasetManager.getOrEncodeSample(imageFile, codec)
        } else {
            imageFile
        }
        val inBytes = targetFile.readBytes()
        val samples = java.util.Collections.synchronizedList(mutableListOf<Double>())
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        val dispatcher = Dispatchers.Default.limitedParallelism(workerCount)

        coroutineScope {
            val jobs = (1..totalTasks).map {
                launch(dispatcher) {
                    val directBuf = ByteBuffer.allocateDirect(1600 * 2400 * 4)
                    val timing = DoubleArray(5)
                    if (NativeBenchmark.nativeDecodeToBuffer(codec.id, inBytes, directBuf, codecThreadCount, 0, timing)) {
                        samples.add(timing[4])
                    }
                    val c = completedCount.incrementAndGet()
                    sendProgress(onProgress, BenchmarkProgress.Running(c, totalTasks, "Parallel worker task $c/$totalTasks (workers: $workerCount, threads: $codecThreadCount)"))
                }
            }
            jobs.joinAll()
        }

        val mem = NativeBenchmark.nativeGetMemoryMetrics() ?: longArrayOf(0, 0, 0)
        val result = BenchmarkResult.computeStats(
            codec = codec,
            codecVersion = NativeBenchmark.nativeGetVersion(codec.id),
            mode = BenchmarkMode.PARALLEL_DECODING,
            outputTarget = OutputTarget.NATIVE_BUFFER,
            imageName = targetFile.name,
            width = 1600,
            height = 2400,
            sourceBytes = targetFile.length(),
            encodedBytes = inBytes.size.toLong(),
            threadCount = codecThreadCount,
            rawSamples = samples.toList(),
            timing = BenchmarkTimingDetail(totalMs = if (samples.isNotEmpty()) samples.average() else 0.0),
            peakRssKb = mem[1],
            heapKb = mem[2]
        )
        sendProgress(onProgress, BenchmarkProgress.Completed(result))
        return@withContext result
    }
}
