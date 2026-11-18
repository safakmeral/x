// G-12: minimal JNI bridge for tr.yasliasistan.speech.whisper.WhisperNative.
//
// Privacy (NFG-48/49, KVKK): audio arrives as a float array in memory and is never written anywhere.
// Recognized text is returned as UTF-8 bytes and is never logged here; whisper.cpp's own log is
// forwarded only for warnings and errors, which carry no transcript.
#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <string>

#include "whisper.h"

namespace {

constexpr const char *kTag = "whisper.cpp";

struct Session {
    whisper_context *ctx = nullptr;
    std::atomic<bool> abort{false};
};

void forward_log(ggml_log_level level, const char *text, void * /*user_data*/) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, kTag, text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_write(ANDROID_LOG_WARN, kTag, text);
    }
}

bool should_abort(void *user_data) {
    return static_cast<Session *>(user_data)->abort.load(std::memory_order_relaxed);
}

Session *from_handle(jlong handle) {
    return reinterpret_cast<Session *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_tr_yasliasistan_speech_whisper_WhisperNative_nativeLoad(
        JNIEnv *env, jobject /*thiz*/, jstring model_path, jboolean flash_attn) {
    whisper_log_set(forward_log, nullptr);
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = flash_attn == JNI_TRUE;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) return 0;

    auto *session = new Session();
    session->ctx = ctx;
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_tr_yasliasistan_speech_whisper_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jfloatArray samples, jstring language,
        jint threads, jint audio_ctx, jint beam_size) {
    Session *session = from_handle(handle);
    if (session == nullptr || session->ctx == nullptr || samples == nullptr) return nullptr;

    const jsize n = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    if (pcm == nullptr) return nullptr;
    const char *lang = env->GetStringUTFChars(language, nullptr);

    const bool beam = beam_size > 1;
    whisper_full_params params = whisper_full_default_params(
            beam ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.translate = false;
    params.detect_language = false;
    params.n_threads = threads > 0 ? threads : 1;
    params.audio_ctx = audio_ctx > 0 ? audio_ctx : 0;
    if (beam) params.beam_search.beam_size = beam_size;
    params.no_context = true;        // every command is independent
    params.single_segment = true;    // short commands: one segment, less looping
    params.no_timestamps = true;
    params.suppress_blank = true;
    params.suppress_nst = true;      // no "(music)" style non-speech tokens
    params.print_progress = false;   // never print text to stdout/logcat
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.abort_callback = should_abort;
    params.abort_callback_user_data = session;

    session->abort.store(false, std::memory_order_relaxed);
    const int rc = whisper_full(session->ctx, params, pcm, n);

    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);  // JNI_ABORT: no copy back
    if (rc != 0 || session->abort.load(std::memory_order_relaxed)) return nullptr;

    std::string text;
    const int segments = whisper_full_n_segments(session->ctx);
    for (int i = 0; i < segments; ++i) {
        const char *segment = whisper_full_get_segment_text(session->ctx, i);
        if (segment != nullptr) text += segment;
    }
    // Bytes, not NewStringUTF: whisper may emit invalid UTF-8, which would abort the VM.
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr && !text.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    // Wipe the local copy of the text.
    std::fill(text.begin(), text.end(), '\0');
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_tr_yasliasistan_speech_whisper_WhisperNative_nativeAbort(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    Session *session = from_handle(handle);
    if (session != nullptr) session->abort.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_tr_yasliasistan_speech_whisper_WhisperNative_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    Session *session = from_handle(handle);
    if (session == nullptr) return;
    whisper_free(session->ctx);
    delete session;
}

extern "C" JNIEXPORT jstring JNICALL
Java_tr_yasliasistan_speech_whisper_WhisperNative_nativeSystemInfo(
        JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(whisper_print_system_info());
}
