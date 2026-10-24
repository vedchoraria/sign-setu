package app.signsetu

import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.planText
import app.signsetu.data.content.questionKind
import app.signsetu.data.content.spelling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typed text through the planner, on the content the app ships. */
class TextSigningTest {

    private val SIGNS = setOf(SignMode.SIGNS)
    private val SIGNS_SPELL = setOf(SignMode.SIGNS, SignMode.SPELL)

    @Test
    fun accentsFoldToTheirLettersInsteadOfVanishing() {
        assertEquals(listOf("c", "a", "f", "e"), spelling("café"))
        assertEquals(listOf("m", "a", "n", "g", "o"), spelling("mangó"))
        // Uppercase accented letters fold the same way.
        assertEquals(listOf("a", "n", "g", "s", "t"), spelling("ÁNGST"))
        val motions = shipped.planText("café", SIGNS_SPELL).motions
        assertEquals(listOf("letter.c", "letter.a", "letter.f", "letter.e"), motions)
    }

    @Test
    fun aDigitInsideAHindiWordSurvives() {
        // Each akshara is a consonant plus its swar (को is क + ओ), then the digits stay as typed.
        assertEquals(listOf("क", "ओ", "व", "इ", "ड", "1", "9"), spelling("कोविड19"))
        val motions = shipped.planText("कोविड19", SIGNS_SPELL).motions
        assertEquals(7, motions.size)
        assertEquals(listOf("digit.1", "digit.9"), motions.takeLast(2))
        assertTrue(motions.take(5).all { it.startsWith("letter.hi.") })
    }

    @Test
    fun aSentenceBreakPausesBeforeTheNextSentence() {
        val plan = shipped.planText("I am hurt. Call doctor", SIGNS_SPELL)
        val motions = plan.motions
        assertTrue("no pause between the sentences: $motions", "pause" in motions)
        val pauseAt = motions.indexOf("pause")
        assertTrue("pause is not between the two sentences: $motions", pauseAt > 0 && pauseAt < motions.lastIndex)

        // The pause is time the second sentence does not start in.
        val text = "I am hurt. Call doctor"
        val beats = plan.beats
        val hurt = beats.firstOrNull { "hurt" in text.substring(it.range).lowercase() }
        val call = beats.firstOrNull { "call" in text.substring(it.range).lowercase() }
        assertTrue("no beat for hurt: ${beats.map { text.substring(it.range) to it.start }}", hurt != null)
        assertTrue("no beat for call: ${beats.map { text.substring(it.range) to it.start }}", call != null)
        assertTrue("second sentence starts inside the pause", call!!.start >= hurt!!.end + 0.3f)
    }

    @Test
    fun aQuestionMarkInAnotherScriptStillAsks() {
        // Arabic question mark: the same intent as "?" and already counted by questionKind.
        assertEquals(app.signsetu.data.content.QuestionKind.WH, questionKind("तुम कहाँ हो؟"))
        // A question word is signed as itself; a plain yes/no question falls back to the asking gesture.
        val wh = shipped.planText("तुम कहाँ हो؟", SIGNS_SPELL).motions
        assertTrue("nothing signs the question: $wh", wh.any { it == "ask" || it.startsWith("sign.where") || it.startsWith("isl.where") })
        assertTrue("ask" in shipped.planText("तुम ठीक हो؟", SIGNS_SPELL).motions)
    }

    @Test
    fun textWithNothingToSignProducesNoGesture() {
        for (text in listOf("", "   ", "\n\t", "!!!", "is the a", "है और तो")) {
            assertNull("$text should plan no gesture", shipped.planText(text, SIGNS).gesture)
        }
    }

    @Test
    fun everyBeatPointsAtItsOwnWordAndTimeOnlyMovesForward() {
        val text = "Yesterday he hit me on the arm, so I could not sleep. Where is the doctor?"
        val plan = shipped.planText(text, SIGNS_SPELL)
        assertTrue(plan.beats.isNotEmpty())
        var previous = -1f
        for (beat in plan.beats) {
            val word = text.substring(beat.range)
            assertTrue("empty beat: $beat", word.isNotBlank())
            assertTrue("beat $word does not advance: ${beat.start} after $previous", beat.start >= previous - 1e-4f)
            assertTrue("beat $word ends before it starts", beat.end > beat.start)
            previous = beat.end
        }
        val keys = plan.gesture!!.keys
        assertTrue(keys.zipWithNext().all { (a, b) -> a.t < b.t })
    }

    @Test
    fun anUnknownWordIsSpelledOnlyWhenTheOfficerLeavesSpellingOn() {
        assertTrue("zorblat" !in shipped.words)
        val spelled = shipped.planText("zorblat", SIGNS_SPELL)
        assertEquals(listOf("letter.z", "letter.o", "letter.r", "letter.b", "letter.l", "letter.a", "letter.t"), spelled.motions)

        val silent = shipped.planText("zorblat", SIGNS)
        assertNull("spelling is off, so the word has nothing to play", silent.gesture)
        assertTrue(silent.beats.isEmpty())
    }

