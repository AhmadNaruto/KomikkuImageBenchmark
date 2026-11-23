package io.anaruto.benchmark

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object JsonExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun export(results: List<BenchmarkResult>, destinationFile: File): Boolean {
        return try {
            destinationFile.parentFile?.mkdirs()
            val text = json.encodeToString(results)
            destinationFile.writeText(text)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
