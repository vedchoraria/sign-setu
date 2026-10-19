package app.signsetu.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.signsetu.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable private data class Msg(val role: String, val content: String)
@Serializable private data class Req(val model: String, val messages: List<Msg>, val temperature: Double, val max_tokens: Int)
@Serializable private data class Choice(val message: Msg)
@Serializable private data class Resp(val choices: List<Choice> = emptyList())

/**
 * Optional third-party AI, used only while the officer has switched it on, a key is set and the phone is online.
 * Names are replaced before sending and put back on the phone; any failure returns null and the on-device path runs.
 */
class CloudAi(private val context: Context, private val settings: SettingsStore) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun ask(system: String, user: String, names: Collection<String>, maxTokens: Int = 800): String? {
        val s = settings.current()
        if (!s.cloudAi || s.cloudAiKey.isBlank() || !online()) return null
        val mask = Pseudonymizer(names)
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(Req.serializer(), Req(MODEL, listOf(Msg("system", system), Msg("user", mask.mask(user))), 0.2, maxTokens))
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${s.cloudAiKey}")
                    connectTimeout = 10_000
                    readTimeout = 25_000
                    doOutput = true
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    if (conn.responseCode !in 200..299) return@runCatching null
                    val text = conn.inputStream.use { it.readBytes().decodeToString() }
                    json.decodeFromString(Resp.serializer(), text).choices.firstOrNull()?.message?.content?.trim()
                        ?.takeIf { it.isNotBlank() }?.let(mask::restore)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
    }

    suspend fun report(sessionLog: String, role: String, dialect: String, names: Collection<String>): String =
        ask(REPORT_PROMPT, "Role: $role\nLanguage: $dialect\nSession log:\n$sessionLog", names, 1200)
            ?: OfflineReport.synthesizeOfflineForensicReport(sessionLog, role, dialect)

    suspend fun followUps(lastAnswer: String, isChild: Boolean, names: Collection<String>): List<String> {
        val tone = if (isChild) "a young traumatised child: short, gentle, simple" else "a vulnerable adult: factual, calm, no pressure"
        val raw = ask(
            "Propose 2 to 3 non-leading follow-up questions for $tone. One question per line starting with '-', no preamble.",
            "Latest answer: $lastAnswer", names, 300,
        )
        val parsed = raw?.lines().orEmpty().map { it.trim().removePrefix("-").removePrefix("*").trim() }.filter { it.endsWith("?") }
        return parsed.ifEmpty { OfflineReport.offlineTraumaFollowUps(lastAnswer, isChild) }
    }

    private companion object {
        const val ENDPOINT = "https://api.tokenrouter.com/v1/chat/completions"
        const val MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"
        const val REPORT_PROMPT = "You are a trauma-informed child-protection police investigator. From the session log write a factual, " +
            "objective report with: 1 incident and timeline, 2 statements by the subject (exact words and gestures), " +
            "3 welfare needs, 4 persons mentioned, 5 reliability (non-leading check), 6 recommended police and CWC actions."
    }
}
