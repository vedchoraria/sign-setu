package app.signsetu.ui.session

import android.content.res.Resources
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Method
import app.signsetu.data.content.givenOf
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportSection

/** One personal detail the subject gave, as the report lists it. */
data class Detail(val field: String, val value: String, val method: Method, val confirmed: Boolean)

/** Taps that are not a detail: the ways out of a question, and a Yes or No to an open one. */
private val NotDetails = setOf("stop", "break", DONT_UNDERSTAND, "wrong", "yes", "no", SOMETHING_ELSE)

/** Every answer the subject gave to a question with an answer type, in order. A reading the officer said back
 * counts only once the subject confirmed it, and then as signed and confirmed. */
fun personalDetails(events: List<SessionEvent>, content: ContentBundle, lang: String): List<Detail> {
    val byId = events.associateBy { it.id }
    fun field(itemId: String?) = itemId?.let(content::question)?.takeIf { it.answer != null }
        ?.let { q -> q.field?.get(lang) ?: q.field?.get("en") ?: q.text.get(lang) ?: q.id }
    return events.filter { it.source == EventSource.SUBJECT }.mapNotNull { e ->
        val given = givenOf(e.reason)
        val ref = refOf(e)
        when {
            given != null -> field(e.itemId)?.let { Detail(it, e.text.orEmpty(), given.first, given.second) }
            ref != null -> {
                val original = byId[ref]?.takeIf { it.type == EventType.ANSWER_SIGNED } ?: return@mapNotNull null
                val agreed = e.type == EventType.ANSWER_GESTURE || (e.type == EventType.ANSWER_CARD && e.cardId == "yes")
                val said = byId[e.shownId]?.text
                if (agreed && said != null) field(byId[original.shownId]?.itemId)?.let { Detail(it, said, Method.SIGN, true) } else null
            }
            e.type == EventType.ANSWER_TEXT -> field(byId[e.shownId]?.itemId)?.let { Detail(it, e.text.orEmpty(), Method.WRITTEN, false) }
            e.type == EventType.ANSWER_CARD && e.cardId !in NotDetails ->
                field(byId[e.shownId]?.itemId)?.let { Detail(it, e.text.orEmpty(), Method.PICTURE, false) }
            else -> null
        }
    }
}

/** Names the subject gave (own, parents, teacher), for masking before any text leaves the phone. */
fun subjectNames(events: List<SessionEvent>, content: ContentBundle, lang: String): List<String> =
    personalDetails(events, content, lang).filter { it.field.contains("name", ignoreCase = true) || it.field.contains("नाम") }
        .map { it.value }.filter { it.isNotBlank() }

/** Other details the subject wrote or chose, such as a village or school; masked too. Pictures and short numbers are not. */
fun subjectDetails(events: List<SessionEvent>, content: ContentBundle, lang: String): List<String> =
    personalDetails(events, content, lang).filter { it.method != Method.PICTURE && it.method != Method.BOARD }
        .map { it.value.trim() }.filter { v -> v.any(Char::isLetter) }

fun Method.label() = when (this) {
    Method.WRITTEN -> R.string.given_written
    Method.PAD -> R.string.given_pad
    Method.PICTURE -> R.string.given_picture
    Method.PICKER -> R.string.given_picker
    Method.SIGN -> R.string.given_sign
    Method.BOARD -> R.string.given_board
}

/** How an answer was given, for its line in the record; null for answers not given through a lesson input. */
fun givenText(res: Resources, e: SessionEvent): String? = givenOf(e.reason)?.let { (method, confirmed) ->
    "(" + listOfNotNull(res.getString(method.label()), res.getString(R.string.given_confirmed).takeIf { confirmed }).joinToString(", ") + ")"
}

/** The report's "Personal details given" section, or null when the subject gave none. */
fun detailsSection(res: Resources, content: ContentBundle, lang: String, events: List<SessionEvent>): ReportSection? =
    personalDetails(events, content, lang).takeIf { it.isNotEmpty() }?.let { details ->
        ReportSection(
            title = res.getString(R.string.details_title),
            header = listOf(R.string.details_field, R.string.details_value, R.string.details_method, R.string.details_confirmed).map(res::getString),
            rows = details.map { d ->
                listOf(d.field, d.value, res.getString(d.method.label()), res.getString(if (d.confirmed) R.string.details_yes else R.string.details_no))
            },
        )
    }
