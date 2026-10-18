package app.signsetu

import app.signsetu.assistant.TokenRouterAiService
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

        val report = TokenRouterAiService.synthesizeOfflineForensicReport(
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
        val painChild = TokenRouterAiService.offlineTraumaFollowUps("pain in stomach", isChild = true)
        assertTrue(painChild.isNotEmpty())
        assertTrue(painChild.all { it.endsWith("?") })
        assertTrue(painChild.any { it.contains("water") || it.contains("pain") || it.contains("rest") })

        // Scared adult
        val fearAdult = TokenRouterAiService.offlineTraumaFollowUps("I am afraid", isChild = false)
        assertTrue(fearAdult.isNotEmpty())
        assertTrue(fearAdult.all { it.endsWith("?") })
        assertTrue(fearAdult.any { it.contains("safe") || it.contains("trusted") || it.contains("pace") })

        // Physical attack
        val attackQuestions = TokenRouterAiService.offlineTraumaFollowUps("they hit me", isChild = false)
        assertTrue(attackQuestions.isNotEmpty())
        assertTrue(attackQuestions.all { it.endsWith("?") })
        assertTrue(attackQuestions.any { it.contains("safety") || it.contains("recently") || it.contains("happened") })
    }

    // ──── New: school, touch, and night follow-up branches ────

    @Test
    fun `school-related follow-ups for child`() {
        val qs = TokenRouterAiService.offlineTraumaFollowUps("teacher at school", isChild = true)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("school") || it.contains("teacher") })
    }

    @Test
    fun `touch-related follow-ups for adult`() {
        val qs = TokenRouterAiService.offlineTraumaFollowUps("bad touch", isChild = false)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("body map") || it.contains("recently") || it.contains("pause") })
    }

    @Test
    fun `night and dark follow-ups for child`() {
        val qs = TokenRouterAiService.offlineTraumaFollowUps("it happened at night in the dark", isChild = true)
        assertTrue(qs.isNotEmpty())
        assertTrue(qs.all { it.endsWith("?") })
        assertTrue(qs.any { it.contains("dark") || it.contains("alone") || it.contains("sleeping") })
    }

    // ──── New: ISL gloss normalizer ────

    @Test
    fun `normalizeIslGloss deduplicates CTC repeats`() {
        val result = TokenRouterAiService.normalizeIslGloss("HELLO HELLO WORLD WORLD WORLD")
        assertEquals("HELLO WORLD", result)
    }

    @Test
    fun `normalizeIslGloss strips filler tokens`() {
        val result = TokenRouterAiService.normalizeIslGloss("UNK HELLO BLANK WORLD PAD NONE")
        assertEquals("HELLO WORLD", result)
    }

    @Test
    fun `normalizeIslGloss handles mixed case and punctuation`() {
        val result = TokenRouterAiService.normalizeIslGloss("hello! world. test?")
        assertEquals("HELLO WORLD TEST", result)
    }

    @Test
    fun `normalizeIslGloss preserves distinct tokens`() {
        val result = TokenRouterAiService.normalizeIslGloss("WATER FOOD WATER FOOD")
        // Not consecutive repeats, so all four stay
        assertEquals("WATER FOOD WATER FOOD", result)
    }

    // ──── New: conversation context builder ────

    @Test
    fun `buildConversationContext with few exchanges returns all`() {
        val log = """
            [QUESTION_SHOWN] [TEXT] text=Are you safe? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=Yes card=yes lang=hi
            [QUESTION_SHOWN] [TEXT] text=Does it hurt? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=No card=no lang=hi
        """.trimIndent()

        val ctx = TokenRouterAiService.buildConversationContext(log, windowSize = 8)
        assertTrue(ctx.contains("Are you safe?"))
        assertTrue(ctx.contains("Yes"))
        assertTrue(ctx.contains("Does it hurt?"))
        assertTrue(ctx.contains("No"))
    }

    @Test
    fun `buildConversationContext with many exchanges summarizes earlier ones`() {
        val lines = (1..20).flatMap { i ->
            listOf(
                "[QUESTION_SHOWN] [TEXT] text=Question $i? card=null lang=en",
                "[ANSWER_CARD] [SUBJECT] text=Answer $i card=a$i lang=hi",
            )
        }.joinToString("\n")

        val ctx = TokenRouterAiService.buildConversationContext(lines, windowSize = 5)
        assertTrue("Should contain summary marker", ctx.contains("[Earlier:"))
        assertTrue("Should contain recent Q", ctx.contains("Question 20?"))
        assertTrue("Should contain recent A", ctx.contains("Answer 20"))
    }

    // ──── New: structured timeline ────

    @Test
    fun `buildStructuredTimeline extracts entries`() {
        val log = """
            [QUESTION_SHOWN] [TEXT] text=Are you safe? card=safe lang=en
            [ANSWER_CARD] [SUBJECT] text=Yes card=yes lang=hi
        """.trimIndent()

        val timeline = TokenRouterAiService.buildStructuredTimeline(log)
        assertEquals(2, timeline.size)
        assertEquals("QUESTION_SHOWN", timeline[0]["type"])
        assertEquals("TEXT", timeline[0]["source"])
        assertEquals("Are you safe?", timeline[0]["text"])
        assertEquals("ANSWER_CARD", timeline[1]["type"])
        assertEquals("SUBJECT", timeline[1]["source"])
        assertEquals("Yes", timeline[1]["text"])
    }

    @Test
    fun `buildStructuredTimeline handles empty log`() {
        val timeline = TokenRouterAiService.buildStructuredTimeline("")
        assertTrue(timeline.isEmpty())
    }
}
