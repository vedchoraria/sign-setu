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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reason on a QUESTION_SHOWN row the AI counsellor wrote; the record and transcript name it. */
const val SHOWN_AI = "ai_counsellor"

/** One line the counsellor wants shown: [say] in plain English for the character, [caption] in the person's language. */
data class CounselLine(val say: String, val caption: String?, val handoff: Boolean)

sealed interface Counsel {
    data object Off : Counsel
    data object Thinking : Counsel
    /** Shown to the officer first; it goes to the stage at [untilMs] unless they act. */
    data class Holding(val line: CounselLine, val untilMs: Long) : Counsel
    data object Waiting : Counsel
    data class Paused(val why: Why) : Counsel
    enum class Why { STOP, HANDOFF, NO_REPLY, LIMIT }
}

/**
 * The AI counsellor on a paired second screen. The officer's phone writes each turn with the online assistant, holds
 * it for the officer for [holdMs], then shows it; the person's answer starts the next turn. The officer can send at
 * once, skip a line, or take over at any time; Stop, a break or a flagged risk hands the session back to them.
 */
class Counsellor(
    private val scope: CoroutineScope,
    private val ui: StateFlow<SessionUi?>,
    private val send: (CounselLine) -> Unit,
    private val holdMs: Long = 6_000,
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
                // null: the hold ran out; true: Send now; false: Skip, and write another line.
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

/** The model's JSON reply as one short line; null when it holds nothing usable. Only the first question is kept. */
fun parseCounselLine(raw: String): CounselLine? {
    val body = raw.substringAfter('{', "").substringBeforeLast('}', "").takeIf { it.isNotBlank() }?.let { "{$it}" }
    val obj = body?.let { runCatching { lenient.parseToJsonElement(it).jsonObject }.getOrNull() }
    val say = (obj?.get("say")?.jsonPrimitive?.content ?: raw.lineSequence().firstOrNull { it.isNotBlank() }).orEmpty().trim()
    val firstQuestion = say.indexOf('?').takeIf { it >= 0 }?.let { say.substring(0, it + 1) } ?: say
    val line = firstQuestion.trim().trim('"').take(MAX_SAY)
    if (line.isBlank() || line.startsWith("{")) return null
    val caption = obj?.get("caption")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != line }?.take(MAX_SAY * 2)
    return CounselLine(line, caption, obj?.get("handoff")?.jsonPrimitive?.booleanOrNull == true)
}

private const val MAX_SAY = 160

/** What happened so far, as the counsellor reads it: who said what, with pictures named in English. */
fun counselTranscript(events: List<SessionEvent>, content: ContentBundle): String = events.mapNotNull { e ->
    val card = e.cardId?.let { id -> content.cards[id]?.label?.pick("en") ?: id }
    when {
        e.type == EventType.QUESTION_SHOWN -> "${if (e.reason == SHOWN_AI) "Counsellor" else "Officer"}: ${e.text?.lineSequence()?.firstOrNull().orEmpty()}"
        e.type == EventType.ANSWER_SIGNED -> signedWords(e.text)?.let { "Person (signed, unconfirmed): $it" }
        e.type in AnswerEvents && e.source == EventSource.SUBJECT || e.type in AnswerEvents && e.via != null ->
            "Person: ${listOfNotNull(card, e.text?.takeIf { it.isNotBlank() }).joinToString(" - ").ifEmpty { e.type.name.lowercase() }}"
        else -> null
    }
}.takeLast(40).joinToString("\n")

/** The counsellor's instructions; [child] picks the simpler register. */
fun counselPrompt(languageName: String, child: Boolean): String = """
You are a gentle, trauma-informed helper talking with a ${if (child) "young child" else "person"} who may be frightened, possibly of the police. A police officer is watching every line and can take over.
The person answers on a phone with pictures, yes/no, writing, drawing or sign language.
Rules:
- One short sentence or one short question per turn, at most 15 simple words.
- Start by greeting warmly, saying you are a helper, they are safe, and they can stop any time.
- Acknowledge feelings before asking anything. Offer water, a break or a trusted person when they seem upset.
- Ask open questions only ("Can you tell me about...?", "What happened next?"). Never suggest a name, place, person, act or detail they have not given. Never ask a question that can be answered only yes or no about what happened.
- Never promise outcomes, never blame, never ask why they did something, never ask for proof.
- If they say they are in danger, hurt, or want to harm themselves, reassure them and set "handoff" true.
- If they ask to stop, thank them and set "handoff" true.
Reply with JSON only: {"say": "<English>", "caption": "<the same in $languageName>", "handoff": false}
""".trimIndent()
