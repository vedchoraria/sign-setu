package app.signsetu.ui.session

import android.content.res.Resources
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent

fun Grade.label() = when (this) {
    Grade.SAID -> R.string.grade_said
    Grade.CONFIRMED -> R.string.grade_confirmed
    Grade.OFFICER_READ -> R.string.grade_officer_read
    Grade.MACHINE_READ -> R.string.grade_machine_read
    Grade.SAID_NO -> R.string.grade_said_no
    Grade.UNCLEAR -> R.string.grade_unclear
}

/** Readings proposed by the app or the officer, kept apart from the subject's own answers in every export. */
val Grade.isReading: Boolean get() = this == Grade.OFFICER_READ || this == Grade.MACHINE_READ

private fun seconds(ms: Long) = (ms / 1000).toInt()

fun durationText(res: Resources, ms: Long): String =
    seconds(ms).let { s -> if (s < 60) res.getString(R.string.trail_seconds, s) else res.getString(R.string.trail_minutes, s / 60, s % 60) }

fun trailText(res: Resources, t: Trail): String = listOfNotNull(
    if (t.changed) res.getString(R.string.trail_changed, t.answers.joinToString(" → "), seconds(t.finalAfterMs))
    else res.getString(R.string.trail_answered, seconds(t.latencyMs)),
    t.pauseMs?.let { res.getString(R.string.trail_paused, durationText(res, it)) },
).joinToString("; ")

fun promptText(res: Resources, content: ContentBundle, lang: String, r: PromptRecord): String {
    val none = res.getString(R.string.prompt_none)
    return res.getString(
        R.string.prompt_detail,
        r.caption?.replace('\n', ' ') ?: none,
        r.motions.joinToString().ifEmpty { none },
        r.cards.joinToString { content.cards[it]?.label?.pick(lang) ?: it }.ifEmpty { none },
        r.content,
    )
}

fun systemText(res: Resources, text: String?): String = when {
    text == SYSTEM_LANGUAGE_FINDER -> res.getString(R.string.officer_find_language)
    text == SYSTEM_DRAWING -> res.getString(R.string.officer_draw)
    text == SYSTEM_READ_SIGNS -> res.getString(R.string.officer_read_signs)
    text == SYSTEM_TELL_ME -> res.getString(R.string.tell_me)
    text == SYSTEM_BREAK_GIVEN -> res.getString(R.string.log_break_given)
    text == SYSTEM_JJ_NOTICE -> res.getString(R.string.log_jj_notice)
    text == SYSTEM_JUVENILE -> res.getString(R.string.log_juvenile)
    text == SYSTEM_JUVENILE_OFF -> res.getString(R.string.log_juvenile_off)
    text != null && text.startsWith(SYSTEM_SIGN_LANGUAGE) -> res.getString(R.string.log_sign_language, text.removePrefix(SYSTEM_SIGN_LANGUAGE))
    text != null && text.startsWith(SYSTEM_RUN_ENDED) -> text.removePrefix(SYSTEM_RUN_ENDED).split('/').mapNotNull { it.toIntOrNull() }
        .takeIf { it.size == 2 }?.let { (done, total) -> res.getString(R.string.run_ended, done, total) } ?: text
    else -> stageSystemText(res, text) ?: autoKeyLabel(text)?.let(res::getString) ?: text.orEmpty()
}

/**
 * The layered record line for each row: a prompt row reads as exactly what the subject saw; an answer row carries
 * its grade, the place of the picture chosen, and its timing and change trail.
 */
fun recordDetail(res: Resources, content: ContentBundle, lang: String, events: List<SessionEvent>, base: (SessionEvent) -> String): (SessionEvent) -> String {
    val grades = grades(events)
    val trails = answerTrails(events)
    val positions = choicePositions(events)
    return { e ->
        if (e.type == EventType.PROMPT) {
            decodePrompt(e.text)?.let { promptText(res, content, lang, it) }.orEmpty()
        } else {
            listOfNotNull(
                grades[e.id]?.let { "[${res.getString(it.label())}]" },
                base(e),
                givenText(res, e),
                cueText(res, e),
                positions[e.id]?.let { (n, of) -> "(${res.getString(R.string.choice_position, n, of)})" },
                trails[e.id]?.let { "· ${trailText(res, it)}" },
            ).joinToString(" ")
        }
    }
}

/** How a tapped answer followed a gesture the app proposed (D-40), e.g. "(after a nod or thumbs up)". */
fun cueText(res: Resources, e: SessionEvent): String? = when (e.reason?.takeIf { e.type == EventType.ANSWER_CARD }) {
    "cue:nod" -> R.string.cue_nod
    "cue:shake" -> R.string.cue_shake
    "cue:point" -> R.string.cue_point
    "cue:palm" -> R.string.cue_palm
    "cue:shrug" -> R.string.cue_shrug
    else -> null
}?.let { "(" + res.getString(it) + ")" }
