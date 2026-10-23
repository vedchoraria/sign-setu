package app.signsetu.assistant

import app.signsetu.data.content.SignLanguage
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class AssistReq(val system: String, val user: String, val max_tokens: Int)
@Serializable private data class AssistResp(val text: String = "")
@Serializable private data class EnrollReq(val code: String)
@Serializable private data class EnrollResp(val token: String = "")
@Serializable private data class Msg(val role: String, val content: String)
@Serializable private data class Reasoning(val exclude: Boolean = true)
// A reasoning model otherwise answers with its own deliberation; OpenRouter then returns the answer alone.
@Serializable private data class ChatReq(val model: String, val messages: List<Msg>, val temperature: Double, val max_tokens: Int, val reasoning: Reasoning = Reasoning())
@Serializable private data class Choice(val message: Msg)
@Serializable private data class ChatResp(val choices: List<Choice> = emptyList())

/** A build that talks straight to an OpenAI-style service with its own key, when it has no gateway. The key is in the APK. */
class Direct(val url: String, val model: String, val key: String)

/** What the officer is told, once per change, when the online assistant stops or starts working. */
enum class Notice { FALLBACK_OFFLINE, FALLBACK_ERROR, REVOKED, BACK_ONLINE, ENROLLED, ENROLL_FAILED, ENROLL_RATE_LIMITED }

/** The online half of the assistant: the project gateway when the build has one (else a [Direct] service), the
 * phone is online, the officer has switched it on and, for the gateway, the device is enrolled. */
class CloudAi(
    private val enabled: suspend () -> Boolean,
    gatewayUrl: String,
    private val http: Http,
    private val vault: TokenVault,
    private val direct: Direct? = null,
    private val online: () -> Boolean,
) {
    // Defaults are encoded: a request option left at its default (reasoning excluded) must still reach the service.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    /** True when a request would actually leave the phone: the officer must have allowed that for the session. */
    suspend fun sendsSessionData(): Boolean = available && enabled() && withContext(Dispatchers.IO) { enrolled }

    /** Trades the department's one-time code for this device's token; false when it was refused or unreachable. */
    suspend fun enroll(code: String): Boolean {
        if (!viaGateway) return false
        if (!online()) { onNotice(Notice.ENROLL_FAILED); return false }
        val reply = withContext(Dispatchers.IO) {
            // Enrolment is a short exchange; a sleeping or wrong host should not hold the officer behind the dialog for a
            // whole chat timeout. A slow but working gateway still gets 15 s, and the officer can retry.
            runCatching {
                http.call("POST", "$base/v1/enroll", json.encodeToString(EnrollReq.serializer(), EnrollReq(code.trim())), null, ENROLL_TIMEOUT_MS)
            }.getOrNull()
        }
        if (reply?.code == 429) {
            // The gateway is refusing every code for a minute (often an attacker's wrong guesses), so a valid code
            // would fail too; telling the officer to wait is more useful than "check the code".
            onNotice(Notice.ENROLL_RATE_LIMITED)
            return false
        }
        val token = reply?.takeIf { it.ok }?.let { runCatching { json.decodeFromString(EnrollResp.serializer(), it.body).token }.getOrNull() }
        val stored = !token.isNullOrBlank() && withContext(Dispatchers.IO) { vault.put(token) }
        if (!stored) { onNotice(Notice.ENROLL_FAILED); return false }
        degraded = false
        onNotice(Notice.ENROLLED)
        return true
    }

    suspend fun ask(system: String, user: String, names: Collection<String>, maxTokens: Int = 800, details: Collection<String> = emptyList()): String? {
        if (!available || !enabled()) return null
        // Keystore decryption and the token file are disk work; never do them on the caller's UI thread.
        val token = if (viaGateway) withContext(Dispatchers.IO) { vault.get() } ?: return null else direct!!.key
        if (!online()) return fallBack(Notice.FALLBACK_OFFLINE)
        val mask = Pseudonymizer(names, details)
        suspend fun once(): Reply? = withContext(Dispatchers.IO) {
            runCatching {
                if (viaGateway) http.call("POST", "$base/v1/assist", json.encodeToString(AssistReq.serializer(), AssistReq(system, mask.mask(user), maxTokens)), token)
                // A reasoning model spends the first tokens on thinking, so a short reply budget is all thinking
                // and no answer; the floor leaves room for the answer itself. Output stays as short as it likes.
                else http.call("POST", direct!!.url, json.encodeToString(ChatReq.serializer(), ChatReq(direct.model, listOf(Msg("system", system), Msg("user", mask.mask(user))), 0.2, maxOf(maxTokens, DIRECT_MIN_TOKENS))), token)
            }.getOrNull()
        }
        // A busy free service sometimes answers with nothing; one retry keeps a conversation going.
        var reply = once()
        if (reply?.ok != true) {
            delay(if (reply?.code == 429) 6_000 else 1_500)
            reply = once()
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

    suspend fun report(sessionLog: String, role: String, dialect: String, names: Collection<String>, signLanguage: SignLanguage = SignLanguage.ISL, details: Collection<String> = emptyList()): String =
        ask(REPORT_PROMPT, "Role: $role\nLanguage: $dialect / ${signLanguage.title}\nSession log:\n$sessionLog", names, 1200, details)
            ?: OfflineReport.synthesizeOfflineForensicReport(sessionLog, role, dialect, signLanguage)

    suspend fun followUps(lastAnswer: String, isChild: Boolean, names: Collection<String>, details: Collection<String> = emptyList()): List<String> {
        val raw = ask(followUpPrompt(followUpTone(isChild)), "Latest answer: $lastAnswer", names, 300, details)
        val parsed = raw?.lines().orEmpty().map { it.trim().removePrefix("-").removePrefix("*").trim() }.filter { it.endsWith("?") }
        return parsed.ifEmpty { OfflineReport.offlineTraumaFollowUps(lastAnswer, isChild) }
    }

    private companion object {
        const val ENROLL_TIMEOUT_MS = 15_000

        /** Enough for a reasoning model's thinking plus the short answer the app asks for; the thinking grows with the
         * conversation, so this leaves twice the room a short turn needs. */
        const val DIRECT_MIN_TOKENS = 2_500
    }
}

internal const val REPORT_PROMPT = "You are a trauma-informed child-protection police investigator. From the session log write a factual, " +
    "objective report with: 1 incident and timeline, 2 statements by the subject (exact words and gestures), " +
    "3 welfare needs, 4 persons mentioned, 5 reliability (non-leading check), 6 recommended police and CWC actions. " +
    "Use only what the session log records. Never invent names, officers, places, dates, times, case numbers or events; " +
    "write Not recorded where the log has nothing. Keep the masked names exactly as given."

internal fun followUpTone(child: Boolean) =
    if (child) "a young traumatised child: short, gentle, simple" else "a vulnerable adult: factual, calm, no pressure"

internal fun followUpPrompt(tone: String) =
    "Propose 2 to 3 non-leading follow-up questions for $tone. One question per line starting with '-', no preamble."
