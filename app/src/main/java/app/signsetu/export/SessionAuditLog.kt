package app.signsetu.export

import app.signsetu.data.session.EventType
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent

/**
 * Structured session audit log builder for post-session forensic analysis.
 *
 * Produces a human-readable plain-text audit timeline that can be included
 * in case diaries, attached to FIRs, or preserved alongside the encrypted PDF.
 * Every entry is deterministic and reproducible from the same event list.
 */
object SessionAuditLog {

    /**
     * Builds a compact, chronological audit log from the session events.
     * Each line is timestamped relative to session start for easy scanning.
     */
    fun buildAuditTimeline(session: Session, events: List<SessionEvent>): String {
        if (events.isEmpty()) return "[Empty session — no events recorded]"
        val start = session.startedAt
        val sorted = events.sortedWith(compareBy({ it.ts }, { it.id }))
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════════════")
        sb.appendLine("SESSION AUDIT LOG — ${session.id}")
        sb.appendLine("Role: ${session.role.name}  Profile: ${session.profile.name}  Lang: ${session.subjectLang}")
        sb.appendLine("═══════════════════════════════════════════════════════")

        for (event in sorted) {
            val elapsed = (event.ts - start).coerceAtLeast(0L)
            val mins = elapsed / 60_000
            val secs = (elapsed % 60_000) / 1000
            val ts = "%02d:%02d".format(mins, secs)
            val label = eventLabel(event)
            sb.appendLine("[$ts] $label")
        }

        val duration = sorted.last().ts - start
        sb.appendLine("═══════════════════════════════════════════════════════")
        sb.appendLine("Total events: ${events.size}  Duration: ${duration / 1000}s")
        sb.appendLine("═══════════════════════════════════════════════════════")
        return sb.toString()
    }

    private fun eventLabel(e: SessionEvent): String {
        val source = when (e.source) {
            EventSource.SUBJECT -> "SUBJECT"
            EventSource.OFFICER -> "OFFICER"
            EventSource.MACHINE -> "MACHINE"
            EventSource.PRESET -> "PRESET"
            else -> e.source.name
        }
        val detail = when (e.type) {
            EventType.QUESTION_SHOWN -> "QUESTION: ${e.text ?: e.cardId ?: "—"}"
            EventType.ANSWER_CARD -> "ANSWER (card): ${e.text ?: e.cardId ?: "—"}"
            EventType.ANSWER_TEXT -> "ANSWER (text): ${e.text ?: "—"}"
            EventType.ANSWER_DRAWING -> "ANSWER (drawing): exhibit #${e.drawingId ?: "?"}"
            EventType.ANSWER_GESTURE -> "ANSWER (gesture): ${e.text ?: e.cardId ?: "—"}"
            EventType.ANSWER_SIGNED -> "ANSWER (signed/ISL): ${e.text ?: "—"}"
            EventType.ANSWER_UNCLEAR -> "ANSWER (unclear): ${e.cardId ?: "—"}"
            EventType.NOTE -> "NOTE: ${e.text ?: "—"}"
            EventType.SYSTEM -> "SYSTEM: ${e.text ?: "—"}"
            EventType.LANGUAGE_CHOSEN -> "LANGUAGE: ${e.text ?: e.lang ?: "—"}"
            EventType.SUGGESTED -> "SUGGESTED: ${e.itemId ?: "—"} (${e.reason ?: "—"})"
            EventType.APPROVED -> "APPROVED: ${e.itemId ?: "—"}"
            EventType.SKIPPED -> "SKIPPED: ${e.itemId ?: "—"}"
            EventType.AUTO_ADVANCED -> "AUTO-ADVANCED: ${e.itemId ?: "—"}"
            EventType.LINK -> "LINK: ${e.text ?: "—"}"
            EventType.PROMPT -> "PROMPT: ${e.text ?: "—"}"
        }
        val via = if (e.via != null) " [via ${e.via}]" else ""
        return "$source → $detail$via"
    }

    /**
     * Extracts a compact summary suitable for the case diary:
     * key facts, counts, and outcome in 5-8 lines.
     */
    fun buildCaseDiarySummary(session: Session, events: List<SessionEvent>): String {
        val questions = events.count { it.type == EventType.QUESTION_SHOWN }
        val answers = events.count { it.type in setOf(
            EventType.ANSWER_CARD, EventType.ANSWER_TEXT, EventType.ANSWER_DRAWING,
            EventType.ANSWER_GESTURE, EventType.ANSWER_SIGNED, EventType.ANSWER_UNCLEAR,
        )}
        val notes = events.filter { it.type == EventType.NOTE && it.source == EventSource.OFFICER }
        val drawings = events.count { it.drawingId != null }
        val signed = events.count { it.type == EventType.ANSWER_SIGNED }
        val stops = events.count { it.cardId == "stop" }
        val viaStage = events.count { it.via != null }

        val duration = if (events.isNotEmpty()) {
            val d = (events.maxOf { it.ts } - session.startedAt) / 1000
            "${d / 60}m ${d % 60}s"
        } else "0s"

        return buildString {
            appendLine("CASE DIARY ENTRY — Session ${session.id}")
            appendLine("Role: ${session.role.name} | Profile: ${session.profile.name} | Language: ${session.subjectLang}")
            appendLine("Duration: $duration | Questions: $questions | Answers: $answers")
            if (drawings > 0) appendLine("Drawing exhibits: $drawings")
            if (signed > 0) appendLine("ISL signed responses: $signed")
            if (stops > 0) appendLine("Subject requested stop: $stops time(s)")
            if (viaStage > 0) appendLine("Second-screen (stage) interactions: $viaStage")
            if (notes.isNotEmpty()) {
                appendLine("Officer notes:")
                notes.forEach { appendLine("  • ${it.text ?: "—"}") }
            }
        }
    }

    /**
     * Returns the list of ISL gloss sequences from ANSWER_SIGNED events,
     * useful for linguistic analysis and interpreter review.
     */
    fun extractIslGlosses(events: List<SessionEvent>): List<String> =
        events.filter { it.type == EventType.ANSWER_SIGNED && !it.text.isNullOrBlank() }
            .map { it.text!!.trim() }

    /**
     * Returns all pain/injury indicators from answer cards.
     */
    fun extractPainIndicators(events: List<SessionEvent>): List<String> {
        val painCards = setOf("pain_arm", "pain_leg", "pain_head", "pain_chest", "pain_stomach",
            "head", "arm", "chest", "stomach", "leg", "hand", "face", "throat")
        return events.filter { it.cardId in painCards }
            .map { it.cardId!! }
            .distinct()
    }

    /**
     * Returns all officer notes in chronological order.
     */
    fun extractOfficerNotes(events: List<SessionEvent>): List<String> =
        events.filter { it.type == EventType.NOTE && it.source == EventSource.OFFICER && !it.text.isNullOrBlank() }
            .map { it.text!!.trim() }
}
