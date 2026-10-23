package app.signsetu.ui.session

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionEvent

/** Auto mode's sequencer: a pure function of the session log, the bank and the role. It only picks the next
 * reviewed item in welfare-triage order. */
enum class Reason { OPENING, NEXT_PHASE, FOLLOWS_ANSWER, BREAK_AFTER_UNSURE, CARRY_ON, CLOSING }

data class Proposal(val q: Question, val reason: Reason)

/** Auto never proposes free recall, follow-up or rights items: those stay the officer's own choice. */
val AUTO_PHASES = setOf(Phase.RAPPORT, Phase.GROUND_RULES, Phase.WELFARE, Phase.PRACTICAL, Phase.BREAK, Phase.CLOSING)
private val IN_ORDER = listOf(Phase.RAPPORT, Phase.GROUND_RULES, Phase.WELFARE, Phase.PRACTICAL, Phase.CLOSING)

/** Machine keys of auto mode's SYSTEM events. */
object AutoKey {
    const val MODE_AUTO = "mode_auto"
    const val MODE_MANUAL = "mode_manual"
    const val MODE_MANUAL_SUBSTANTIVE = "mode_manual_substantive"
    const val PAUSED = "auto_paused"
    const val RESUMED = "auto_resumed"
    const val WRAP_UP = "auto_wrap_up"
    const val ENDED_STOP = "auto_ended_stop"
    const val ADVANCE_ON = "auto_advance_on"
    const val ADVANCE_OFF = "auto_advance_off"
    const val BREAK = "auto_break"
}

/** Reasons a QUESTION_SHOWN row carries. */
const val SHOWN_RIGHTS = "rights_shown"
const val SHOWN_CAPTION_MISSING = "caption_missing"
/** A step of the stage's own opening (GroundRules.kt), which already covers the ground rules. */
const val SHOWN_OPENING = "opening"
/** The only skip that drops an item for the session; choosing something else or switching mode does not. */
const val SKIP_OFFICER = "officer"

private const val K_BREAK = "k1"
private const val K_SLOW = "k2"
private const val K_CARRY_ON = "k3"

private val UnsureCards = setOf("dont_know", "dont_understand")

fun SessionEvent.isStop() = cardId == "stop"
fun SessionEvent.isUnsure() = cardId in UnsureCards || type == EventType.ANSWER_UNCLEAR

/** A rights item is recorded as shown, never as understood; otherwise the row notes a missing caption. */
fun shownReason(content: ContentBundle, q: Question, lang: String): String? = when {
    content.isRights(q) -> SHOWN_RIGHTS
    q.text[lang] == null -> SHOWN_CAPTION_MISSING
    else -> null
}

fun next(content: ContentBundle, role: Role, events: List<SessionEvent>): Proposal? = candidates(content, role, events).firstOrNull()

/** Every item auto mode may propose now, best first (R1 to R8). */
fun candidates(content: ContentBundle, role: Role, events: List<SessionEvent>): List<Proposal> {
    val log = SessionLog(content, events)
    val allowed = content.groupsFor(role).filterNot { it.sensitive || it.rights }.map { it.id }.toSet()
    val usable = content.questions.filter { q -> q.phase in AUTO_PHASES && q.group in allowed && !log.endedUnsure(q.id) }
    val phases = when {
        events.any { it.type == EventType.SYSTEM && it.text == AutoKey.WRAP_UP } -> listOf(Phase.CLOSING)
        events.any { it.reason == SHOWN_OPENING } && events.none { it.type == EventType.SYSTEM && it.text == SYSTEM_RULES_SKIPPED } ->
            IN_ORDER - Phase.GROUND_RULES
        else -> IN_ORDER
    }

    val pool = usable
        .filter { q -> q.phase in phases && !log.shown(q.id) && q.id !in log.skipped && log.afterHolds(q) }
        .sortedWith(compareBy({ log.avoidOption(it) }, { IN_ORDER.indexOf(it.phase) }))
    val breakItems = usable.filter { it.phase == Phase.BREAK }.associateBy { it.id }

    val out = mutableListOf<Proposal>()
    // R4b: after a resume, ask first whether to carry on.
    if (log.justResumed()) breakItems[K_CARRY_ON]?.let { out += Proposal(it, Reason.CARRY_ON) }
    // R3: two unsure answers in a row: offer a break.
    if (log.twoUnsure()) (breakItems[K_BREAK] ?: breakItems[K_SLOW])?.let { out += Proposal(it, Reason.BREAK_AFTER_UNSURE) }
    // R6: a follow-up whose condition holds goes before the rest.
    pool.filter { it.after != null }.forEach { out += Proposal(it, Reason.FOLLOWS_ANSWER) }
    pool.filter { it.after == null }.forEach { q ->
        out += Proposal(q, when (q.phase) {
            Phase.RAPPORT -> Reason.OPENING
            Phase.CLOSING -> Reason.CLOSING
            else -> Reason.NEXT_PHASE
        })
    }
    return out.distinctBy { it.q.id }
}

