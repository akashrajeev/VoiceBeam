#include <jni.h>
#include <string>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_akashrajeev_voicebeam_ml_WhisperNative_load(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_context_default_params();
    params.use_gpu = false; // CPU reference experiment, no unverified GPU assumption.
    auto *ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_akashrajeev_voicebeam_ml_WhisperNative_decode(JNIEnv *env, jobject, jlong handle, jfloatArray audio) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx) return env->NewStringUTF("");
    const jsize n = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = 2;
    p.language = "en";
    p.translate = false;
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = true;
    p.print_realtime = p.print_progress = p.print_timestamps = p.print_special = false;
    int status = whisper_full(ctx, p, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    if (status != 0) {
        auto e = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(e, "Offline Whisper decoding failed");
        return nullptr;
    }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(ctx); ++i) text += whisper_full_get_segment_text(ctx, i);
    return env->NewStringUTF(text.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_akashrajeev_voicebeam_ml_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    if (handle) whisper_free(reinterpret_cast<whisper_context *>(handle));
}
