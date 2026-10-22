package app.signsetu

import app.signsetu.ui.session.titleToKeep
import org.junit.Assert.assertEquals
import org.junit.Test

class RootedHistoryTest {
    @Test
    fun aRootedPhoneKeepsNoTitleSoFinishingDeletes() {
        assertEquals(null, titleToKeep("FIR 12", historyOff = true))
        assertEquals("FIR 12", titleToKeep("FIR 12", historyOff = false))
        assertEquals(null, titleToKeep(null, historyOff = false))
    }
}
