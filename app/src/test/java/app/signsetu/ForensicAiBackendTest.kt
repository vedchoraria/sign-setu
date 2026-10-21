package app.signsetu

import app.signsetu.assistant.OfflineReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicAiBackendTest {

    @Test
    fun offlineReportExtractsAnswersPainAgesCuesAndNotesFromTheLog() {
        val rawLog = """
            [QUESTION_SHOWN] [TEXT] text=Are you feeling pain anywhere? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=Pain in arm card=pain_arm lang=hi cue:nod]
            [QUESTION_SHOWN] [TEXT] text=How old are you? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=null card=n9 lang=en
            [QUESTION_SHOWN] [TEXT] text=What happened? card=null lang=en
            [ANSWER_SIGNED] [SUBJECT] text=attack home scared card=null lang=en
            [ANSWER_DRAWING] [SUBJECT] text=null card=null lang=null
            [NOTE] [OFFICER] text=Victim accompanied by aunt; appears shaken but communicative. card=null lang=en
        """.trimIndent()

        val report = OfflineReport.synthesizeOfflineForensicReport(rawLog, role = "VICTIM", regionalDialect = "Chhattisgarhi")

        assertTrue(report.contains("Subject Classification : Victim / Reporting Subject"))
        assertTrue(report.contains("Questions Formulated   : 3 structured"))
        assertTrue(report.contains("Evidentiary Responses  : 3 recorded answers, 1 drawing/landmark exhibits"))
        // An answer with no text is recorded by its card.
        assertTrue(report.contains("• n9"))
        assertTrue(report.contains("Pointed / Indicated Pain Areas : arm\n"))
        assertTrue(report.contains("Reported via finger gestures: 9 years"))
        assertTrue(report.contains("Observed Physical Cues        : nod\n"))
        assertTrue(report.contains("   • Victim accompanied by aunt; appears shaken but communicative."))
        assertTrue(OfflineReport.synthesizeOfflineForensicReport("", "ACCUSED", "hi").contains("Accused / Subject in Custody"))
    }

    @Test
    fun followUpsFollowTheTopicOfTheLastAnswerAndWhetherTheSubjectIsAChild() {
        fun first(answer: String, child: Boolean) = OfflineReport.offlineTraumaFollowUps(answer, child).first()
        assertEquals("Would you like a glass of water?", first("pain in stomach", child = true))
        assertEquals("Do you need medical attention right away?", first("मुझे दर्द है", child = false))
        assertEquals("Do you feel safe in this room?", first("I am afraid", child = false))
        assertEquals("Did this happen recently?", first("they hit me", child = false))
        assertEquals("Do you feel safe returning home?", first("at home", child = true))
        assertEquals("Do you feel safe at school?", first("teacher at school", child = true))
        assertEquals("Can you point on the body map to where?", first("bad touch", child = false))
        assertEquals("Are you scared of the dark?", first("it happened at night in the dark", child = true))
        assertEquals("Are you feeling comfortable?", first("blue", child = true))
        assertEquals("Are you comfortable continuing?", first("blue", child = false))
        for (child in listOf(true, false)) {
            assertTrue(OfflineReport.offlineTraumaFollowUps("pain", child).all { it.endsWith("?") })
        }
    }
}
