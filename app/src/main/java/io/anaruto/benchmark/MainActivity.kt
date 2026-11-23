package io.anaruto.benchmark

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.anaruto.benchmark.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var datasetManager: DatasetManager
    private lateinit var runner: BenchmarkRunner
    private var benchmarkJob: Job? = null
    private val resultsList = mutableListOf<BenchmarkResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        datasetManager = DatasetManager(this)
        runner = BenchmarkRunner(datasetManager)

        initViews()
        initSpinners()
        checkOrInitDataset()
    }

    private fun initViews() {
        val meta = DeviceMetadata()
        binding.tvDeviceInfo.text = "Device: ${meta.manufacturer} ${meta.deviceModel} | SoC: ${meta.soc} | Android: ${meta.androidVersion} (API ${meta.sdkInt}) | ABI: ${meta.abi}"

        binding.btnRunBenchmark.setOnClickListener {
            if (benchmarkJob?.isActive == true) {
                benchmarkJob?.cancel()
                binding.btnRunBenchmark.text = "Run Benchmark"
                binding.tvStatus.text = "Cancelled"
                binding.progressBar.visibility = View.GONE
            } else {
                startSelectedBenchmark()
            }
        }

        binding.btnExport.setOnClickListener {
            exportCurrentResults()
        }
    }

    private fun initSpinners() {
        val codecs = listOf("WebP", "JPEG XL", "AVIF")
        val codecAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codecs)
        binding.spCodec.adapter = codecAdapter

        val targets = listOf("Native Buffer", "Android Bitmap")
        val targetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, targets)
        binding.spTarget.adapter = targetAdapter

        val threads = listOf("1 Thread", "2 Threads", "4 Threads")
        val threadAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, threads)
        binding.spThreads.adapter = threadAdapter
    }

    private fun checkOrInitDataset() {
        lifecycleScope.launch {
            val sample = datasetManager.generateSyntheticSample("manga_page_001_1600x2400", 1600, 2400)
            logConsole("Initialized sample dataset image: ${sample.name} (${sample.length() / 1024} KB)")
        }
    }

    private fun startSelectedBenchmark() {
        val selectedCodec = when (binding.spCodec.selectedItemPosition) {
            0 -> Codec.WEBP
            1 -> Codec.JXL
            else -> Codec.AVIF
        }

        val target = if (binding.spTarget.selectedItemPosition == 0) {
            OutputTarget.NATIVE_BUFFER
        } else {
            OutputTarget.ANDROID_BITMAP
        }

        val threads = when (binding.spThreads.selectedItemPosition) {
            1 -> 2
            2 -> 4
            else -> 1
        }

        val config = DecoderBenchmarkConfig(
            codec = selectedCodec,
            outputTarget = target,
            threadCount = threads,
            warmupIterations = 5,
            testIterations = 20
        )

        val sampleFile = File(datasetManager.mastersDir, "manga_page_001_1600x2400.png")

        binding.btnRunBenchmark.text = "Stop Benchmark"
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        logConsole("\n--- Starting Benchmark: ${selectedCodec.label} | $target | $threads threads ---")

        benchmarkJob = lifecycleScope.launch {
            runner.runDecoderBenchmark(config, sampleFile) { progress ->
                when (progress) {
                    is BenchmarkRunner.BenchmarkProgress.Running -> {
                        binding.tvStatus.text = progress.message
                        binding.progressBar.max = progress.totalIterations
                        binding.progressBar.progress = progress.currentIteration
                    }
                    is BenchmarkRunner.BenchmarkProgress.Completed -> {
                        resultsList.add(progress.result)
                        displayResultSummary(progress.result)
                        binding.btnRunBenchmark.text = "Run Benchmark"
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Benchmark Completed"
                    }
                    is BenchmarkRunner.BenchmarkProgress.Error -> {
                        logConsole("ERROR: ${progress.message}")
                        binding.btnRunBenchmark.text = "Run Benchmark"
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = "Error: ${progress.message}"
                    }
                }
            }
        }
    }

    private fun displayResultSummary(r: BenchmarkResult) {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("RESULT: ${r.codec.label} (${r.codecVersion})")
        sb.appendLine("Target: ${r.outputTarget} | Threads: ${r.threadCount}")
        sb.appendLine("Resolution: ${r.width}x${r.height}")
        sb.appendLine("Median Latency: %.2f ms".format(r.medianMs))
        sb.appendLine("P95 Latency:    %.2f ms".format(r.p95Ms))
        sb.appendLine("P99 Latency:    %.2f ms".format(r.p99Ms))
        sb.appendLine("Throughput:     %.2f img/sec (%.2f MP/sec)".format(r.imagesPerSecond, r.megaPixelsPerSecond))
        sb.appendLine("Peak RSS:       ${r.peakRssKb} KB")
        sb.appendLine("Native Heap:    ${r.nativeHeapKb} KB")
        sb.appendLine("==========================================")
        logConsole(sb.toString())
    }

    private fun exportCurrentResults() {
        if (resultsList.isEmpty()) {
            Toast.makeText(this, "No benchmark results to export yet!", Toast.LENGTH_SHORT).show()
            return
        }

        val exportDir = File(datasetManager.datasetDir, "results").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val csvFile = File(exportDir, "benchmark_results_$ts.csv")
        val jsonFile = File(exportDir, "benchmark_results_$ts.json")

        val csvOk = CsvExporter.export(resultsList, csvFile)
        val jsonOk = JsonExporter.export(resultsList, jsonFile)

        if (csvOk && jsonOk) {
            logConsole("Exported CSV: ${csvFile.absolutePath}")
            logConsole("Exported JSON: ${jsonFile.absolutePath}")
            Toast.makeText(this, "Exported results successfully!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to export results", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logConsole(msg: String) {
        binding.tvConsole.append("$msg\n")
    }
}
