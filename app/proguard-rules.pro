# Komikku Image Codec Benchmark Proguard Rules
-keepclassmembers class * {
    native <methods>;
}
-keep class io.anaruto.benchmark.NativeBenchmark { *; }
-keep class io.anaruto.benchmark.BenchmarkResult { *; }
-keep class io.anaruto.benchmark.BenchmarkConfig { *; }
