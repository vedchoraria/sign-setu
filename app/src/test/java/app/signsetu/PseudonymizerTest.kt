package app.signsetu

import app.signsetu.assistant.Pseudonymizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudonymizerTest {
    @Test
    fun namesAreMaskedAndRestored() {
        val p = Pseudonymizer(listOf("Sita Devi", "Mohan"))
        val sent = p.mask("sita said Mohan hit her; call 9876543210")
        assertFalse(sent.contains("Sita", ignoreCase = true))
        assertFalse(sent.contains("Mohan"))
        assertFalse(sent.contains("9876543210"))
        assertEquals("Ravi said Ravi 2 hit her; call [number 3]", sent)
        assertEquals("Sita Devi said Mohan hit her; call 9876543210", p.restore(sent))
    }

    @Test
    fun phoneNumbersInCommonWrittenFormsAreMasked() {
        listOf("+91 98765 43210", "98765-43210", "(0771) 2345678", "९८७६५४३२१०", "9876 543 210").forEach { number ->
            val p = Pseudonymizer(emptyList())
            val sent = p.mask("call $number now")
            assertEquals("call [number 1] now", sent)
            assertEquals("call $number now", p.restore(sent))
        }
    }

    @Test
    fun aadhaarPanPostcodeEmailAndLinkAreMasked() {
        val p = Pseudonymizer(emptyList())
        val text = "Aadhaar 1234 5678 9012, PAN ABCDE1234F, pin 492001, mail sita.devi@example.co.in, see https://example.org/x?id=7"
        val sent = p.mask(text)
        listOf("1234", "ABCDE1234F", "492001", "sita.devi@example.co.in", "example.org").forEach { assertFalse("leaked $it in $sent", sent.contains(it)) }
        assertEquals(text, p.restore(sent))
    }

    @Test
    fun shortNumbersThatAreNotIdentifiersSurviveSoTranslationStaysUseful() {
        val p = Pseudonymizer(emptyList())
        val text = "She is 9 years old, in class 4, with 2 brothers at 10:30"
        assertEquals(text, p.mask(text))
    }

    @Test
    fun multiPartNamesAreMaskedWholeAndByPart() {
        val p = Pseudonymizer(listOf("Ram Kumar Verma"))
        val sent = p.mask("Ram Kumar Verma, called Verma, and Kumar's friend Ram")
        assertEquals("Ravi, called Ravi, and Ravi's friend Ravi", sent)
        assertEquals("Ram Kumar Verma", p.restore("Ravi"))
    }

    @Test
    fun devanagariNamesAreMaskedButNotInsideALongerWord() {
        val p = Pseudonymizer(listOf("सीता देवी", "राम"))
        assertEquals("Ravi ने Ravi 2 को बुलाया", p.mask("सीता ने राम को बुलाया"))
        // A matra after the name makes a different word, not the name.
        assertEquals("रामायण और रामा", p.mask("रामायण और रामा"))
    }

    @Test
    fun otherDetailsTheSubjectGaveAreMaskedAsDetails() {
        val p = Pseudonymizer(listOf("Sita"), details = listOf("Kondagaon", "Govt Primary School", "9"))
        val sent = p.mask("Sita goes to Govt Primary School in Kondagaon, aged 9")
        assertEquals("Ravi goes to [detail 2] in [detail 1], aged 9", sent)
        assertEquals("Sita goes to Govt Primary School in Kondagaon, aged 9", p.restore(sent))
    }

    @Test
    fun aNameIsNotAlsoTreatedAsADetail() {
        val p = Pseudonymizer(listOf("Sita"), details = listOf("Sita", "Raipur"))
        assertEquals("Ravi lives in [detail 1]", p.mask("Sita lives in Raipur"))
    }

    @Test
    fun whatItDoesNotKnowPassesThrough() {
        val p = Pseudonymizer(listOf("Sita"))
        val text = "She lives in the red house behind the temple in Raipur and her uncle Bablu hits her"
        val sent = p.mask(text)
        assertTrue("an unknown name and place are not masked, by design", sent.contains("Bablu") && sent.contains("Raipur"))
    }

    @Test
    fun aStandInInTheReplyIsRestoredToTheRightPerson() {
        val p = Pseudonymizer(listOf("Sita", "Mohan"))
        p.mask("Sita and Mohan")
        assertEquals("Mohan said Sita is safe", p.restore("Ravi 2 said Ravi is safe"))
    }
}
