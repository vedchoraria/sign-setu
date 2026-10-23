package app.signsetu.ui.session

import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import kotlin.random.Random

const val SOMETHING_ELSE = "something_else"
const val DONT_UNDERSTAND = "dont_understand"

/** Cards that are answers in themselves; a set that has them is a scale or a yes/no, not a picture choice. */
private val AnswerCards = setOf("yes", "no", "dont_know", "stop", SOMETHING_ELSE, DONT_UNDERSTAND)
private val NUMBER = Regex("n\\d+")

/** Picture choices in a fresh random order, so the first or last picture is not favoured, with ways out that do
 * not force a choice. "I don't know" and "I don't understand" are always on the stage's answer strip. */
fun offered(cards: List<String>, random: Random): List<String> {
    val pictures = cards.filter { it != "draw" }
    if (pictures.size < 2 || "draw" in cards || pictures.any { it in AnswerCards || NUMBER.matches(it) }) return cards
    return pictures.shuffled(random).take(4) + SOMETHING_ELSE
}

/** The pictures of [all] that [shown] left out, with a way out again when only one is left; null when none were. */
fun unseen(all: List<String>, shown: List<String>): List<String>? {
    if (SOMETHING_ELSE !in shown || SOMETHING_ELSE in all) return null
    val rest = all.filter { it !in shown }
    return when (rest.size) { 0 -> null; 1 -> rest + SOMETHING_ELSE; else -> rest }
}

/** The pictures a show-back or say-back offers: the reading under check and up to two others from [pool]. A
 * single picture is no choice, so then only the reading comes back and a Yes to it confirms nothing. */
fun checkChoices(meant: String?, pool: List<String>, max: Int = 3): List<String> {
    meant ?: return emptyList()
    val others = pool.filter { it != meant && it != "draw" && it !in AnswerCards && !NUMBER.matches(it) }.distinct()
    return listOf(meant) + others.take(max - 1)
}

/** For each answer to a shuffled set, its 1-based place among the pictures offered and how many there were. */
fun choicePositions(events: List<SessionEvent>): Map<Long, Pair<Int, Int>> {
    val records = prompts(events)
    var cards: List<String> = emptyList()
    val out = mutableMapOf<Long, Pair<Int, Int>>()
    for (e in events) {
        when (e.type) {
            EventType.QUESTION_SHOWN -> cards = records[e.id]?.cards.orEmpty()
            EventType.SYSTEM -> cards = emptyList()
            EventType.ANSWER_CARD, EventType.ANSWER_GESTURE -> {
                val i = cards.indexOf(e.cardId)
                if (i >= 0 && SOMETHING_ELSE in cards) out[e.id] = (i + 1) to cards.size
            }
            else -> Unit
        }
    }
    return out
}
