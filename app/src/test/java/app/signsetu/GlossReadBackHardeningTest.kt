package app.signsetu

import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.glossToText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Gloss back to readable text: the draft an officer reads after the subject signs. */
class GlossReadBackHardeningTest {

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
