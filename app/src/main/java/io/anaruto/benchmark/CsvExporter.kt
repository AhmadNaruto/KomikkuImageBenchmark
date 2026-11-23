package io.anaruto.benchmark

import java.io.File
import java.io.FileWriter

object CsvExporter {

    private const val CSV_HEADER =
        "codec,mode,output_target,image,width,height,threads,file_size_bytes,median_ms,p95_ms,p99_ms,mean_ms,min_ms,max_ms,images_per_sec,megapixels_per_sec,peak_rss_kb,balanced_score,timestamp"

    fun export(results: List<BenchmarkResult>, destinationFile: File): Boolean {
        return try {
            destinationFile.parentFile?.mkdirs()
            FileWriter(destinationFile, false).use { writer ->
                writer.appendLine(CSV_HEADER)
                for (r in results) {
                    writer.appendLine(
                        listOf(
                            r.codec.label,
                            r.mode.name,
                            r.outputTarget.name,
                            "\"${r.imageName}\"",
                            r.width,
                            r.height,
                            r.threadCount,
                            r.encodedFileSizeBytes,
                            "%.3f".format(r.medianMs),
                            "%.3f".format(r.p95Ms),
                            "%.3f".format(r.p99Ms),
                            "%.3f".format(r.meanMs),
                            "%.3f".format(r.minMs),
                            "%.3f".format(r.maxMs),
                            "%.2f".format(r.imagesPerSecond),
                            "%.2f".format(r.megaPixelsPerSecond),
                            r.peakRssKb,
                            "%.2f".format(r.balancedScore),
                            r.timestamp
                        ).joinToString(",")
                    )
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
