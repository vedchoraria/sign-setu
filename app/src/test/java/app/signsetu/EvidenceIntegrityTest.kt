package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.EvidenceIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun chain(s: Session = session, e: List<SessionEvent> = events) = EvidenceIntegrity.computeChainHash(s, e)

    @Test
    fun theChainIsAStableSealOfTheWholeTimeline() {
        val seal = Regex("[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}")
        assertTrue(chain().matches(seal))
        assertTrue(chain(e = emptyList()).matches(seal))
        assertEquals(chain(), chain(e = events.shuffled(java.util.Random(1))))
    }

    @Test
    fun anyChangeToTheSessionOrAnEventBreaksTheSeal() {
        val original = chain()
        assertNotEquals(original, chain(e = events + SessionEvent(10, 1700000060000, EventType.NOTE, EventSource.OFFICER, "en", "Extra", null, null, null)))
        assertNotEquals(original, chain(e = events.dropLast(1)))
        assertNotEquals(original, chain(e = events.toMutableList().also { it[1] = it[1].copy(text = "TAMPERED") }))
        assertNotEquals(original, chain(e = events.toMutableList().also { it[1] = it[1].copy(cardId = "no") }))
        assertNotEquals(original, chain(e = events.toMutableList().also { it[5] = it[5].copy(drawingId = 102L) }))
        assertNotEquals(original, chain(s = session.copy(id = "different-session")))
        assertNotEquals(original, chain(s = session.copy(role = Role.ACCUSED)))
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
    fun theCertificateCarriesTheSessionItsDigestsItsCountsAndTheOfficer() {
        val record = "abcd-ef01-2345-6789"
        val cert = EvidenceIntegrity.generateSection63Certificate(
            session, events, chain(), record,
            officerName = "Inspector Sharma",
            officerRank = "Inspector of Police",
            policeStation = "Bastar PS",
        )
        assertTrue(cert.contains("[Under Section 63 of Bharatiya Sakshya Adhiniyam, 2023 (BSA)]"))
        assertTrue(cert.contains("Electronic Session ID   : test-session-001"))
        assertTrue(cert.contains("Terminal Chained Hash    : ${chain()}"))
        assertTrue(cert.contains("PDF Record Digest        : $record"))
        assertTrue(cert.contains("Total Recorded Events    : 9"))
        assertTrue(cert.contains("Non-Leading Inquiries    : 3"))
        assertTrue(cert.contains("Subject Direct Answers   : 5 (Affirmative: 1, Negative: 1)"))
        assertTrue(cert.contains("Producing Officer        : Inspector Sharma (Inspector of Police)"))
        assertTrue(cert.contains("Station: Bastar PS"))
    }
}
