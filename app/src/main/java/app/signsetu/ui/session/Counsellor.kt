package app.signsetu.ui.session

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reason on a QUESTION_SHOWN row the AI counsellor wrote; the record and transcript name it. */
const val SHOWN_AI = "ai_counsellor"

/** One line the counsellor wants shown: [say] in plain English for the character, [caption] in the person's language. */
data class CounselLine(val say: String, val caption: String?, val handoff: Boolean)

sealed interface Counsel {
    data object Off : Counsel
    data object Thinking : Counsel
    /** Shown to the officer first; it goes to the stage on its own after [Counsellor.holdMs], or when they send it. */
    data class Holding(val line: CounselLine, val untilMs: Long) : Counsel
    data object Waiting : Counsel
    data class Paused(val why: Why) : Counsel
    enum class Why { STOP, HANDOFF, NO_REPLY, LIMIT }
}

/** The AI counsellor, on a paired second screen or this phone's stage. The officer's phone writes each turn
 * with the online assistant and shows it before it goes out; the officer can send it, skip it, or take over.
 * It starts only for a victim: the role must be set, and changing it away from victim stops the counsellor. */
class Counsellor(
    private val scope: CoroutineScope,
    private val ui: StateFlow<SessionUi?>,
    private val send: (CounselLine) -> Unit,
    private val holdMs: Long = 2_000,
    private val maxTurns: Int = 30,
) {
    private val state = MutableStateFlow<Counsel>(Counsel.Off)
    val counsel: StateFlow<Counsel> = state.asStateFlow()
    private var job: Job? = null
    private val decided = MutableStateFlow<Boolean?>(null)

    fun start(system: String, write: suspend (system: String, transcript: String) -> String?, transcript: (List<SessionEvent>) -> String) {
        job?.cancel()
        job = scope.launch {
            var turns = 0
            while (true) {
                if (turns++ >= maxTurns) { state.value = Counsel.Paused(Counsel.Why.LIMIT); return@launch }
                state.value = Counsel.Thinking
                val events = ui.value?.events.orEmpty()
                val line = write(system, transcript(events))?.let(::parseCounselLine)
                if (line == null) { state.value = Counsel.Paused(Counsel.Why.NO_REPLY); return@launch }
                decided.value = null
                state.value = Counsel.Holding(line, System.currentTimeMillis() + holdMs)
                // true: Send; false: Skip, and write another line. After the hold the line goes on its own, so the
                // conversation keeps its pace with the officer watching; Take over cancels the job outright.
                val go = withTimeoutOrNull(holdMs) { decided.first { it != null } } ?: true
                if (!go) continue
                val mark = events.maxOfOrNull { it.id } ?: 0L
                send(line)
                if (line.handoff) { state.value = Counsel.Paused(Counsel.Why.HANDOFF); return@launch }
                state.value = Counsel.Waiting
                val answer = ui.first { s -> s?.events.orEmpty().any { it.id > mark && it.type in AnswerEvents } }
                    ?.events?.firstOrNull { it.id > mark && it.type in AnswerEvents }
                if (answer?.cardId in PauseCards) { state.value = Counsel.Paused(Counsel.Why.STOP); return@launch }
                // A moment to read the answer before the next line arrives.
                delay(800)
            }
        }
    }

    fun sendNow() { decided.value = true }
    fun skip() { decided.value = false }

    fun takeOver() {
        job?.cancel()
        job = null
        state.value = Counsel.Off
    }
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

/** A reply cut off before its closing brace still carries the line; this reads it back out of the raw text. */
private val SAY_FIELD = Regex("\\\"say\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")

private fun unescapeJson(s: String): String = s.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\")

/** The model's JSON reply as one short line; null when it holds nothing usable. Only the first question is kept. */
fun parseCounselLine(raw: String): CounselLine? {
    val body = raw.substringAfter('{', "").substringBeforeLast('}', "").takeIf { it.isNotBlank() }?.let { "{$it}" }
    val obj = body?.let { runCatching { lenient.parseToJsonElement(it).jsonObject }.getOrNull() }
    val recovered = if (obj == null) SAY_FIELD.find(raw)?.groupValues?.get(1)?.let(::unescapeJson) else null
    val say = (obj?.get("say")?.jsonPrimitive?.content ?: recovered ?: raw.lineSequence().firstOrNull { it.isNotBlank() }).orEmpty().trim()
    val firstQuestion = say.indexOf('?').takeIf { it >= 0 }?.let { say.substring(0, it + 1) } ?: say
    val line = firstQuestion.trim().trim('"').take(MAX_SAY)
    if (line.isBlank() || line.startsWith("{")) return null
    val caption = obj?.get("caption")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != line }?.take(MAX_SAY * 2)
    return CounselLine(line, caption, obj?.get("handoff")?.jsonPrimitive?.booleanOrNull == true)
}

private const val MAX_SAY = 160

/** The exchange as a JSON chat history: the person's turns as user, the counsellor's as assistant, the
 * officer's as officer, so the model reads the conversation in order rather than as a wall of lines. */
fun counselHistory(events: List<SessionEvent>, content: ContentBundle): String = buildJsonArray {
    events.forEach { e ->
        val card = e.cardId?.let { id -> content.cards[id]?.label?.pick("en") ?: id }
        val turn = when {
            e.type == EventType.QUESTION_SHOWN -> (if (e.reason == SHOWN_AI) "assistant" else "officer") to e.text
            e.type == EventType.ANSWER_SIGNED -> "user" to signedWords(e.text)?.let { "(signed, unconfirmed) $it" }
            e.type in AnswerEvents && e.source == EventSource.SUBJECT || e.type in AnswerEvents && e.via != null ->
                "user" to listOfNotNull(card, e.text?.takeIf { it.isNotBlank() }).joinToString(" - ").ifEmpty { e.type.name.lowercase() }
            else -> null
        } ?: return@forEach
        add(buildJsonObject {
            put("role", turn.first)
            put("text", turn.second?.lineSequence()?.firstOrNull().orEmpty())
        })
    }
}.takeLast(40).toString()

/** The counsellor's instructions; [child] picks the simpler register. */
fun counselPrompt(languageName: String, child: Boolean): String = """
You are a gentle, trauma-informed helper talking with a ${if (child) "young child" else "person"} who may be frightened, possibly of the police. A police officer is watching every line and can take over.
The person answers on a phone with pictures, yes/no, writing, drawing or sign language.
The conversation so far arrives as a JSON array of turns: role "assistant" is you, "user" is the person, "officer" is the police officer in the room.
Rules:
- One short sentence or one short question per turn, at most 15 simple words.
- Say it the way a kind person actually speaks: plain, warm, flowing, contractions welcome. No headings, no lists, no brackets, and never explain what you are doing.
- Start by greeting warmly, saying you are a helper, they are safe, and they can stop any time.
- Acknowledge feelings before asking anything. Offer water, a break or a trusted person when they seem upset.
- Ask open questions only ("Can you tell me about...?", "What happened next?"). Never suggest a name, place, person, act or detail they have not given. Never ask a question that can be answered only yes or no about what happened.
- Never promise outcomes, never blame, never ask why they did something, never ask for proof.
- Never say you will fetch, bring or do anything yourself; say the officer will help with it.
- If they say they are in danger, hurt, or want to harm themselves, reassure them and set "handoff" true.
- If they ask to stop, thank them and set "handoff" true.
Reply with JSON only: {"say": "<English>", "caption": "<the same in $languageName>", "handoff": false}
""".trimIndent()
