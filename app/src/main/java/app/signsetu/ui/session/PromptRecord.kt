package app.signsetu.ui.session

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.planText
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Exactly what the subject was shown for one prompt: the caption in their language, the motions the character
 * played, the pictures in the order offered, and the content they came from. */
@Serializable
data class PromptRecord(
    val caption: String? = null,
    val motions: List<String> = emptyList(),
    val cards: List<String> = emptyList(),
    val content: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

fun PromptRecord.encode(): String = json.encodeToString(PromptRecord.serializer(), this)

fun decodePrompt(text: String?): PromptRecord? = text?.let { runCatching { json.decodeFromString(PromptRecord.serializer(), it) }.getOrNull() }

/** Mirrors what the stage plays for each item (Stage.kt), so the record names what the subject actually saw. */
fun ContentBundle.promptFor(pending: Pending, lang: String): PromptRecord {
    val (caption, motions, cards) = when (pending) {
        is Pending.Ask -> Triple(pending.question.text.get(lang), listOfNotNull(pending.question.gesture ?: pending.question.lexicon), pending.question.cards)
        is Pending.FreeText -> Triple(
            pending.caption ?: pending.text,
            if (pending.asksChoice) listOf("ask") else planText(pending.text, pending.modes).motions,
            pending.cardIds,
        )
        is Pending.Cards -> Triple(null, listOf("ask"), pending.cardIds)
        is Pending.ShowBack -> if (pending.asksChoice) {
            Triple(subjectText("is_this_it", lang), listOf("ask"), pending.choices)
        } else {
            Triple(subjectText("is_this_it", lang), listOf(pending.entry.id), listOf(pending.entry.card))
        }
        is Pending.PhraseAsk -> Triple(
            pending.slot.text.get(lang),
            pending.phrase.signs.mapNotNull { if (it == Phrase.SLOT) pending.slot.sign else it },
            listOf(pending.slot.card),
        )
        Pending.LanguageFinder -> Triple(null, listOf("wave"), emptyList())
        Pending.Drawing -> Triple(subjectText("draw", lang), emptyList(), emptyList())
        is Pending.GroundRules -> ruleStep(pending).let { r -> Triple(r?.let { ruleText(this, it.key, lang) }, listOfNotNull(r?.gesture), listOfNotNull(r?.card)) }
    }
    return PromptRecord(caption, motions.filter { it in gestures }, cards, version)
}

/** Each prompt record by the id of the QUESTION_SHOWN or SYSTEM row it belongs to. */
fun prompts(events: List<SessionEvent>): Map<Long, PromptRecord> {
    val out = mutableMapOf<Long, PromptRecord>()
    var shown: Long? = null
    for (e in events) {
        when (e.type) {
            EventType.QUESTION_SHOWN, EventType.SYSTEM -> shown = e.id
            EventType.PROMPT -> shown?.let { id -> decodePrompt(e.text)?.let { out[id] = it } }
            else -> Unit
        }
    }
    return out
}
