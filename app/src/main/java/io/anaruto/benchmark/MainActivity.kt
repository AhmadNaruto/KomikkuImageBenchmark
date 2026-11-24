package io.anaruto.benchmark

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import io.anaruto.benchmark.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        initTabs()
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

    private fun initTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showDashboard()
                    1 -> logConsole("\n[Mode: Decoder Benchmark] Select Codec, Target, and Thread count, then click 'Run Benchmark'.")
                    2 -> logConsole("\n[Mode: Encoder Benchmark] Encodes master images across lossless and lossy matrix.")
                    3 -> showDatasetManager()
                    4 -> showResultsSummary()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                onTabSelected(tab)
            }
        })
    }

    private fun checkOrInitDataset() {
        lifecycleScope.launch(Dispatchers.Default) {
            val sample = datasetManager.generateMangaPattern("manga_page_001_1600x2400", 1600, 2400, "bw")
            withContext(Dispatchers.Main) {
                logConsole("Initialized sample dataset master: ${sample.name} (${sample.length() / 1024} KB)")
                binding.tvStatus.text = "Pre-encoding benchmark bitstreams..."
            }
            for (c in listOf(Codec.WEBP, Codec.JXL, Codec.AVIF)) {
                try {
                    val enc = datasetManager.getOrEncodeSample(sample, c)
                    withContext(Dispatchers.Main) {
                        logConsole("✓ Prepared ${c.label} bitstream: ${enc.name} (${enc.length() / 1024} KB)")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        logConsole("Notice: ${c.label} pre-encoding deferred: ${e.message}")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                binding.tvStatus.text = "Ready. Select Codec and click 'Run Benchmark'."
            }
        }
    }

    private fun showDashboard() {
        val webpVer = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_WEBP)
        val jxlVer = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_JXL)
        val avifVer = NativeBenchmark.nativeGetVersion(NativeBenchmark.CODEC_AVIF)
        val meta = DeviceMetadata()

        val sb = StringBuilder()
        sb.appendLine("\n================ DASHBOARD ================")
        sb.appendLine("Device: ${meta.manufacturer} ${meta.deviceModel}")
        sb.appendLine("SoC:    ${meta.soc} | RAM: 8GB LPDDR4X | ABI: ${meta.abi}")
        sb.appendLine("OS:     Android ${meta.androidVersion} (API ${meta.sdkInt})")
        sb.appendLine("-------------------------------------------")
        sb.appendLine("NATIVE CODEC LIBRARIES:")
        sb.appendLine("• WebP:    $webpVer")
        sb.appendLine("• JPEG XL: $jxlVer (libjxl + Highway SIMD)")
        sb.appendLine("• AVIF:    $avifVer (dav1d backend)")
        sb.appendLine("-------------------------------------------")
        sb.appendLine("Total benchmark runs recorded: ${resultsList.size}")
        sb.appendLine("Dataset directory: ${datasetManager.datasetDir.absolutePath}")
        sb.appendLine("===========================================")
        logConsole(sb.toString())
    }

    private fun showDatasetManager() {
        lifecycleScope.launch {
            binding.tvStatus.text = "Generating resolution test suite..."
            withContext(Dispatchers.IO) {
                datasetManager.generateMangaPattern("manga_bw_1600x2400", 1600, 2400, "bw")
                datasetManager.generateMangaPattern("manga_tone_2500x3600", 2500, 3600, "screentone")
                datasetManager.generateMangaPattern("manga_color_4000x6000", 4000, 6000, "color")
            }

            val list = withContext(Dispatchers.IO) { datasetManager.listMasterImages() }
            val sb = StringBuilder()
            sb.appendLine("\n============== DATASET CATALOG ==============")
            sb.appendLine("Total master images: ${list.size}")
            for (img in list) {
                sb.appendLine("• ${img.fileName} | ${img.width}x${img.height} | ${img.fileSizeBytes / 1024} KB | ${img.category}")
                sb.appendLine("  SHA-256: ${img.sha256}")
            }
            sb.appendLine("=============================================")
            logConsole(sb.toString())
            binding.tvStatus.text = "Dataset catalog updated."
        }
    }

    private fun showResultsSummary() {
        if (resultsList.isEmpty()) {
            logConsole("\n[Results] No benchmark results yet. Run benchmarks first.")
            return
        }

        val sb = StringBuilder()
        sb.appendLine("\n================= BENCHMARK SUMMARY TABLE =================")
        sb.appendLine("| Codec   | Target | Threads | Latency (ms) | Speed (MP/s) | Score |")
        sb.appendLine("|---------|--------|---------|--------------|--------------|-------|")
        for (r in resultsList) {
            val targetStr = if (r.outputTarget == OutputTarget.NATIVE_BUFFER) "Buffer" else "Bitmap"
            sb.appendLine("| %-7s | %-6s | %-7d | %12.2f | %12.2f | %5.1f |".format(
                r.codec.label, targetStr, r.threadCount, r.medianMs, r.megaPixelsPerSecond, r.balancedScore
            ))
        }
        sb.appendLine("===========================================================")
        logConsole(sb.toString())
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

        val sampleFile = File(datasetManager.mastersDir, "manga_page_001_1600x2400.png")

        binding.btnRunBenchmark.text = "Stop Benchmark"
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0

        val isEncoderMode = (binding.tabLayout.selectedTabPosition == 2)

        if (isEncoderMode) {
            val encConfig = EncoderBenchmarkConfig(
                codec = selectedCodec,
                lossless = true,
                quality = 90,
                effort = if (selectedCodec == Codec.JXL) 7 else 4,
                threadCount = threads,
                warmupIterations = 3,
                testIterations = 10
            )
            logConsole("\n--- Starting Encoder Benchmark: ${selectedCodec.label} | $threads threads ---")

            benchmarkJob = lifecycleScope.launch {
                runner.runEncoderBenchmark(encConfig, sampleFile) { progress ->
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
                            binding.tvStatus.text = "Encoder Benchmark Completed"
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
        } else {
            val config = DecoderBenchmarkConfig(
                codec = selectedCodec,
                outputTarget = target,
                threadCount = threads,
                warmupIterations = 5,
                testIterations = 20
            )
            logConsole("\n--- Starting Decoder Benchmark: ${selectedCodec.label} | $target | $threads threads ---")

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
                            binding.tvStatus.text = "Decoder Benchmark Completed"
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
        sb.appendLine("Balanced Score: %.2f / 100".format(r.balancedScore))
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
