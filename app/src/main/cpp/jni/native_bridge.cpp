#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>

#include "image_types.h"
#include "decoder.h"
#include "encoder.h"
#include "benchmark.h"

#define LOG_TAG "KomikkuJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static thread_local std::string g_last_error = "";

extern "C" {

JNIEXPORT jstring JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeGetLastError(JNIEnv* env, jclass /* clazz */) {
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeGetVersion(JNIEnv* env, jclass /* clazz */, jint codec_id) {
    auto decoder = komikku::IDecoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!decoder) {
        return env->NewStringUTF("Unknown");
    }
    return env->NewStringUTF(decoder->GetVersion());
}

JNIEXPORT jboolean JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeGetInfo(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jbyteArray data_array,
    jintArray out_info_array
) {
    if (!data_array || !out_info_array) {
        g_last_error = "Invalid arguments to nativeGetInfo";
        return JNI_FALSE;
    }

    jsize data_len = env->GetArrayLength(data_array);
    jbyte* data_bytes = env->GetByteArrayElements(data_array, nullptr);
    if (!data_bytes) {
        g_last_error = "Failed to lock byte array";
        return JNI_FALSE;
    }

    auto decoder = komikku::IDecoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!decoder) {
        env->ReleaseByteArrayElements(data_array, data_bytes, JNI_ABORT);
        g_last_error = "Failed to instantiate decoder";
        return JNI_FALSE;
    }

    komikku::ImageInfo info;
    bool success = decoder->ReadInfo(reinterpret_cast<const uint8_t*>(data_bytes), static_cast<size_t>(data_len), &info);
    env->ReleaseByteArrayElements(data_array, data_bytes, JNI_ABORT);

    if (!success) {
        g_last_error = decoder->GetLastError();
        return JNI_FALSE;
    }

    jint info_buf[4];
    info_buf[0] = static_cast<jint>(info.width);
    info_buf[1] = static_cast<jint>(info.height);
    info_buf[2] = info.has_alpha ? 1 : 0;
    info_buf[3] = static_cast<jint>(info.bit_depth);

    env->SetIntArrayRegion(out_info_array, 0, 4, info_buf);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeDecodeToBuffer(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jbyteArray in_data,
    jobject out_direct_buffer,
    jint thread_count,
    jint pixel_format,
    jdoubleArray out_timing_array
) {
    if (!in_data || !out_direct_buffer) {
        g_last_error = "Invalid input or output buffer";
        return JNI_FALSE;
    }

    void* dst_ptr = env->GetDirectBufferAddress(out_direct_buffer);
    jlong dst_capacity = env->GetDirectBufferCapacity(out_direct_buffer);
    if (!dst_ptr || dst_capacity <= 0) {
        g_last_error = "Invalid DirectByteBuffer";
        return JNI_FALSE;
    }

    jsize in_len = env->GetArrayLength(in_data);
    jbyte* in_bytes = env->GetByteArrayElements(in_data, nullptr);
    if (!in_bytes) {
        g_last_error = "Failed to lock input byte array";
        return JNI_FALSE;
    }

    auto decoder = komikku::IDecoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!decoder) {
        env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);
        g_last_error = "Failed to create decoder";
        return JNI_FALSE;
    }

    komikku::DecodeOptions options;
    options.output_format = static_cast<komikku::PixelFormat>(pixel_format);
    options.thread_count = thread_count;

    komikku::BenchmarkTiming timing;
    bool ok = decoder->Decode(
        reinterpret_cast<const uint8_t*>(in_bytes),
        static_cast<size_t>(in_len),
        options,
        reinterpret_cast<uint8_t*>(dst_ptr),
        static_cast<size_t>(dst_capacity),
        &timing
    );

    env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);

    if (!ok) {
        g_last_error = decoder->GetLastError();
        return JNI_FALSE;
    }

    if (out_timing_array && env->GetArrayLength(out_timing_array) >= 5) {
        jdouble timings[5] = {
            timing.init_ms,
            timing.header_ms,
            timing.decode_ms,
            timing.color_convert_ms,
            timing.total_ms
        };
        env->SetDoubleArrayRegion(out_timing_array, 0, 5, timings);
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeDecodeToBitmap(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jbyteArray in_data,
    jobject bitmap,
    jint thread_count,
    jdoubleArray out_timing_array
) {
    if (!in_data || !bitmap) {
        g_last_error = "Null input data or bitmap";
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        g_last_error = "Failed to get AndroidBitmapInfo";
        return JNI_FALSE;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        g_last_error = "Failed to lock bitmap pixels";
        return JNI_FALSE;
    }

    jsize in_len = env->GetArrayLength(in_data);
    jbyte* in_bytes = env->GetByteArrayElements(in_data, nullptr);
    if (!in_bytes) {
        AndroidBitmap_unlockPixels(env, bitmap);
        g_last_error = "Failed to access input bytes";
        return JNI_FALSE;
    }

    auto decoder = komikku::IDecoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!decoder) {
        env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);
        AndroidBitmap_unlockPixels(env, bitmap);
        g_last_error = "Failed to create decoder";
        return JNI_FALSE;
    }

    komikku::DecodeOptions options;
    options.output_format = (info.format == ANDROID_BITMAP_FORMAT_RGB_565) ?
                            komikku::PixelFormat::RGB_565 : komikku::PixelFormat::RGBA_8888;
    options.thread_count = thread_count;

    size_t capacity = info.height * info.stride;
    komikku::BenchmarkTiming timing;
    bool ok = decoder->Decode(
        reinterpret_cast<const uint8_t*>(in_bytes),
        static_cast<size_t>(in_len),
        options,
        reinterpret_cast<uint8_t*>(pixels),
        capacity,
        &timing
    );

    env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, bitmap);

    if (!ok) {
        g_last_error = decoder->GetLastError();
        return JNI_FALSE;
    }

    if (out_timing_array && env->GetArrayLength(out_timing_array) >= 5) {
        jdouble timings[5] = {
            timing.init_ms,
            timing.header_ms,
            timing.decode_ms,
            timing.color_convert_ms,
            timing.total_ms
        };
        env->SetDoubleArrayRegion(out_timing_array, 0, 5, timings);
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeEncodeFromBuffer(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jobject direct_buffer,
    jint width,
    jint height,
    jboolean lossless,
    jint quality,
    jint effort,
    jint thread_count,
    jdoubleArray out_timing_array
) {
    if (!direct_buffer || width <= 0 || height <= 0) {
        g_last_error = "Invalid parameters for encode";
        return nullptr;
    }

    void* src_ptr = env->GetDirectBufferAddress(direct_buffer);
    if (!src_ptr) {
        g_last_error = "Invalid DirectByteBuffer";
        return nullptr;
    }

    auto encoder = komikku::IEncoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!encoder) {
        g_last_error = "Failed to create encoder";
        return nullptr;
    }

    komikku::EncodeOptions options;
    options.codec = static_cast<komikku::CodecType>(codec_id);
    options.lossless = (lossless == JNI_TRUE);
    options.quality = quality;
    options.effort_or_method = effort;
    options.thread_count = thread_count;

    std::vector<uint8_t> encoded_data;
    komikku::BenchmarkTiming timing;

    bool ok = encoder->Encode(
        reinterpret_cast<const uint8_t*>(src_ptr),
        static_cast<uint32_t>(width),
        static_cast<uint32_t>(height),
        options,
        encoded_data,
        &timing
    );

    if (!ok) {
        g_last_error = encoder->GetLastError();
        return nullptr;
    }

    if (out_timing_array && env->GetArrayLength(out_timing_array) >= 5) {
        jdouble timings[5] = {
            timing.init_ms,
            timing.header_ms,
            timing.decode_ms,
            timing.color_convert_ms,
            timing.total_ms
        };
        env->SetDoubleArrayRegion(out_timing_array, 0, 5, timings);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(encoded_data.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(encoded_data.size()),
                                reinterpret_cast<const jbyte*>(encoded_data.data()));
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeEncodeFromBitmap(
    JNIEnv* env,
    jclass clazz,
    jint codec_id,
    jobject bitmap,
    jboolean lossless,
    jint quality,
    jint effort,
    jint thread_count,
    jdoubleArray out_timing_array
) {
    if (!bitmap) {
        g_last_error = "Null bitmap provided";
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        g_last_error = "Failed to query bitmap info";
        return nullptr;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        g_last_error = "Failed to lock bitmap pixels";
        return nullptr;
    }

    auto encoder = komikku::IEncoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!encoder) {
        AndroidBitmap_unlockPixels(env, bitmap);
        g_last_error = "Failed to create encoder";
        return nullptr;
    }

    komikku::EncodeOptions options;
    options.codec = static_cast<komikku::CodecType>(codec_id);
    options.lossless = (lossless == JNI_TRUE);
    options.quality = quality;
    options.effort_or_method = effort;
    options.thread_count = thread_count;

    std::vector<uint8_t> encoded_data;
    komikku::BenchmarkTiming timing;

    bool ok = encoder->Encode(
        reinterpret_cast<const uint8_t*>(pixels),
        info.width,
        info.height,
        options,
        encoded_data,
        &timing
    );

    AndroidBitmap_unlockPixels(env, bitmap);

    if (!ok) {
        g_last_error = encoder->GetLastError();
        return nullptr;
    }

    if (out_timing_array && env->GetArrayLength(out_timing_array) >= 5) {
        jdouble timings[5] = {
            timing.init_ms,
            timing.header_ms,
            timing.decode_ms,
            timing.color_convert_ms,
            timing.total_ms
        };
        env->SetDoubleArrayRegion(out_timing_array, 0, 5, timings);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(encoded_data.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(encoded_data.size()),
                                reinterpret_cast<const jbyte*>(encoded_data.data()));
    }
    return result;
}

JNIEXPORT jdoubleArray JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeBenchmarkDecode(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jbyteArray in_data,
    jint warmup_iters,
    jint test_iters,
    jint thread_count,
    jint pixel_format
) {
    if (!in_data || test_iters <= 0) {
        g_last_error = "Invalid arguments to benchmark";
        return nullptr;
    }

    jsize in_len = env->GetArrayLength(in_data);
    jbyte* in_bytes = env->GetByteArrayElements(in_data, nullptr);
    if (!in_bytes) {
        g_last_error = "Failed to get bytes";
        return nullptr;
    }

    auto decoder = komikku::IDecoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!decoder) {
        env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);
        g_last_error = "Failed to create decoder";
        return nullptr;
    }

    komikku::ImageInfo info;
    if (!decoder->ReadInfo(reinterpret_cast<const uint8_t*>(in_bytes), static_cast<size_t>(in_len), &info)) {
        env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);
        g_last_error = decoder->GetLastError();
        return nullptr;
    }

    size_t out_size = komikku::CalculateBufferSize(info.width, info.height, static_cast<komikku::PixelFormat>(pixel_format));
    std::vector<uint8_t> out_buf(out_size);

    komikku::DecodeOptions options;
    options.output_format = static_cast<komikku::PixelFormat>(pixel_format);
    options.thread_count = thread_count;

    // Warm-up phase
    for (int i = 0; i < warmup_iters; ++i) {
        decoder->Decode(reinterpret_cast<const uint8_t*>(in_bytes), in_len, options, out_buf.data(), out_size, nullptr);
    }

    // Measurement phase
    std::vector<double> samples;
    samples.reserve(test_iters);

    for (int i = 0; i < test_iters; ++i) {
        komikku::BenchmarkTiming timing;
        if (decoder->Decode(reinterpret_cast<const uint8_t*>(in_bytes), in_len, options, out_buf.data(), out_size, &timing)) {
            samples.push_back(timing.total_ms);
        } else {
            samples.push_back(-1.0);
        }
    }

    env->ReleaseByteArrayElements(in_data, in_bytes, JNI_ABORT);

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(samples.size()));
    if (result) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(samples.size()), samples.data());
    }
    return result;
}

