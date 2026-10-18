// The bridge between Jotter and llama.cpp: open a small language model and have it answer one
// prompt at a time, reporting progress and stopping when asked.
#include <jni.h>

#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

extern std::atomic<bool> cancelled;

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string tmpl;
};

std::string utf(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text, bool special) {
    int n = -llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, special, true);
    std::vector<llama_token> out(n > 0 ? n : 0);
    if (n > 0) llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), out.data(), n, special, true);
    return out;
}

void report(JNIEnv * env, jobject listener, jmethodID onProgress, int pct) {
    if (listener != nullptr && onProgress != nullptr) env->CallVoidMethod(listener, onProgress, pct);
}

bool shouldAbort(void *) { return cancelled.load(); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_io_github_akrishna87_jotter_Llm_open(JNIEnv * env, jobject, jstring path, jint nCtx, jint threads) {
    static bool started = false;
    if (!started) {
        llama_backend_init();
        started = true;
    }
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    std::string p = utf(env, path);
    llama_model * model = llama_model_load_from_file(p.c_str(), mp);
    if (model == nullptr) return 0;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(nCtx);
    cp.n_batch = 512;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }
    llama_set_abort_callback(ctx, shouldAbort, nullptr);

    auto * s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    const char * t = llama_model_chat_template(model, nullptr);
    s->tmpl = t != nullptr ? t : "chatml";
    // Low temperature and a repeat penalty: faithful, and no looping on the same line.
    s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s->sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(s->vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(42));
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL Java_io_github_akrishna87_jotter_Llm_close(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return;
    if (s->sampler != nullptr) llama_sampler_free(s->sampler);
    if (s->ctx != nullptr) llama_free(s->ctx);
    if (s->model != nullptr) llama_model_free(s->model);
    delete s;
}

JNIEXPORT jint JNICALL Java_io_github_akrishna87_jotter_Llm_countTokens(JNIEnv * env, jobject, jlong handle, jstring text) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return 0;
    return static_cast<jint>(tokenize(s->vocab, utf(env, text), false).size());
}

JNIEXPORT jint JNICALL Java_io_github_akrishna87_jotter_Llm_contextSize(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    return s == nullptr ? 0 : static_cast<jint>(llama_n_ctx(s->ctx));
}

// Answers [user] (with [system] as the instructions), writing at most [maxTokens]. Progress runs
// 0-70 while reading the prompt and 70-100 while writing. Returns UTF-8, or null if it failed or
// was stopped.
JNIEXPORT jbyteArray JNICALL Java_io_github_akrishna87_jotter_Llm_generate(
    JNIEnv * env, jobject, jlong handle, jstring system, jstring user, jint maxTokens, jobject listener) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return nullptr;
    jmethodID onProgress = nullptr;
    if (listener != nullptr) {
        jclass cls = env->GetObjectClass(listener);
        onProgress = env->GetMethodID(cls, "onProgress", "(I)V");
        env->DeleteLocalRef(cls);
    }

    std::string sys = utf(env, system);
    std::string usr = utf(env, user);
    llama_chat_message msgs[2] = {{"system", sys.c_str()}, {"user", usr.c_str()}};
    std::vector<char> buf(sys.size() + usr.size() + 1024);
    int n = llama_chat_apply_template(s->tmpl.c_str(), msgs, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n > static_cast<int>(buf.size())) {
        buf.resize(n + 1);
        n = llama_chat_apply_template(s->tmpl.c_str(), msgs, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (n < 0) return nullptr;
    std::string prompt(buf.data(), n);

    std::vector<llama_token> tokens = tokenize(s->vocab, prompt, true);
    const int nCtx = static_cast<int>(llama_n_ctx(s->ctx));
    if (tokens.empty() || static_cast<int>(tokens.size()) + maxTokens > nCtx) return nullptr;

    llama_memory_clear(llama_get_memory(s->ctx), true);
    llama_sampler_reset(s->sampler);

    const int batch = static_cast<int>(llama_n_batch(s->ctx));
    for (size_t i = 0; i < tokens.size(); i += batch) {
        if (cancelled.load()) return nullptr;
        int len = static_cast<int>(std::min(tokens.size() - i, static_cast<size_t>(batch)));
        if (llama_decode(s->ctx, llama_batch_get_one(tokens.data() + i, len)) != 0) return nullptr;
        report(env, listener, onProgress, static_cast<int>((i + len) * 70 / tokens.size()));
    }

    std::string out;
    int last = -1;
    for (int i = 0; i < maxTokens; i++) {
        if (cancelled.load()) return nullptr;
        llama_token tok = llama_sampler_sample(s->sampler, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        char piece[256];
        int len = llama_token_to_piece(s->vocab, tok, piece, sizeof(piece), 0, false);
        if (len > 0) out.append(piece, len);
        if (llama_decode(s->ctx, llama_batch_get_one(&tok, 1)) != 0) return nullptr;
        int pct = 70 + i * 30 / maxTokens;
        if (pct != last) {
            last = pct;
            report(env, listener, onProgress, pct);
        }
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

}  // extern "C"
