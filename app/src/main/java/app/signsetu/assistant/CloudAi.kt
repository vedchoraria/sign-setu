package app.signsetu.assistant

import app.signsetu.data.content.SignLanguage
import app.signsetu.net.Http
import app.signsetu.net.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class AssistReq(val system: String, val user: String, val max_tokens: Int)
@Serializable private data class AssistResp(val text: String = "")
@Serializable private data class EnrollReq(val code: String)
@Serializable private data class EnrollResp(val token: String = "")
@Serializable private data class Msg(val role: String, val content: String)
@Serializable private data class ChatReq(val model: String, val messages: List<Msg>, val temperature: Double, val max_tokens: Int)
@Serializable private data class Choice(val message: Msg)
@Serializable private data class ChatResp(val choices: List<Choice> = emptyList())

/** A build that talks straight to an OpenAI-style service with its own key, when it has no gateway. The key is in the APK. */
class Direct(val url: String, val model: String, val key: String)

/** What the officer is told, once per change, when the online assistant stops or starts working. */
enum class Notice { FALLBACK_OFFLINE, FALLBACK_ERROR, REVOKED, BACK_ONLINE, ENROLLED, ENROLL_FAILED }

/**
 * The online half of the assistant: the project gateway when the build has one (else a [Direct] service), the phone is
 * online, the officer has switched it on and, for the gateway, the device is enrolled. Names are replaced before sending and put back on the phone. Any other case,
 * or any failure, returns null and the on-device path runs, so the app works the same with no connection (D-47).
 */
class CloudAi(
    private val enabled: suspend () -> Boolean,
    gatewayUrl: String,
    private val http: Http,
    private val vault: TokenVault,
    private val direct: Direct? = null,
    private val online: () -> Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Called on any thread; the UI shows it as a toast. */
    var onNotice: (Notice) -> Unit = {}
    private var degraded = false

    private fun fallBack(why: Notice): String? {
        if (!degraded) { degraded = true; onNotice(why) }
        return null
    }

    private val base = gatewayUrl.trimEnd('/')

    /** The gateway is used whenever the build has one; a direct service only without it. */
    val viaGateway: Boolean get() = base.startsWith("https://")

    /** This build has somewhere to send to; without it the online switches are not shown. */
    val available: Boolean get() = viaGateway || direct?.url?.startsWith("https://") == true && direct.key.isNotBlank()
    val enrolled: Boolean get() = !viaGateway || vault.get() != null

    /** Trades the department's one-time code for this device's token; false when it was refused or unreachable. */
    suspend fun enroll(code: String): Boolean {
        if (!viaGateway) return false
        if (!online()) { onNotice(Notice.ENROLL_FAILED); return false }
        val reply = withContext(Dispatchers.IO) {
            runCatching { http.call("POST", "$base/v1/enroll", json.encodeToString(EnrollReq.serializer(), EnrollReq(code.trim())), null) }.getOrNull()
        }
        val token = reply?.takeIf { it.ok }?.let { runCatching { json.decodeFromString(EnrollResp.serializer(), it.body).token }.getOrNull() }
        if (token.isNullOrBlank()) { onNotice(Notice.ENROLL_FAILED); return false }
        vault.put(token)
        degraded = false
        onNotice(Notice.ENROLLED)
        return true
    }

    suspend fun ask(system: String, user: String, names: Collection<String>, maxTokens: Int = 800): String? {
        if (!available || !enabled()) return null
        val token = if (viaGateway) vault.get() ?: return null else direct!!.key
        if (!online()) return fallBack(Notice.FALLBACK_OFFLINE)
        val mask = Pseudonymizer(names)
        val reply = withContext(Dispatchers.IO) {
            runCatching {
                if (viaGateway) http.call("POST", "$base/v1/assist", json.encodeToString(AssistReq.serializer(), AssistReq(system, mask.mask(user), maxTokens)), token)
                else http.call("POST", direct!!.url, json.encodeToString(ChatReq.serializer(), ChatReq(direct.model, listOf(Msg("system", system), Msg("user", mask.mask(user))), 0.2, maxTokens)), token)
            }.getOrNull()
        }
        // A revoked device stops trying until it is enrolled again.
        if (viaGateway && reply?.code == 401) { vault.clear(); return fallBack(Notice.REVOKED) }
        val text = reply?.takeIf { it.ok }?.let {
            runCatching {
                if (viaGateway) json.decodeFromString(AssistResp.serializer(), it.body).text.trim()
                else json.decodeFromString(ChatResp.serializer(), it.body).choices.first().message.content.trim()
            }.getOrNull()
        }
        if (text.isNullOrBlank()) return fallBack(Notice.FALLBACK_ERROR)
        if (degraded) { degraded = false; onNotice(Notice.BACK_ONLINE) }
        return mask.restore(text)
    }

    suspend fun report(sessionLog: String, role: String, dialect: String, names: Collection<String>, signLanguage: SignLanguage = SignLanguage.ISL): String =
        ask(REPORT_PROMPT, "Role: $role\nLanguage: $dialect / ${signLanguage.title}\nSession log:\n$sessionLog", names, 1200)
            ?: OfflineReport.synthesizeOfflineForensicReport(sessionLog, role, dialect, signLanguage)

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
        const val REPORT_PROMPT = "You are a trauma-informed child-protection police investigator. From the session log write a factual, " +
            "objective report with: 1 incident and timeline, 2 statements by the subject (exact words and gestures), " +
            "3 welfare needs, 4 persons mentioned, 5 reliability (non-leading check), 6 recommended police and CWC actions."
    }
}
