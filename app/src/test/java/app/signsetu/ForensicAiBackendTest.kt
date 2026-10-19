package app.signsetu

import app.signsetu.assistant.OfflineReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicAiBackendTest {

    @Test
    fun offlineForensicReportSynthesizesStructuredLegalReportFromLog() {
        val rawLog = """
            [QUESTION_SHOWN] [TEXT] text=Are you feeling pain anywhere? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=Pain in arm card=pain_arm lang=hi cue:nod
            [QUESTION_SHOWN] [TEXT] text=How old are you? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=9 card=n9 lang=en
            [QUESTION_SHOWN] [TEXT] text=What happened? card=null lang=en
            [ANSWER_SIGNED] [SUBJECT] text=attack home scared card=null lang=en
            [ANSWER_DRAWING] [SUBJECT] text=null card=null lang=null
            [NOTE] [OFFICER] text=Victim accompanied by aunt; appears shaken but communicative. card=null lang=en
        """.trimIndent()

        val report = OfflineReport.synthesizeOfflineForensicReport(
            sessionLogText = rawLog,
            role = "VICTIM",
            regionalDialect = "Chhattisgarhi / ISL",
        )

        assertNotNull(report)
        assertTrue("Contains incident essence section", report.contains("I. INCIDENT ESSENCE & CASE METADATA"))
        assertTrue("Contains statements section", report.contains("II. STATEMENTS & ALLEGATIONS BY SUBJECT"))
        assertTrue("Contains injury and pain evaluation", report.contains("III. INJURY, PAIN & IMMEDIATE MEDICAL EVALUATION"))
        assertTrue("Contains recorded officer observations", report.contains("IV. RECORDED OFFICER OBSERVATIONS & CASE NOTES"))
        assertTrue("Contains forensic admissibility & safeguards", report.contains("V. FORENSIC ADMISSIBILITY & STATUTORY SAFEGUARDS"))
        assertTrue("Contains recommended police & CWC actions", report.contains("VI. RECOMMENDED IMMEDIATE POLICE & CWC ACTIONS"))

        // Content integrity checks
        assertTrue("Extracted pain point in arm", report.contains("arm"))
        assertTrue("Extracted age 9", report.contains("9 years"))
        assertTrue("Extracted signed words", report.contains("attack home scared"))
        assertTrue("Extracted officer note", report.contains("Victim accompanied by aunt"))
        assertTrue("Mentions POCSO Act", report.contains("POCSO Act"))
        assertTrue("Mentions Section 164 CrPC / BNSS", report.contains("Section 164 CrPC") || report.contains("183"))
    }

    @Test
    fun offlineTraumaFollowUpSuggestionsProvideEmpatheticNonLeadingQuestions() {
        // Child in pain
        val painChild = OfflineReport.offlineTraumaFollowUps("pain in stomach", isChild = true)
        assertTrue(painChild.isNotEmpty())
        assertTrue(painChild.all { it.endsWith("?") })
        assertTrue(painChild.any { it.contains("water") || it.contains("pain") || it.contains("rest") })

        // Scared adult
        val fearAdult = OfflineReport.offlineTraumaFollowUps("I am afraid", isChild = false)
        assertTrue(fearAdult.isNotEmpty())
        assertTrue(fearAdult.all { it.endsWith("?") })
        assertTrue(fearAdult.any { it.contains("safe") || it.contains("trusted") || it.contains("pace") })

        // Physical attack
        val attackQuestions = OfflineReport.offlineTraumaFollowUps("they hit me", isChild = false)
        assertTrue(attackQuestions.isNotEmpty())
        assertTrue(attackQuestions.all { it.endsWith("?") })
        assertTrue(attackQuestions.any { it.contains("safety") || it.contains("recently") || it.contains("happened") })
    }

    // ──── New: school, touch, and night follow-up branches ────

    @Test
    fun `school-related follow-ups for child`() {
        val qs = OfflineReport.offlineTraumaFollowUps("teacher at school", isChild = true)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("school") || it.contains("teacher") })
    }

    @Test
    fun `touch-related follow-ups for adult`() {
        val qs = OfflineReport.offlineTraumaFollowUps("bad touch", isChild = false)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("body map") || it.contains("recently") || it.contains("pause") })
    }

    @Test
    fun `night and dark follow-ups for child`() {
        val qs = OfflineReport.offlineTraumaFollowUps("it happened at night in the dark", isChild = true)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("dark") || it.contains("alone") || it.contains("sleeping") })
    }

    // ──── New: ISL gloss normalizer ────
}
