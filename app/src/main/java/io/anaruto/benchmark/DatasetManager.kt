package io.anaruto.benchmark

import android.content.Context
import android.graphics.*
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

    val resultsDir: File by lazy {
        File(datasetDir, "results").apply { mkdirs() }
    }

    fun listMasterImages(): List<ImageMetadata> {
        val files = mastersDir.listFiles { f ->
            f.extension.lowercase() in listOf("png", "webp", "jxl", "avif")
        } ?: emptyArray()

        return files.map { file ->
            val sha256 = calculateSha256(file)
            val info = IntArray(4)
            // Query dimensions via WebP/Bitmap factory
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)

            ImageMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                width = if (opts.outWidth > 0) opts.outWidth else 1600,
                height = if (opts.outHeight > 0) opts.outHeight else 2400,
                hasAlpha = file.name.contains("alpha", ignoreCase = true),
                fileSizeBytes = file.length(),
                sha256 = sha256,
                category = detectCategory(file.name)
            )
        }
    }

    /**
     * Generates a reproducible standard manga pattern for benchmark suites:
     * Categories: BW, Grayscale, Color, Screentone, Text
     * Resolutions: 1600x2400, 2500x3600, 4000x6000
     */
    fun generateMangaPattern(
        name: String,
        width: Int,
        height: Int,
        category: String
    ): File {
        val file = File(mastersDir, "$name.png")
        if (file.exists() && file.length() > 0) return file

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (category.lowercase()) {
            "bw", "screentone" -> drawBwScreentone(canvas, width, height)
            "gray", "grayscale" -> drawGrayscaleWash(canvas, width, height)
            "color" -> drawColorIllustration(canvas, width, height)
            else -> drawBwScreentone(canvas, width, height)
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    private fun drawBwScreentone(canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            isAntiAlias = false // Sharp 1-bit lines
        }

        // Halftone dot / grid pattern
        val step = 12f
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

        // Manga panel borders
        paint.strokeWidth = 8f
        canvas.drawRect(40f, 40f, width - 40f, height / 2f - 20f, paint)
        canvas.drawRect(40f, height / 2f + 20f, width - 40f, height - 40f, paint)

        // Speech bubble & fine text
        val bubblePaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(width / 2f, height / 4f, 250f, bubblePaint)
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(width / 2f, height / 4f, 250f, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = (width / 35).toFloat()
        val text = "Komikku Manga Benchmark"
        canvas.drawText(text, width / 2f - paint.measureText(text) / 2f, height / 4f, paint)
    }

    private fun drawGrayscaleWash(canvas: Canvas, width: Int, height: Int) {
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE),
            null,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Grayscale circles
        val circlePaint = Paint().apply {
            color = Color.WHITE
            alpha = 180
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, height / 2f, width / 3f, circlePaint)
    }

    private fun drawColorIllustration(canvas: Canvas, width: Int, height: Int) {
        val shader = RadialGradient(
            width / 2f, height / 2f, width.toFloat(),
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE, Color.MAGENTA),
            null,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Detailed overlay
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = (width / 25).toFloat()
            isAntiAlias = true
            setShadowLayer(8f, 2f, 2f, Color.BLACK)
        }
        val label = "Full Color Manga Spread"
        canvas.drawText(label, width / 2f - textPaint.measureText(label) / 2f, height / 2f, textPaint)
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

    fun calculateSha256(file: File): String {
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