JNIEXPORT jdoubleArray JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeBenchmarkEncode(
    JNIEnv* env,
    jclass /* clazz */,
    jint codec_id,
    jobject direct_buffer,
    jint width,
    jint height,
    jboolean lossless,
    jint quality,
    jint effort,
    jint warmup_iters,
    jint test_iters,
    jint thread_count
) {
    if (!direct_buffer || width <= 0 || height <= 0 || test_iters <= 0) {
        g_last_error = "Invalid arguments to benchmark";
        return nullptr;
    }

    void* src_ptr = env->GetDirectBufferAddress(direct_buffer);
    if (!src_ptr) {
        g_last_error = "Invalid direct buffer";
        return nullptr;
    }

    auto encoder = komikku::IEncoder::Create(static_cast<komikku::CodecType>(codec_id));
    if (!encoder) {
        g_last_error = "Failed to create encoder";
        return nullptr;
    }

    komikku::EncodeOptions options;
    options.codec = static_cast<komikku::CodecType>(codec_id);
    options.lossless = (lossless == JNI_TRUE);
    options.quality = quality;
    options.effort_or_method = effort;
    options.thread_count = thread_count;

    std::vector<uint8_t> out_data;

    // Warm-up
    for (int i = 0; i < warmup_iters; ++i) {
        encoder->Encode(reinterpret_cast<const uint8_t*>(src_ptr), width, height, options, out_data, nullptr);
    }

    // Measurement
    std::vector<double> samples;
    samples.reserve(test_iters);

    for (int i = 0; i < test_iters; ++i) {
        komikku::BenchmarkTiming timing;
        if (encoder->Encode(reinterpret_cast<const uint8_t*>(src_ptr), width, height, options, out_data, &timing)) {
            samples.push_back(timing.total_ms);
        } else {
            samples.push_back(-1.0);
        }
    }

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(samples.size()));
    if (result) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(samples.size()), samples.data());
    }
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_io_anaruto_benchmark_NativeBenchmark_nativeGetMemoryMetrics(JNIEnv* env, jclass /* clazz */) {
    komikku::MemoryMetrics mem = komikku::ReadMemoryMetrics();
    jlong values[3] = { mem.rss_kb, mem.peak_rss_kb, mem.native_heap_kb };
    jlongArray result = env->NewLongArray(3);
    if (result) {
        env->SetLongArrayRegion(result, 0, 3, values);
    }
    return result;
}

} // extern "C"
