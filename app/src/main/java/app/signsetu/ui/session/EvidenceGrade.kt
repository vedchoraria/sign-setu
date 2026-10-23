package app.signsetu.ui.session

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent

/** Whose meaning an answer line carries. Only the subject's own answers and confirmations are theirs. */
enum class Grade { SAID, CONFIRMED, OFFICER_READ, MACHINE_READ, SAID_NO, UNCLEAR }

/** A say-back prompt or answer points at the line it checks with this prefix in its lexicon id. */
const val REF_PREFIX = "ref:"

fun refOf(e: SessionEvent): Long? = e.lexiconId?.takeIf { it.startsWith(REF_PREFIX) }?.removePrefix(REF_PREFIX)?.toLongOrNull()

/** Grades every answer line; a say-back answer regrades the line it checked, and the latest one wins. */
fun grades(events: List<SessionEvent>): Map<Long, Grade> {
    val out = mutableMapOf<Long, Grade>()
    for (e in events) {
        if (e.type !in AnswerEvents) continue
        val base = when {
            e.source == EventSource.MACHINE -> Grade.MACHINE_READ
            // The officer closed an item the subject left unanswered: no reading, just no answer.
            e.type == EventType.ANSWER_UNCLEAR && e.source == EventSource.OFFICER -> Grade.UNCLEAR
            e.source != EventSource.SUBJECT -> Grade.OFFICER_READ
            e.type == EventType.ANSWER_SIGNED -> Grade.MACHINE_READ
            e.type == EventType.ANSWER_GESTURE -> Grade.CONFIRMED
            e.type == EventType.ANSWER_UNCLEAR -> if (refOf(e) != null && e.cardId == "no") Grade.SAID_NO else Grade.UNCLEAR
            else -> Grade.SAID
        }
        out[e.id] = base
        val ref = refOf(e) ?: continue
        if (ref in out && (base == Grade.CONFIRMED || base == Grade.SAID_NO)) out[ref] = base
    }
    return out
}

/** Lines whose meaning the subject has not yet confirmed or rejected can be said back to them. */
fun canSayBack(e: SessionEvent, grade: Grade?): Boolean =
    grade != null && grade != Grade.CONFIRMED && e.type != EventType.LANGUAGE_CHOSEN && refOf(e) == null
