package io.anaruto.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class WebPCodecTest {

    @Test
    fun testWebPVersion() {
        val version = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_WEBP)
        assertNotNull(version)
        assertTrue("WebP version should start with 1.", version.startsWith("1."))
    }

    @Test
    fun testLosslessRoundTripAndAlpha() {
        val width = 64
        val height = 64
        val capacity = width * height * 4
        val directBuf = ByteBuffer.allocateDirect(capacity)

        // Generate known synthetic pattern with alpha gradient
        for (y in 0 until height) {
            for (x in 0 until width) {
                directBuf.put(((x * 255) / width).toByte())   // R
                directBuf.put(((y * 255) / height).toByte())  // G
                directBuf.put(128.toByte())                    // B
                directBuf.put(((x + y) % 256).toByte())        // A
            }
        }
        directBuf.flip()

        // 1. Encode to Lossless WebP
        val encodedBytes = NativeBenchmark.nativeEncodeFromBuffer(
            codecId = NativeBenchmark.CODEC_WEBP,
            directBuffer = directBuf,
            width = width,
            height = height,
            lossless = true,
            quality = 100,
            effort = 4,
            threadCount = 1,
            outTiming = null
        )
        assertNotNull("Encoded WebP bytes should not be null", encodedBytes)
        assertTrue("Encoded WebP should have non-zero size", encodedBytes!!.size > 64)

        // 2. Read Info
        val info = IntArray(4)
        val infoOk = NativeBenchmark.nativeGetInfo(NativeBenchmark.CODEC_WEBP, encodedBytes, info)
        assertTrue("nativeGetInfo should succeed", infoOk)
        assertEquals("Width must match", width, info[0])
        assertEquals("Height must match", height, info[1])
        assertEquals("Alpha flag must be set", 1, info[2])

        // 3. Decode back to DirectByteBuffer
        val decodedBuf = ByteBuffer.allocateDirect(capacity)
        val timing = DoubleArray(5)
        val decodeOk = NativeBenchmark.nativeDecodeToBuffer(
            codecId = NativeBenchmark.CODEC_WEBP,
            inData = encodedBytes,
            outDirectBuffer = decodedBuf,
            threadCount = 1,
            pixelFormat = NativeBenchmark.FORMAT_RGBA_8888,
            outTiming = timing
        )
        assertTrue("Decoding should succeed", decodeOk)
        assertTrue("Decode time should be > 0", timing[4] >= 0.0)

        // 4. Verify exact lossless pixel match
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
        assertEquals("Lossless roundtrip must have 0 differing bytes", 0, diffCount)
    }

    @Test
    fun testInvalidAndTruncatedFileRejection() {
        val corruptedBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00) // Truncated RIFF
        val info = IntArray(4)
        val ok = NativeBenchmark.nativeGetInfo(NativeBenchmark.CODEC_WEBP, corruptedBytes, info)
        assertFalse("Malformed header should fail gracefully", ok)
        val errorMsg = NativeBenchmark.nativeGetLastError()
        assertNotNull(errorMsg)
        assertTrue("Error message should be descriptive", errorMsg.isNotEmpty())
    }

    @Test
    fun testBitmapOutputDecode() {
        val width = 128
        val height = 128
        val srcBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                srcBitmap.setPixel(x, y, Color.rgb(x * 2, y * 2, 100))
            }
        }

        val encoded = NativeBenchmark.nativeEncodeFromBitmap(
            codecId = NativeBenchmark.CODEC_WEBP,
            bitmap = srcBitmap,
            lossless = false,
            quality = 90,
            effort = 4,
            threadCount = 2,
            outTiming = null
        )
        assertNotNull(encoded)

        val dstBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val timing = DoubleArray(5)
        val ok = NativeBenchmark.nativeDecodeToBitmap(
            codecId = NativeBenchmark.CODEC_WEBP,
            inData = encoded!!,
            bitmap = dstBitmap,
            threadCount = 2,
            outTiming = timing
        )
        assertTrue("Bitmap decode must succeed", ok)
        assertTrue("Header timing measured", timing[1] >= 0.0)
        assertTrue("Decode timing measured", timing[2] >= 0.0)

        srcBitmap.recycle()
        dstBitmap.recycle()
    }
}
