package io.anaruto.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AvifCodecTest {

    @Test
    fun testAvifVersionAndDav1dBackend() {
        val version = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_AVIF)
        assertNotNull(version)
        assertTrue("AVIF version should report dav1d backend", version.contains("dav1d"))
    }

    @Test
    fun testLosslessRoundTripAndAlpha() {
        val width = 64
        val height = 64
        val capacity = width * height * 4
        val directBuf = ByteBuffer.allocateDirect(capacity)

        // Generate known gradient with alpha
        for (y in 0 until height) {
            for (x in 0 until width) {
                directBuf.put(((x * 255) / width).toByte())   // R
                directBuf.put(((y * 255) / height).toByte())  // G
                directBuf.put(150.toByte())                    // B
                directBuf.put(((x + y * 2) % 256).toByte())    // A
            }
        }
        directBuf.flip()

        // 1. Encode with AVIF Lossless
        val encodedBytes = NativeBenchmark.nativeEncodeFromBuffer(
            codecId = NativeBenchmark.CODEC_AVIF,
            directBuffer = directBuf,
            width = width,
            height = height,
            lossless = true,
            quality = 100,
            effort = 4, // speed 4
            threadCount = 2,
            outTiming = null
        )
        assertNotNull("Encoded AVIF bytes should not be null", encodedBytes)
        assertTrue("Encoded AVIF should have non-zero size", encodedBytes!!.size > 64)

        // 2. Query basic info
        val info = IntArray(4)
        val infoOk = NativeBenchmark.nativeGetInfo(NativeBenchmark.CODEC_AVIF, encodedBytes, info)
        assertTrue("nativeGetInfo should succeed for AVIF", infoOk)
        assertEquals("Width must match", width, info[0])
        assertEquals("Height must match", height, info[1])
        assertEquals("Alpha flag must be set", 1, info[2])

        // 3. Decode back to DirectByteBuffer
        val decodedBuf = ByteBuffer.allocateDirect(capacity)
        val timing = DoubleArray(5)
        val decodeOk = NativeBenchmark.nativeDecodeToBuffer(
            codecId = NativeBenchmark.CODEC_AVIF,
            inData = encodedBytes,
            outDirectBuffer = decodedBuf,
            threadCount = 2,
            pixelFormat = NativeBenchmark.FORMAT_RGBA_8888,
            outTiming = timing
        )
        assertTrue("AVIF decode should succeed", decodeOk)
        assertTrue("Decode time must be positive", timing[4] >= 0.0)

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
        assertEquals("AVIF lossless roundtrip must have 0 byte differences", 0, diffCount)
    }

    @Test
    fun testBitmapDecode() {
        val width = 128
        val height = 128
        val srcBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                srcBitmap.setPixel(x, y, Color.rgb(x * 2, y, 180))
            }
        }

        val encoded = NativeBenchmark.nativeEncodeFromBitmap(
            codecId = NativeBenchmark.CODEC_AVIF,
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
            codecId = NativeBenchmark.CODEC_AVIF,
            inData = encoded!!,
            bitmap = dstBitmap,
            threadCount = 2,
            outTiming = timing
        )
        assertTrue("AVIF Bitmap decode must succeed", ok)
        assertTrue("Color convert time measured", timing[3] >= 0.0)

        srcBitmap.recycle()
        dstBitmap.recycle()
    }
}
