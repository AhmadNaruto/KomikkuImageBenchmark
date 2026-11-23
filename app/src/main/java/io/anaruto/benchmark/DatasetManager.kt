package io.anaruto.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@Serializable
data class ImageMetadata(
    val fileName: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val fileSizeBytes: Long,
    val sha256: String,
    val category: String
)

class DatasetManager(private val context: Context) {

    val datasetDir: File by lazy {
        File(context.filesDir, "dataset").apply { mkdirs() }
    }

    val mastersDir: File by lazy {
        File(datasetDir, "masters").apply { mkdirs() }
    }

    val encodedDir: File by lazy {
        File(datasetDir, "encoded").apply { mkdirs() }
    }

    fun listMasterImages(): List<ImageMetadata> {
        val files = mastersDir.listFiles { f ->
            f.extension.lowercase() in listOf("png", "webp", "jxl", "avif")
        } ?: emptyArray()

        return files.map { file ->
            val sha256 = calculateSha256(file)
            ImageMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                width = 1600,
                height = 2400,
                hasAlpha = false,
                fileSizeBytes = file.length(),
                sha256 = sha256,
                category = detectCategory(file.name)
            )
        }
    }

    /**
     * Generates standard reproducible manga-like synthetic test patterns
     * if no external dataset is imported yet.
     */
    fun generateSyntheticSample(name: String, width: Int, height: Int): File {
        val file = File(mastersDir, "$name.png")
        if (file.exists() && file.length() > 0) return file

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            isAntiAlias = true
        }

        // 1. Draw screentone / grid pattern
        val step = 16f
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
        }
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += step
        }

        // 2. Draw mock speech bubble and text
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, 300f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = 6f
        canvas.drawCircle(cx, cy, 300f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 64f
        val text = "Komikku Benchmark Page"
        val textWidth = paint.measureText(text)
        canvas.drawText(text, cx - textWidth / 2f, cy, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    private fun detectCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("bw") -> "Black & White"
            lower.contains("gray") -> "Grayscale"
            lower.contains("color") -> "Color"
            lower.contains("tone") -> "Screentone"
            lower.contains("text") -> "Speech Bubble / Text"
            else -> "Manga Page"
        }
    }

    private fun calculateSha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var len: Int
                while (input.read(buf).also { len = it } != -1) {
                    md.update(buf, 0, len)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }
}