/** "Welfare · step 3 of 10": items of [q]'s phase already shown, and how many there are for this person. */
fun phaseStep(content: ContentBundle, role: Role, events: List<SessionEvent>, q: Question): Pair<Int, Int> {
    val log = SessionLog(content, events)
    val allowed = content.groupsFor(role).map { it.id }.toSet()
    val inPhase = content.questions.filter { it.phase == q.phase && it.group in allowed }
    val shown = inPhase.count { log.shown(it.id) }
    return (shown + 1).coerceAtMost(inPhase.size) to inPhase.size
}

/** A statement may follow a statement on the subject's own Next tap, three at most in a row; any question hands back. */
fun autoAdvanceAllowed(from: Question, answer: SessionEvent?, to: Question, subjectLang: String, chained: Int): Boolean =
    from.kind == Kind.STATEMENT && to.kind == Kind.STATEMENT &&
        (answer == null || !(answer.isStop() || answer.isUnsure() || answer.type == EventType.ANSWER_SIGNED)) &&
        to.text[subjectLang] != null && chained < 3

/** The last answer to the last bank item shown, and that item: what auto mode reacts to. */
fun lastShownAnswer(content: ContentBundle, events: List<SessionEvent>): Pair<Question, SessionEvent?>? {
    val log = SessionLog(content, events)
    val row = log.rows.lastOrNull() ?: return null
    val q = content.question(row.itemId ?: return null) ?: return null
    return q to log.answers(row).lastOrNull()
}

private class SessionLog(val content: ContentBundle, val events: List<SessionEvent>) {
    val rows = events.filter { it.type == EventType.QUESTION_SHOWN && it.itemId != null }
    val skipped = events.filter { it.type == EventType.SKIPPED && it.reason == SKIP_OFFICER }.mapNotNull { it.itemId }.toSet()
    private val answersByShown = events.filter { it.type in AnswerEvents && it.shownId != null }.groupBy { it.shownId }

    fun shown(id: String) = rows.any { it.itemId == id }
    fun answers(row: SessionEvent) = answersByShown[row.id].orEmpty()
    private fun question(row: SessionEvent) = row.itemId?.let(content::question)

    /** No answer counts as unsure, except for a statement, which asks for none. */
    fun unsure(row: SessionEvent): Boolean {
        val last = answers(row).lastOrNull() ?: return question(row)?.kind != Kind.STATEMENT
        return last.isUnsure()
    }

    /** R1: an item last answered unsure or Stop is never offered again. */
    fun endedUnsure(id: String): Boolean {
        val row = rows.lastOrNull { it.itemId == id } ?: return false
        return unsure(row) || answers(row).lastOrNull()?.isStop() == true
    }

    /** R2: after an unsure answer to an option item, other options from its group wait. */
    fun avoidOption(q: Question): Boolean {
        val row = rows.lastOrNull() ?: return false
        val last = question(row) ?: return false
        return last.kind == Kind.OPTION && q.kind == Kind.OPTION && q.group == last.group && unsure(row)
    }

    /** R6: "s2:yes" holds when s2's latest answer is yes and was never changed; "any" takes any sure answer. */
    fun afterHolds(q: Question): Boolean {
        val (ref, want) = q.after?.split(':')?.takeIf { it.size == 2 } ?: return q.after == null
        val row = rows.lastOrNull { it.itemId == ref } ?: return false
        val answers = answers(row)
        val last = answers.lastOrNull() ?: return false
        if (answers.map { it.type to it.cardId }.distinct().size > 1) return false
        return if (want == "any") !last.isUnsure() && !last.isStop() else last.cardId == want
    }

    fun justResumed(): Boolean {
        val resumed = events.indexOfLast { it.type == EventType.SYSTEM && it.text == AutoKey.RESUMED }
        return resumed >= 0 && events.drop(resumed).none { it.type == EventType.QUESTION_SHOWN }
    }

    /** R3, once per run of unsure answers: a break item already shown after them ends the run. */
    fun twoUnsure(): Boolean {
        val recent = rows.takeLast(2)
        return recent.size == 2 && recent.none { question(it)?.phase == Phase.BREAK } && recent.all(::unsure)
    }
}
