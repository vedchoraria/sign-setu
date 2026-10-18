package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.SessionAuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAuditLogTest {

    private val session = Session(
        id = "audit-test-001",
        startedAt = 1700000000000L,
        profile = Profile.ISL_USER,
        subjectLang = "hi",
        role = Role.VICTIM,
    )

    private val events = listOf(
        SessionEvent(1, 1700000001000, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", "Are you safe?", "safe", null, null),
        SessionEvent(2, 1700000005000, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", "Yes", "yes", null, null),
        SessionEvent(3, 1700000010000, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", "Does it hurt?", null, null, null),
        SessionEvent(4, 1700000015000, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", null, "pain_arm", null, null),
        SessionEvent(5, 1700000020000, EventType.ANSWER_DRAWING, EventSource.SUBJECT, null, null, null, null, 101L),
        SessionEvent(6, 1700000030000, EventType.NOTE, EventSource.OFFICER, "en", "Subject calm, cooperative", null, null, null),
        SessionEvent(7, 1700000040000, EventType.ANSWER_SIGNED, EventSource.MACHINE, "isl", "POINT CHEST HURT", null, null, null),
        SessionEvent(8, 1700000050000, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", null, "stop", null, null),
    )

    // ──── Audit timeline ────

    @Test
    fun `audit timeline contains session id`() {
        val log = SessionAuditLog.buildAuditTimeline(session, events)
        assertTrue(log.contains("audit-test-001"))
    }

    @Test
    fun `audit timeline contains all events`() {
        val log = SessionAuditLog.buildAuditTimeline(session, events)
        assertTrue(log.contains("QUESTION"))
        assertTrue(log.contains("ANSWER"))
        assertTrue(log.contains("NOTE"))
        assertTrue(log.contains("Total events: 8"))
    }

    @Test
    fun `audit timeline handles empty events`() {
        val log = SessionAuditLog.buildAuditTimeline(session, emptyList())
        assertTrue(log.contains("Empty session"))
    }

    @Test
    fun `audit timeline has timestamps`() {
        val log = SessionAuditLog.buildAuditTimeline(session, events)
        assertTrue(log.contains("[00:01]"))  // 1 second in
        assertTrue(log.contains("[00:50]"))  // 50 seconds in
    }

    // ──── Case diary summary ────

    @Test
    fun `case diary summary contains question and answer counts`() {
        val summary = SessionAuditLog.buildCaseDiarySummary(session, events)
        assertTrue(summary.contains("Questions: 2"))
        assertTrue(summary.contains("Answers: 5"))
    }

    @Test
    fun `case diary summary contains role and profile`() {
        val summary = SessionAuditLog.buildCaseDiarySummary(session, events)
        assertTrue(summary.contains("VICTIM"))
        assertTrue(summary.contains("ISL_USER"))
    }

    @Test
    fun `case diary summary shows officer notes`() {
        val summary = SessionAuditLog.buildCaseDiarySummary(session, events)
        assertTrue(summary.contains("Subject calm, cooperative"))
    }

    @Test
    fun `case diary summary mentions stop requests`() {
        val summary = SessionAuditLog.buildCaseDiarySummary(session, events)
        assertTrue(summary.contains("stop"))
    }

    // ──── ISL gloss extraction ────

    @Test
    fun `extractIslGlosses returns signed answers`() {
        val glosses = SessionAuditLog.extractIslGlosses(events)
        assertEquals(1, glosses.size)
        assertEquals("POINT CHEST HURT", glosses[0])
    }

    @Test
    fun `extractIslGlosses handles no signed events`() {
        val noSigns = events.filter { it.type != EventType.ANSWER_SIGNED }
        val glosses = SessionAuditLog.extractIslGlosses(noSigns)
        assertTrue(glosses.isEmpty())
    }

    // ──── Pain indicators ────

    @Test
    fun `extractPainIndicators finds pain cards`() {
        val pains = SessionAuditLog.extractPainIndicators(events)
        assertTrue(pains.contains("pain_arm"))
    }

    @Test
    fun `extractPainIndicators deduplicates`() {
        val doubled = events + SessionEvent(9, 1700000060000, EventType.ANSWER_CARD, EventSource.SUBJECT, null, null, "pain_arm", null, null)
        val pains = SessionAuditLog.extractPainIndicators(doubled)
        assertEquals(1, pains.count { it == "pain_arm" })
    }

    // ──── Officer notes extraction ────

    @Test
    fun `extractOfficerNotes returns only officer notes`() {
        val notes = SessionAuditLog.extractOfficerNotes(events)
        assertEquals(1, notes.size)
        assertEquals("Subject calm, cooperative", notes[0])
    }

    @Test
    fun `extractOfficerNotes excludes non-officer sources`() {
        val mixed = events + SessionEvent(10, 1700000070000, EventType.NOTE, EventSource.MACHINE, "en", "Machine note", null, null, null)
        val notes = SessionAuditLog.extractOfficerNotes(mixed)
        assertEquals(1, notes.size)
    }
}
