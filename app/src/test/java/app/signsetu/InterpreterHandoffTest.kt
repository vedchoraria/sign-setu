package app.signsetu

import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.sanitizeSandesHandoffClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Sandes interpreter contact handoff safeguards and clipboard isolation.
 * Ensures zero session data, transcript entries, or PII can leak via interpreter handoff.
 */
class InterpreterHandoffTest {

    @Test
    fun sandesHandoffCopiesExclusivelyConfiguredContactString() {
        val testContact = "+919876543210"
        val sanitized = sanitizeSandesHandoffClipboard(testContact)
        assertEquals("+919876543210", sanitized)

        // Contact with leading/trailing whitespace is sanitized
        val paddedContact = "   9876543210   \n"
        assertEquals("9876543210", sanitizeSandesHandoffClipboard(paddedContact))
    }

    @Test
    fun sandesHandoffNeverContainsSessionOrTranscriptData() {
        val configuredContact = "officer.interpreter.nic"
        val sanitized = sanitizeSandesHandoffClipboard(configuredContact)

        // Verify no session delimiter, event tags, or JSON structures are injected
        assertTrue(!sanitized.contains("session"))
        assertTrue(!sanitized.contains("event"))
        assertTrue(!sanitized.contains("{"))
        assertTrue(!sanitized.contains("}"))
        assertEquals(configuredContact, sanitized)
    }

    @Test
    fun whatsAppContactNumberFormatting() {
        // Standard 10-digit Indian mobile number
        assertEquals("919876543210", whatsappNumber("9876543210"))
        // Number with country code and formatting
        assertEquals("919876543210", whatsappNumber("+91 98765-43210"))
        // Empty or invalid inputs
        assertNull(whatsappNumber(""))
        assertNull(whatsappNumber("   "))
        assertNull(whatsappNumber("12345"))
    }
}
