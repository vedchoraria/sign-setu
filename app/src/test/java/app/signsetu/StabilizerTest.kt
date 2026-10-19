package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.Stabilizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StabilizerTest {
    private fun hand(x: Float) = List(21) { Offset(x, 0.5f) }

    @Test
    fun shortDropoutIsHeldAndLongOneIsNot() {
        val s = Stabilizer()
        s.mark(LandmarkFrame(0, rightHand = hand(0.5f)))
        assertNotNull(s.mark(LandmarkFrame(33)).rightHand)
        assertNull(s.mark(LandmarkFrame(300)).rightHand)
    }

    @Test
    fun jitterIsDampedButFastMotionPassesThrough() {
        val s = Stabilizer()
        s.mark(LandmarkFrame(0, rightHand = hand(0.5f)))
        val still = s.mark(LandmarkFrame(33, rightHand = hand(0.502f))).rightHand!![0].x
        assertEquals(0.5008f, still, 0.0005f)
        val fast = s.mark(LandmarkFrame(66, rightHand = hand(0.9f))).rightHand!![0].x
        assertEquals(0.9f, fast, 1e-6f)
    }
}
