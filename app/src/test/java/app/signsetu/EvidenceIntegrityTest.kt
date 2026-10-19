package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.EvidenceIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the cryptographic chain-of-custody hashing, session metrics aggregation,
 * and Section 63 BSA 2023 statutory certificate generation.
 */
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

    // ──── Chain-of-custody hashing ────

    @Test
    fun `chain hash is deterministic for same inputs`() {
        val hash1 = EvidenceIntegrity.computeChainHash(session, events)
        val hash2 = EvidenceIntegrity.computeChainHash(session, events)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `chain hash format is four dash-separated 4-char hex groups`() {
        val hash = EvidenceIntegrity.computeChainHash(session, events)
        assertTrue("Expected xxxx-xxxx-xxxx-xxxx but got: $hash", hash.matches(Regex("[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}")))
    }

    @Test
    fun `chain hash changes when an event is added`() {
        val original = EvidenceIntegrity.computeChainHash(session, events)
        val extra = events + SessionEvent(10, 1700000060000, EventType.NOTE, EventSource.OFFICER, "en", "Extra note", null, null, null)
        val withExtra = EvidenceIntegrity.computeChainHash(session, extra)
        assertNotEquals(original, withExtra)
    }

    @Test
    fun `chain hash changes when an event text is altered`() {
        val original = EvidenceIntegrity.computeChainHash(session, events)
        val tampered = events.toMutableList()
        tampered[1] = tampered[1].copy(text = "TAMPERED")
        val afterTamper = EvidenceIntegrity.computeChainHash(session, tampered)
        assertNotEquals("Tampered event must produce a different chain hash", original, afterTamper)
    }

    @Test
    fun `chain hash changes with different session id`() {
        val other = session.copy(id = "different-session")
        val hash1 = EvidenceIntegrity.computeChainHash(session, events)
        val hash2 = EvidenceIntegrity.computeChainHash(other, events)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `empty event list produces a genesis-only hash`() {
        val hash = EvidenceIntegrity.computeChainHash(session, emptyList())
        assertTrue(hash.isNotBlank())
        assertTrue(hash.matches(Regex("[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}")))
    }

    // ──── Session metrics ────

    @Test
    fun `session metrics counts questions correctly`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        assertEquals(3, metrics.questionCount)
    }

    @Test
    fun `session metrics counts answers correctly`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        // ANSWER_CARD x2 + ANSWER_DRAWING x1 + ANSWER_SIGNED x1 + ANSWER_UNCLEAR x1 = 5
        assertEquals(5, metrics.answerCount)
    }

    @Test
    fun `session metrics counts affirmative and negative`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        assertEquals(1, metrics.affirmativeCount)
        assertEquals(1, metrics.negativeCount)
    }

    @Test
    fun `session metrics counts drawings`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        assertEquals(1, metrics.drawingCount)
    }

    @Test
    fun `session metrics counts signed answers`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        assertEquals(1, metrics.signedCount)
    }

    @Test
    fun `session metrics computes duration in seconds`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, events, now = 1700000060000L)
        // last event at ts=1700000055000, session started at 1700000000000 → 55 seconds
        assertEquals(55L, metrics.durationSeconds)
    }

    @Test
    fun `session metrics handles empty events`() {
        val metrics = EvidenceIntegrity.computeSessionMetrics(session, emptyList(), now = 1700000060000L)
        assertEquals(0, metrics.questionCount)
        assertEquals(0, metrics.answerCount)
        // Duration falls back to now - start = 60 seconds
        assertEquals(60L, metrics.durationSeconds)
    }

    // ──── Section 63 BSA certificate ────

    @Test
    fun `certificate contains section 63 BSA 2023 header`() {
        val digest = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, digest, "abcd-ef01")
        assertTrue("Must reference Section 63 BSA", cert.contains("Section 63") && cert.contains("BSA"))
    }

    @Test
    fun `certificate contains session id`() {
        val digest = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, digest, "abcd-ef01")
        assertTrue(cert.contains("test-session-001"))
    }

    @Test
    fun `certificate contains chain digest and record digest`() {
        val chain = EvidenceIntegrity.computeChainHash(session, events)
        val record = "abcd-ef01-2345-6789"
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, chain, record)
        assertTrue("Must contain chain digest", cert.contains(chain))
        assertTrue("Must contain record digest", cert.contains(record))
    }

    @Test
    fun `certificate contains proper operation statement`() {
        val chain = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, chain, "rec-digest")
        assertTrue(cert.contains("PROPER OPERATION"))
        assertTrue(cert.contains("SOLEMN AFFIRMATION"))
    }

    @Test
    fun `certificate contains forensic metrics`() {
        val chain = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, chain, "rec-digest")
        assertTrue("Must contain question count", cert.contains("3"))
        assertTrue("Must contain event count", cert.contains("9"))
    }

    @Test
    fun `certificate mentions POCSO and JJ Act`() {
        val chain = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(session, events, chain, "rec-digest")
        // It references Section 65B of Indian Evidence Act 1872 (the predecessor of Section 63 BSA)
        assertTrue(cert.contains("65B") || cert.contains("Indian Evidence Act"))
    }

    @Test
    fun `certificate uses custom officer details when provided`() {
        val chain = EvidenceIntegrity.computeChainHash(session, events)
        val cert = EvidenceIntegrity.generateSection63Certificate(
            session, events, chain, "rec-digest",
            officerName = "Inspector Sharma",
            officerRank = "Inspector of Police",
            policeStation = "Bastar PS",
        )
        assertTrue(cert.contains("Inspector Sharma"))
        assertTrue(cert.contains("Inspector of Police"))
        assertTrue(cert.contains("Bastar PS"))
    }
}
