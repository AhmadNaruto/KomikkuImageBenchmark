package io.anaruto.benchmark

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NativeBenchmark {

    init {
        System.loadLibrary("komikku_benchmark")
    }

    const val CODEC_WEBP = 0
    const val CODEC_JXL = 1
    const val CODEC_AVIF = 2

    const val FORMAT_RGBA_8888 = 0
    const val FORMAT_RGB_565 = 1

    external fun nativeGetLastError(): String
    external fun nativeGetVersion(codecId: Int): String

    /**
     * @param outInfo Array of size 4: [width, height, hasAlpha (1/0), bitDepth]
     */
    external fun nativeGetInfo(codecId: Int, data: ByteArray, outInfo: IntArray): Boolean

    /**
     * @param outTiming Array of size 5: [initMs, headerMs, decodeMs, colorConvertMs, totalMs]
     */
    external fun nativeDecodeToBuffer(
        codecId: Int,
        inData: ByteArray,
        outDirectBuffer: ByteBuffer,
        threadCount: Int,
        pixelFormat: Int,
        outTiming: DoubleArray?
    ): Boolean

    external fun nativeDecodeToBitmap(
        codecId: Int,
        inData: ByteArray,
        bitmap: Bitmap,
        threadCount: Int,
        outTiming: DoubleArray?
    ): Boolean

    external fun nativeEncodeFromBuffer(
        codecId: Int,
        directBuffer: ByteBuffer,
        width: Int,
        height: Int,
        lossless: Boolean,
        quality: Int,
        effort: Int,
        threadCount: Int,
        outTiming: DoubleArray?
    ): ByteArray?

    external fun nativeEncodeFromBitmap(
        codecId: Int,
        bitmap: Bitmap,
        lossless: Boolean,
        quality: Int,
        effort: Int,
        threadCount: Int,
        outTiming: DoubleArray?
    ): ByteArray?

    external fun nativeBenchmarkDecode(
        codecId: Int,
        inData: ByteArray,
        warmupIters: Int,
        testIters: Int,
        threadCount: Int,
        pixelFormat: Int
    ): DoubleArray?

    external fun nativeBenchmarkEncode(
        codecId: Int,
        directBuffer: ByteBuffer,
        width: Int,
        height: Int,
        lossless: Boolean,
        quality: Int,
        effort: Int,
        warmupIters: Int,
        testIters: Int,
        threadCount: Int
    ): DoubleArray?

    /**
     * @return [rssKb, peakRssKb, nativeHeapKb]
     */
    external fun nativeGetMemoryMetrics(): LongArray?
}
