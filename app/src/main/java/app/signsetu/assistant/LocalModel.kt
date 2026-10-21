package app.signsetu.assistant

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

/** A llama.cpp model in memory; one request at a time, each starting from an empty context. */
class LocalModel private constructor(private var handle: Long) {
    fun chat(system: String, user: String, maxTokens: Int): String =
        synchronized(this) { if (handle == 0L) "" else nativeChat(handle, system, user, maxTokens).trim() }

    fun close() = synchronized(this) {
        if (handle != 0L) nativeFree(handle)
        handle = 0L
    }

    companion object {
        private val loaded by lazy { runCatching { System.loadLibrary("assistant") }.isSuccess }

        fun load(file: File): LocalModel? {
            if (!loaded || !file.exists()) return null
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            return nativeLoad(file.absolutePath, threads, CONTEXT).takeIf { it != 0L }?.let(::LocalModel)
        }

        val supported: Boolean get() = loaded

        private const val CONTEXT = 1024

        @JvmStatic private external fun nativeLoad(path: String, threads: Int, context: Int): Long
        @JvmStatic private external fun nativeChat(handle: Long, system: String, user: String, maxTokens: Int): String
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}

/**
 * The model file on this phone. It is imported once from a file the department hands out, and accepted only if
 * its hash is one the app knows, so a swapped or damaged file is never parsed.
 */
class ModelStore(private val context: Context) {
    private val file = File(context.noBackupFilesDir, "models/assistant.gguf")
    private val lock = Mutex()
    private var model: LocalModel? = null

    enum class State { NOT_INSTALLED, READY, TOO_LITTLE_MEMORY, UNSUPPORTED }

    fun state(): State = when {
        !LocalModel.supported -> State.UNSUPPORTED
        !enoughMemory() -> State.TOO_LITTLE_MEMORY
        !file.exists() -> State.NOT_INSTALLED
        else -> State.READY
    }

    /** Copies and checks the picked file; returns false, keeping nothing, if it is not a known model. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            unload()
            val partial = File(file.parentFile, "import.part").apply { parentFile?.mkdirs() }
            val digest = MessageDigest.getInstance("SHA-256")
            val ok = runCatching {
                DigestInputStream(context.contentResolver.openInputStream(uri)!!, digest).use { input ->
                    partial.outputStream().use { input.copyTo(it, 1 shl 20) }
                }
                digest.digest().toHexString() in KNOWN_MODELS
            }.getOrDefault(false)
            if (ok) partial.renameTo(file) else partial.delete()
            ok
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) { lock.withLock { unload(); file.delete() } }

    /** Runs [block] with the model, loading it on first use. */
    suspend fun <T> use(block: (LocalModel) -> T): T? = withContext(Dispatchers.Default) {
        lock.withLock {
            if (state() != State.READY) return@withLock null
            val m = model ?: (if (roomToLoad()) LocalModel.load(file)?.also { model = it } else null) ?: return@withLock null
            block(m)
        }
    }

    /** Frees the model's memory; it loads again on the next request. */
    suspend fun release() = lock.withLock { unload() }

    private fun unload() {
        model?.close()
        model = null
    }

    private fun memory() = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    private fun enoughMemory(): Boolean = memory().totalMem >= MIN_MEMORY_BYTES

    // Checked at load time: total memory says nothing about what the camera and the stage are using right now.
    private fun roomToLoad(): Boolean = memory().let { !it.lowMemory && it.availMem >= MIN_FREE_BYTES }

    companion object {
        // A 3 GB phone reports well under 3 GB; whether 0.9 GB is free for the model is checked when it loads.
        private const val MIN_MEMORY_BYTES = 2_400L * 1024 * 1024
        private const val MIN_FREE_BYTES = 1_200L * 1024 * 1024

        /** SHA-256 of the model files the department may install; see docs/12-on-device-assistant.md. */
        val KNOWN_MODELS = setOf(
            // Gemma 3 1B instruction-tuned, Q4_K_M GGUF (Gemma Terms of Use), from ggml-org/gemma-3-1b-it-GGUF.
            "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
        )
    }
}
