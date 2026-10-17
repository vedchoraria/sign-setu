package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AslDamageTest {
    private fun bundle(vararg rejected: String) = ContentBundle(
        groups = emptyList(), questions = emptyList(), categories = emptyList(), cards = emptyMap(), lexicon = emptyMap(),
        languages = emptyList(), subjectUi = emptyMap(), rejected = rejected.toSet(),
    )

    @Test
    fun rejectedAslWordsDisablesAslOnly() {
        val b = bundle(SignLanguage.ASL.words)
        assertFalse("ISL must still start", b.damaged)
        assertTrue(b.aslDisabled)
    }

    @Test
    fun rejectedAslSignsDisablesAslOnly() {
        val b = bundle(SignLanguage.ASL.signs)
        assertFalse(b.damaged)
        assertTrue(b.aslDisabled)
    }

    @Test
    fun rejectedSharedTextFileStillBlocksSessions() {
        val b = bundle("words.json")
        assertTrue(b.damaged)
        assertFalse(b.aslDisabled)
    }
}