    /** What the practice screen promises: any typed line comes out as signs, with unknown words spelled. */
    @Test
    fun anySentenceIsSignableWithThePracticeModes() {
        val practice = setOf(SignMode.SIGNS, SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES)
        val text = "Where is the hospital, Dr Mehta?"
        val plan = shipped.planText(text, practice)
        assertTrue("a line with grammar words still signs", plan.gesture != null)
        // Every word that is not pure grammar gets a beat: a sign, a gesture, a picture or its letters.
        // ISL signs the question word last, and the beat keeps the word as the officer typed it.
        assertEquals(listOf("hospital", "Dr", "Mehta", "Where"), plan.beats.map { text.substring(it.range) })
        assertTrue("a motion for every beat", plan.motions.size >= plan.beats.size)
        // The name has no sign, so it is fingerspelled rather than dropped.
        assertTrue(plan.motions.containsAll(listOf("letter.m", "letter.e", "letter.h", "letter.t", "letter.a")))
        assertTrue(plan.gesture!!.keys.zipWithNext().all { (a, b) -> a.t < b.t })
    }

    @Test
    fun aLongSentenceStaysOnOneContinuousTimeline() {
        val text = (1..40).joinToString(" ") { if (it % 3 == 0) "pain" else "water" }
        val plan = shipped.planText(text, SIGNS_SPELL)
        val keys = plan.gesture!!.keys
        assertTrue(keys.size > 40)
        assertTrue(keys.zipWithNext().all { (a, b) -> a.t < b.t })
        assertEquals(plan.beats.last().end, keys.last().t, 0.35f)
    }

    private val words = shipped.words

    @Test
    fun nothingReadBackIsAnEmptySentenceNotABareFullStop() {
        for (gloss in listOf(emptyList(), listOf(""), listOf("  "), listOf("?"))) {
            val text = glossToText(gloss, words)
            assertEquals("no draft for $gloss", "", text.en)
            assertEquals("no draft for $gloss", "", text.hi)
        }
    }

    @Test
    fun islGlossReordersToEnglishSubjectVerbObject() {
        // ISL signs the object before the verb; the draft moves the verb back after the subject.
        assertEquals("I want water.", glossToText(listOf("me", "water", "want"), words, SignLanguage.ISL).en)
    }

    @Test
    fun aslGlossKeepsItsOwnOrderAndEndsWithItsOwnPunctuation() {
        assertEquals("I want water.", glossToText(listOf("me", "want", "water"), words, SignLanguage.ASL).en)
        assertEquals("Where your mother?", glossToText(listOf("where", "your", "mother"), words, SignLanguage.ASL).en)
    }

    @Test
    fun aFingerspelledRunBecomesOneWord() {
        // Each letter is its own gloss; the draft joins them and capitalises the word.
        assertEquals("Water.", glossToText(listOf("w", "a", "t", "e", "r"), words).en)
    }

    @Test
    fun raisedBrowsAskWithoutAQuestionSign() {
        assertEquals("You hungry?", glossToText(listOf("you", "hungry"), words, SignLanguage.ISL, browsUp = true).en)
        assertEquals("You hungry.", glossToText(listOf("you", "hungry"), words, SignLanguage.ISL, browsUp = false).en)
    }

    @Test
    fun hindiKeepsTheVerbLastAndTheQuestionWordBeforeIt() {
        val text = glossToText(listOf("me", "water", "want"), words, SignLanguage.ISL)
        assertEquals("मुझे पानी चाहिए।", text.hi)
        // Chhattisgarhi is the same sentence with its word swaps.
        assertEquals("मोला पानी चाही।", text.hne)
    }

    @Test
    fun aNoShakeNegatesTheDraft() {
        val text = glossToText(listOf("me", "hungry", "no.shake"), words, SignLanguage.ISL)
        assertEquals(true, "not" in text.en)
        assertEquals(true, "नहीं" in text.hi)
    }

    @Test
    fun aStateWordTakesACopulaNotAnAuxiliary() {
        // "I do not hungry" is not English; hungry is a state, so the negation carries am/is/are.
        assertEquals("I am not hungry.", glossToText(listOf("me", "hungry", "no.shake"), words).en)
        assertEquals("You are not hungry.", glossToText(listOf("you", "hungry", "no.shake"), words).en)
        // A real verb keeps the auxiliary.
        assertEquals("I do not want water.", glossToText(listOf("me", "water", "want", "no.shake"), words).en)
    }

    @Test
    fun aHeadShakeOnItsOwnIsNoNotDoNot() {
        val text = glossToText(listOf("no.shake"), words)
        assertEquals("No.", text.en)
        assertEquals("नहीं।", text.hi)
        assertEquals("नइ।", text.hne)
    }

    @Test
    fun aQuestionWordCarriesNoDanglingAuxiliary() {
        // "What do?" is not a sentence; with nothing to ask about, the question sign is the whole draft.
        assertEquals("What?", glossToText(listOf("what"), words).en)
        assertEquals("What do you want?", glossToText(listOf("what", "you", "want"), words).en)
        // No verb was signed, so none is invented for a possession.
        assertEquals("Where your mother?", glossToText(listOf("where", "your", "mother"), words).en)
    }
}
