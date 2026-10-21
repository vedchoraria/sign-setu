package app.signsetu

import app.signsetu.camera.BlurMeter
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.laplacianVariance
import app.signsetu.sign.SignGuess
import app.signsetu.sign.decideRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPolicyTest {
    private val hand = List(21) { androidx.compose.ui.geometry.Offset(0.5f, 0.5f) }
    private fun frames(gap: Long, blurry: Boolean = false) = List(10) { LandmarkFrame(it * gap, rightHand = hand, blurry = blurry) }
    private val weak = listOf(SignGuess("water", 0.1f))

    @Test
    fun smearedOrSlowPicturesAskForSlowerSigning() {
        assertFalse(decideRead(frames(33), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
        assertTrue(decideRead(frames(33, blurry = true), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
        assertTrue(decideRead(frames(200), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
    }

    @Test
    fun aSureReadIsNeverBlamedOnThePicture() {
        val sure = listOf(SignGuess("water", 0.9f))
        assertFalse(decideRead(frames(33, blurry = true), sure, null, emptySet(), emptyMap(), emptyMap()).slower)
    }

    @Test
    fun homeSignComesFirstAndCandidatesStopAtThree() {
        val all = listOf("a", "b", "c", "d").map { SignGuess(it, 0.5f) }
        val seg = decideRead(frames(33), all, SignGuess("home", 1f), emptySet(), emptyMap(), emptyMap())
        assertEquals(listOf("home", "a", "b"), seg.guesses!!.map { it.word })
    }

    @Test
    fun edgesReadSharpAndSmearReadsBlurry() {
        val w = 32
        val sharp = IntArray(w * w) { if (it % w % 3 == 0) 0xFFFFFF else 0 }
        val flat = IntArray(w * w) { 0x808080 }
        val s = laplacianVariance(sharp, w, w)
        assertTrue(s > 1000f)
        assertEquals(0f, laplacianVariance(flat, w, w), 1e-3f)
        val m = BlurMeter()
        assertFalse(m.judge(s))
        assertTrue(m.judge(s * 0.1f))
        assertFalse("flat scene alone is not blur", BlurMeter().judge(1f))
    }
}
