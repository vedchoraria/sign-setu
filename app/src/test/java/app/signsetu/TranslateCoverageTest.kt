package app.signsetu

import app.signsetu.data.content.toHindi
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hindi draft is offered next to every question the officer types, so its recall over the shipped bank is a
 * quality bar, not a nicety: a question with no draft leaves the officer without a caption.
 */
class TranslateCoverageTest {
    private val content = shipped

    private fun english(): List<String> =
        (content.questions.map { it.text["en"].orEmpty() } + content.phrases.flatMap { p -> p.slots.map { it.text["en"].orEmpty() } })
            .map { it.trim() }
            .filter { it.isNotEmpty() && '…' !in it && '%' !in it }

    @Test
    fun everyBankSentenceGetsADraft() {
        val missing = english().filter { content.toHindi(it) == null }
        assertTrue("no Hindi draft for ${missing.size}: ${missing.joinToString(" | ")}", missing.isEmpty())
    }
}
