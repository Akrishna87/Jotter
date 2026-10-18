// The bridge between Jotter (Kotlin) and whisper.cpp: load the CPU backends, open a model,
// transcribe 16 kHz mono audio, report progress and stop when asked.
#include <jni.h>

#include <atomic>
#include <string>

#include "ggml-backend.h"
#include "whisper.h"

// Set by Kotlin to stop transcription or a summary part-way; shared with summary.cpp.
std::atomic<bool> cancelled{false};

namespace {

struct Listener {
    JNIEnv * env;
    jobject target;
    jmethodID onProgress;
};

std::string utf(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

void report(whisper_context *, whisper_state *, int progress, void * user) {
    auto * l = static_cast<Listener *>(user);
    if (l->target != nullptr && l->onProgress != nullptr) l->env->CallVoidMethod(l->target, l->onProgress, progress);
}

bool keepGoing(void *) { return !cancelled.load(); }

bool shouldAbort(void *) { return cancelled.load(); }

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL Java_io_github_akrishna87_jotter_Whisper_loadBackends(JNIEnv * env, jobject, jstring dir) {
    if (ggml_backend_reg_count() == 0) {
        std::string d = utf(env, dir);
        ggml_backend_load_all_from_path(d.c_str());
    }
    return static_cast<jint>(ggml_backend_reg_count());
}

JNIEXPORT jlong JNICALL Java_io_github_akrishna87_jotter_Whisper_open(JNIEnv * env, jobject, jstring model) {
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    std::string path = utf(env, model);
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params(path.c_str(), params));
}

JNIEXPORT void JNICALL Java_io_github_akrishna87_jotter_Whisper_close(JNIEnv *, jobject, jlong ctx) {
    if (ctx != 0) whisper_free(reinterpret_cast<whisper_context *>(ctx));
}

JNIEXPORT void JNICALL Java_io_github_akrishna87_jotter_Whisper_setCancelled(JNIEnv *, jobject, jboolean on) {
    cancelled.store(on == JNI_TRUE);
}

// Returns UTF-8 lines: the language code first, then "startMs<TAB>endMs<TAB>text" per segment.
// Null if the model failed or transcription was stopped.
JNIEXPORT jbyteArray JNICALL Java_io_github_akrishna87_jotter_Whisper_transcribe(
    JNIEnv * env, jobject, jlong ctxPtr, jfloatArray pcm, jint threads, jstring language, jboolean translate, jstring prompt, jobject listener) {
    auto * ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx == nullptr || pcm == nullptr) return nullptr;

    std::string lang = utf(env, language);
    std::string context = utf(env, prompt);

    Listener l{env, listener, nullptr};
    if (listener != nullptr) {
        jclass cls = env->GetObjectClass(listener);
        l.onProgress = env->GetMethodID(cls, "onProgress", "(I)V");
        env->DeleteLocalRef(cls);
    }

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = threads;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;
    p.translate = translate == JNI_TRUE;
    p.language = lang.empty() ? "auto" : lang.c_str();
    p.detect_language = false;
    p.no_context = context.empty();
    p.initial_prompt = context.empty() ? nullptr : context.c_str();
    p.suppress_blank = true;
    p.progress_callback = report;
    p.progress_callback_user_data = &l;
    p.encoder_begin_callback = [](whisper_context *, whisper_state *, void * u) { return keepGoing(u); };
    p.abort_callback = shouldAbort;

    jsize n = env->GetArrayLength(pcm);
    jfloat * samples = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, p, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0 || cancelled.load()) return nullptr;

    std::string out;
    int langId = whisper_full_lang_id(ctx);
    const char * code = langId >= 0 ? whisper_lang_str(langId) : nullptr;
    out += code != nullptr ? code : "";
    out += '\n';
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        std::string text = whisper_full_get_segment_text(ctx, i);
        for (char & c : text) {
            if (c == '\n' || c == '\t' || c == '\r') c = ' ';
        }
        out += std::to_string(whisper_full_get_segment_t0(ctx, i) * 10);
        out += '\t';
        out += std::to_string(whisper_full_get_segment_t1(ctx, i) * 10);
        out += '\t';
        out += text;
        out += '\n';
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

}  // extern "C"
