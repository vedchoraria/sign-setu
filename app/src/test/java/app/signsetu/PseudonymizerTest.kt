package app.signsetu

import app.signsetu.assistant.Pseudonymizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
