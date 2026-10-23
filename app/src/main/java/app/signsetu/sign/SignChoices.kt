package app.signsetu.sign

import androidx.compose.runtime.Immutable
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.WordEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.hypot

/** Below this the reader's best guess is treated as a failed read. */
const val READ_MIN_SCORE = 0.4f

/** Wrist speed, in frame widths per second, above which the hands move too fast for the reader. */
const val FAST_HANDS = 2.5f

/** Share of a sign's time with no hand tracked above which tracking is treated as lost. */
const val LOST_SHARE = 0.3f

/** A gap between analysed frames longer than this means the tracker lost the person. */
const val LOST_GAP_MS = 150L

/** Reads assets/content/sign_words.json: the reader's labels to a written form in words.json. */
fun parseSignWords(json: String): Map<String, String> =
    Json.parseToJsonElement(json).jsonObject.getValue("words").jsonObject.mapValues { it.value.jsonPrimitive.content }

/** The optional "cards" object of sign_words.json: labels to the further picture cards they answer. */
fun parseSignCards(json: String): Map<String, List<String>> =
    Json.parseToJsonElement(json).jsonObject["cards"]?.jsonObject?.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }.orEmpty()

/** The app card a reader label stands for, if the two vocabularies share the concept. */
fun cardFor(label: String, signWords: Map<String, String>, words: Map<String, WordEntry>): String? =
    signWords[label]?.let { words[it]?.card }

/** Every card a label answers: its word's card, then those listed in [extra]. */
fun cardsFor(label: String, signWords: Map<String, String>, words: Map<String, WordEntry>, extra: Map<String, List<String>> = emptyMap()) =
    listOfNotNull(cardFor(label, signWords, words)) + extra[label].orEmpty()

/** Guesses whose concept answers the item on screen first, each group by score; none are dropped. */
fun inContext(
    guesses: List<SignGuess>,
    context: Set<String>,
    signWords: Map<String, String>,
    words: Map<String, WordEntry>,
    extra: Map<String, List<String>> = emptyMap(),
): List<SignGuess> {
    if (context.isEmpty()) return guesses
    return guesses.sortedWith(
        compareByDescending<SignGuess> { g -> cardsFor(g.word, signWords, words, extra).any { it in context } }
            .thenByDescending { it.score }
    )
}

/** One alternative the subject can pick: a picture card, or the officer's word when it has no card. */
@Immutable
data class SignChoice(val card: String?, val text: String)

/** The signs whose meaning the subject is choosing, one at a time; [eventId] is the signed answer they came
 * from. [read] is the reader's top label for each, signed back on the character. */
@Immutable
data class SignConfirm(val eventId: Long, val queue: List<List<SignChoice>>, val read: List<String?> = emptyList())

/** Two or three alternatives for one sign, picked in this priority: the officer's reading, the reader's
 * guesses, then the item's own answer cards. Fewer than two is no choice at all, so none are offered. */
fun signChoices(
    officerWord: String?,
    segment: SignSegment,
    signWords: Map<String, String>,
    words: Map<String, WordEntry>,
    max: Int = 3,
    extra: Map<String, List<String>> = emptyMap(),
): List<SignChoice> {
    val officer = officerWord?.trim()?.takeIf { it.isNotEmpty() && it != "?" }?.let { w -> SignChoice(words[w.lowercase()]?.card, w) }
    val machine = segment.guesses.orEmpty().mapNotNull { g ->
        val cards = cardsFor(g.word, signWords, words, extra)
        (cards.firstOrNull { it in segment.context } ?: cards.firstOrNull())?.let { SignChoice(it, g.word) }
    }
    val items = segment.context.map { SignChoice(it, it) }
    val all = (listOfNotNull(officer) + machine + items).distinctBy { it.card ?: it.text }.take(max)
    return all.takeIf { it.size >= 2 }.orEmpty()
}

/** A failed read of hands that moved too fast or were lost: the only time the subject is asked to sign slower. */
fun needsSlower(frames: List<LandmarkFrame>, guesses: List<SignGuess>): Boolean =
    (guesses.firstOrNull()?.score ?: 0f) < READ_MIN_SCORE && (trackingLost(frames) || tooFast(frames) || poorPicture(frames))

internal fun trackingLost(frames: List<LandmarkFrame>): Boolean {
    if (frames.size < 2) return true
    val span = (frames.last().timestampMs - frames.first().timestampMs).coerceAtLeast(1)
    val lost = frames.zipWithNext().sumOf { (a, b) ->
        val gap = b.timestampMs - a.timestampMs
        if (gap > LOST_GAP_MS || b.hands.isEmpty()) gap else 0L
    }
    return lost.toFloat() / span > LOST_SHARE
}

internal fun tooFast(frames: List<LandmarkFrame>): Boolean = frames.zipWithNext().any { (a, b) ->
    val seconds = (b.timestampMs - a.timestampMs) / 1000f
    seconds > 0 && listOf(a.leftHand to b.leftHand, a.rightHand to b.rightHand).any { (p, q) ->
        p != null && q != null && hypot(q[0].x - p[0].x, (q[0].y - p[0].y) / b.aspect) / seconds > FAST_HANDS
    }
}
