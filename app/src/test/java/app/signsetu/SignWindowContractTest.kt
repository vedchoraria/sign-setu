package app.signsetu

import app.signsetu.camera.analysisDue
import app.signsetu.sign.SignWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Segmentation numbers are a contract shared with the Python evaluator (docs/11 §2, docs/13 §6.1). */
class SignWindowContractTest {

    @Test
    fun answerWindowUsesTheDocumentedPauseAndMinimum() {
        assertEquals(400L, SignWindow.ANSWER.pauseMs)
        assertEquals(200L, SignWindow.ANSWER.minMs)
        assertEquals(20_000L, SignWindow.ANSWER.limitMs)
    }

    @Test
    fun narrativeWindowUsesTheDocumentedPauseAndMinimum() {
        assertEquals(700L, SignWindow.NARRATIVE.pauseMs)
        assertEquals(200L, SignWindow.NARRATIVE.minMs)
        assertEquals(180_000L, SignWindow.NARRATIVE.limitMs)
    }

    @Test
    fun analysisRunsAtMostOnceEveryThirtyMilliseconds() {
        assertTrue(analysisDue(1_000L, 1_030L))
        assertFalse(analysisDue(1_000L, 1_029L))
        assertTrue("the first frame is analysed whatever its timestamp", analysisDue(null, 23_277_574L))
        assertTrue(analysisDue(null, 0L))
    }
}
