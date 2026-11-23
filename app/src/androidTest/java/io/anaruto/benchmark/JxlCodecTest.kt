package io.anaruto.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class JxlCodecTest {

    @Test
    fun testJxlVersion() {
        val version = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_JXL)
        assertNotNull(version)
        assertTrue("JXL version should start with 0.11 or similar", version.isNotEmpty())
    }

    @Test
    fun testLosslessRoundTripAndAlpha() {
        val width = 64
        val height = 64
        val capacity = width * height * 4
        val directBuf = ByteBuffer.allocateDirect(capacity)

        // Known checkerboard / gradient with alpha
        for (y in 0 until height) {
            for (x in 0 until width) {
                directBuf.put(((x * 255) / width).toByte())   // R
                directBuf.put(((y * 255) / height).toByte())  // G
                directBuf.put(200.toByte())                    // B
                directBuf.put(((x * y) % 256).toByte())        // A
            }
        }
        directBuf.flip()

        // 1. Encode with Modular Lossless JXL
        val encodedBytes = NativeBenchmark.nativeEncodeFromBuffer(
            codecId = NativeBenchmark.CODEC_JXL,
            directBuffer = directBuf,
            width = width,
            height = height,
            lossless = true,
            quality = 100,
            effort = 4,
            threadCount = 2,
            outTiming = null
        )
        assertNotNull("Encoded JXL bytes should not be null", encodedBytes)
        assertTrue("Encoded JXL should have non-zero size", encodedBytes!!.size > 64)

        // 2. Query basic info
        val info = IntArray(4)
        val infoOk = NativeBenchmark.nativeGetInfo(NativeBenchmark.CODEC_JXL, encodedBytes, info)
        assertTrue("nativeGetInfo should succeed for JXL", infoOk)
        assertEquals("Width must match", width, info[0])
        assertEquals("Height must match", height, info[1])
        assertEquals("Alpha flag must be set", 1, info[2])

        // 3. Decode back to DirectByteBuffer
        val decodedBuf = ByteBuffer.allocateDirect(capacity)
        val timing = DoubleArray(5)
        val decodeOk = NativeBenchmark.nativeDecodeToBuffer(
            codecId = NativeBenchmark.CODEC_JXL,
            inData = encodedBytes,
            outDirectBuffer = decodedBuf,
            threadCount = 2,
            pixelFormat = NativeBenchmark.FORMAT_RGBA_8888,
            outTiming = timing
        )
        assertTrue("JXL decode should succeed", decodeOk)
        assertTrue("Total timing should be >= 0", timing[4] >= 0.0)

        // 4. Validate exact lossless match
        directBuf.rewind()
        decodedBuf.rewind()
        var diffCount = 0
        for (i in 0 until capacity) {
            val original = directBuf.get()
            val decoded = decodedBuf.get()
            if (original != decoded) {
                diffCount++
            }
        }
        assertEquals("JXL lossless roundtrip must have 0 byte differences", 0, diffCount)
    }

    @Test
    fun testBitmapDecodeWithThreadRunner() {
        val width = 128
        val height = 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, Color.rgb(x * 2, y, 150))
            }
        }

        val encoded = NativeBenchmark.nativeEncodeFromBitmap(
            codecId = NativeBenchmark.CODEC_JXL,
            bitmap = bitmap,
            lossless = false,
            quality = 90,
            effort = 4,
            threadCount = 4,
            outTiming = null
        )
        assertNotNull(encoded)

        val dstBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val timing = DoubleArray(5)
        val ok = NativeBenchmark.nativeDecodeToBitmap(
            codecId = NativeBenchmark.CODEC_JXL,
            inData = encoded!!,
            bitmap = dstBitmap,
            threadCount = 4,
            outTiming = timing
        )
        assertTrue("JXL Bitmap decode must succeed", ok)
        assertTrue("Init time measured", timing[0] >= 0.0)
        assertTrue("Decode time measured", timing[2] >= 0.0)

        bitmap.recycle()
        dstBitmap.recycle()
    }
}
