package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.EventSeal
import app.signsetu.export.EvidenceIntegrity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceIntegrityTest {

    private val session = Session(
        id = "test-session-001",
        startedAt = 1700000000000L,
        profile = Profile.ISL_USER,
        subjectLang = "hi",
        role = Role.VICTIM,
    )

    private val events = listOf(
        SessionEvent(1, 1700000001000, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", "Are you safe?", "safe", null, null),
        SessionEvent(2, 1700000005000, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", "Yes", "yes", null, null),
        SessionEvent(3, 1700000010000, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", "Does it hurt?", "hurt", null, null),
        SessionEvent(4, 1700000015000, EventType.ANSWER_CARD, EventSource.SUBJECT, "hi", "No", "no", null, null),
        SessionEvent(5, 1700000020000, EventType.QUESTION_SHOWN, EventSource.PRESET, "en", "Where does it hurt?", null, null, null),
        SessionEvent(6, 1700000030000, EventType.ANSWER_DRAWING, EventSource.SUBJECT, null, null, null, null, 101L),
        SessionEvent(7, 1700000040000, EventType.NOTE, EventSource.OFFICER, "en", "Subject appears calm", null, null, null),
        SessionEvent(8, 1700000050000, EventType.ANSWER_SIGNED, EventSource.MACHINE, "isl", "HAND POINT CHEST", null, null, null),
        SessionEvent(9, 1700000055000, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, null, null, "unclear", null, null),
    )

    private val drawings = mapOf(101L to byteArrayOf(1, 2, 3))

    // Seals each row as SessionRepository.log does, in write order.
    private fun sealed(s: Session = session, e: List<SessionEvent> = events): List<SessionEvent> {
        var previous = EventSeal.genesis(s.id, s.startedAt)
        return e.map { ev ->
            val hash = EventSeal.seal(previous, s.id, ev.ts, ev.type.name, ev.source.name, ev.lang, ev.text, ev.cardId, ev.lexiconId,
                ev.drawingId?.let(drawings::get), ev.itemId, ev.reason, ev.shownId, ev.via)
            previous = hash
            ev.copy(hash = hash)
        }
    }

    private fun check(e: List<SessionEvent>, s: Session = session, d: Map<Long, ByteArray> = drawings) =
        runBlocking { EvidenceIntegrity.verifyChain(s, e) { d[it] } }

    @Test
    fun anIntactLogVerifiesAndItsHeadIsTheLastSeal() {
        val log = sealed()
        val c = check(log)
        assertTrue(c.intact)
        assertEquals(log.last().hash, c.head)
        assertEquals(9, c.sealed)
        assertTrue(c.short.matches(Regex("[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}")))
    }

    @Test
    fun anyChangeToARowOrItsDrawingIsFoundAtThatRow() {
        val log = sealed()
        assertEquals(2L, check(log.toMutableList().also { it[1] = it[1].copy(text = "TAMPERED") }).brokenAt)
        assertEquals(2L, check(log.toMutableList().also { it[1] = it[1].copy(cardId = "no") }).brokenAt)
        assertEquals(6L, check(log, d = mapOf(101L to byteArrayOf(9))).brokenAt)
        // A removed row breaks the next one, whose seal covers it.
        assertEquals(3L, check(log.filterNot { it.id == 2L }).brokenAt)
        assertEquals(1L, check(log, s = session.copy(id = "different-session")).brokenAt)
    }

    @Test
    fun rowsFromBeforeSealingAreCountedNotTrusted() {
        val c = check(events)
        assertTrue(c.intact)
        assertEquals(9, c.unsealed)
        assertEquals(0, c.sealed)
    }

    @Test
    fun metricsCountQuestionsAnswersAndTheirKinds() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        assertEquals(9, metrics.totalEvents)
        assertEquals(3, metrics.questionCount)
        // Two cards, one drawing, one signed and one unclear; the note is not an answer.
        assertEquals(5, metrics.answerCount)
        assertEquals(1, metrics.affirmativeCount)
        assertEquals(1, metrics.negativeCount)
        assertEquals(1, metrics.drawingCount)
        assertEquals(1, metrics.signedCount)
        // To the latest event, not to now.
        assertEquals(55L, metrics.durationSeconds)
        val empty = EvidenceIntegrity.computeSessionMetrics(session, emptyList(), now = 1700000060000L)
        assertEquals(0, empty.answerCount)
        assertEquals(60L, empty.durationSeconds)
    }

    @Test
    fun theCertificateCarriesTheSessionItsDigestsAndItsCounts() {
        val record = "abcd-ef01-2345-6789"
        val chain = check(sealed())
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, chain, record)
        assertTrue(cert.contains("[Under Section 63 of Bharatiya Sakshya Adhiniyam, 2023 (BSA)]"))
        assertTrue(cert.contains("Electronic Session ID   : test-session-001"))
        assertTrue(cert.contains("Chain Head (SHA-256)     : ${chain.head}"))
        assertTrue(cert.contains("Chain Status             : Intact: 9 sealed entries re-checked"))
        assertTrue(cert.contains("PDF Record Digest        : $record"))
        assertTrue(cert.contains("Total Recorded Events    : 9"))
        assertTrue(cert.contains("Items Shown              : 3"))
        assertTrue(cert.contains("Subject Direct Answers   : 5 (Affirmative: 1, Negative: 1)"))
    }
}
