// JNI bridge to llama.cpp for the on-device assistant. One model and one context per handle;
// every call starts from an empty context, so nothing from one request reaches the next.
#include <jni.h>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"

namespace {

constexpr int32_t kBatch = 256;

struct Assistant {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
};

// GetStringUTFChars gives modified UTF-8, which splits characters outside the BMP into surrogates; ask Java for real UTF-8.
std::string utf8(JNIEnv * env, jstring s) {
    jclass string = env->FindClass("java/lang/String");
    jmethodID getBytes = env->GetMethodID(string, "getBytes", "(Ljava/lang/String;)[B");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(s, getBytes, env->NewStringUTF("UTF-8")));
    std::string out(static_cast<size_t>(env->GetArrayLength(bytes)), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(out.size()), reinterpret_cast<jbyte *>(out.data()));
    return out;
}

// JNI's NewStringUTF wants modified UTF-8 and a whole code point at the end; pass bytes through String(byte[], UTF_8).
jstring toJava(JNIEnv * env, const std::string & s) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    jclass string = env->FindClass("java/lang/String");
    jmethodID init = env->GetMethodID(string, "<init>", "([BLjava/lang/String;)V");
    return static_cast<jstring>(env->NewObject(string, init, bytes, env->NewStringUTF("UTF-8")));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_signsetu_assistant_LocalModel_nativeLoad(JNIEnv * env, jclass, jstring path, jint threads, jint context) {
    llama_backend_init();
    auto mp = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(utf8(env, path).c_str(), mp);
    if (!model) return 0;
    auto cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(context);
    // The batch sizes the compute buffer; prompts are short and longer ones are fed in pieces below.
    cp.n_batch = kBatch;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        llama_model_free(model);
        return 0;
    }
    return reinterpret_cast<jlong>(new Assistant{model, ctx});
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_signsetu_assistant_LocalModel_nativeChat(JNIEnv * env, jclass, jlong handle, jstring system, jstring user, jint maxTokens) {
    auto * a = reinterpret_cast<Assistant *>(handle);
    const llama_vocab * vocab = llama_model_get_vocab(a->model);
    llama_memory_clear(llama_get_memory(a->ctx), true);

    std::string sys = utf8(env, system);
    std::string usr = utf8(env, user);
    llama_chat_message messages[] = {{"system", sys.c_str()}, {"user", usr.c_str()}};
    const char * tmpl = llama_model_chat_template(a->model, nullptr);
    std::vector<char> buf(2 * (sys.size() + usr.size()) + 512);
    int32_t n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n > static_cast<int32_t>(buf.size())) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (n < 0) return toJava(env, "");
    std::string prompt(buf.data(), n);

    std::vector<llama_token> tokens(prompt.size() + 16);
    int32_t count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count < 0 || count + maxTokens > static_cast<int32_t>(llama_n_ctx(a->ctx))) return toJava(env, "");
    tokens.resize(count);

    // Greedy decoding: the same request always gives the same answer, which reviewers and tests rely on.
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string out;
    bool ok = true;
    for (int32_t at = 0; ok && at < count; at += kBatch) {
        ok = llama_decode(a->ctx, llama_batch_get_one(tokens.data() + at, std::min(kBatch, count - at))) == 0;
    }
    llama_token next = 0;
    for (int i = 0; ok && i < maxTokens; ++i) {
        if (i > 0 && llama_decode(a->ctx, llama_batch_get_one(&next, 1)) != 0) break;
        next = llama_sampler_sample(sampler, a->ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        char piece[256];
        int32_t len = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (len > 0) out.append(piece, len);
    }
    llama_sampler_free(sampler);
    return toJava(env, out);
}

extern "C" JNIEXPORT void JNICALL
Java_app_signsetu_assistant_LocalModel_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto * a = reinterpret_cast<Assistant *>(handle);
    llama_free(a->ctx);
    llama_model_free(a->model);
    delete a;
}
