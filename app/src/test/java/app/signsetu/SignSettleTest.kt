package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignQuiet
import app.signsetu.sign.SignWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a sign window ends itself: three seconds with nothing raised, or two since the hands stopped moving.
 * The point of it is that what was given is ingested without anyone tapping Done.
 */
class SignSettleTest {

    private fun hand(x: Float) = List(21) { Offset(x + it * 0.01f, 0.5f) }

    /** One frame every 33 ms; each script entry is a hand x position, or null for no hand. */
    private fun frame(i: Int, x: Float?) = LandmarkFrame(i * 33L, rightHand = x?.let(::hand))

    private fun moving(n: Int, from: Float = 0.2f) = List<Float?>(n) { from + it * 0.03f }
    private fun still(n: Int, at: Float = 0.5f) = List<Float?>(n) { at }
    private fun gone(n: Int) = List<Float?>(n) { null }

    /** The first frame on which [settled] turned true, or null if it never did. */
    private fun settledAt(capture: SignCapture, frames: List<LandmarkFrame>): LandmarkFrame? =
        frames.firstOrNull { f -> capture.add(f); capture.settled() }

    @Test
    fun anEmptyWindowSettlesAfterThreeSecondsWithoutASign() {
        val quiet = SignQuiet()
        assertFalse("nothing seen yet", quiet.settled(0))
        quiet.see(0, moved = false)
        assertFalse("2.9 s of grace is not enough", quiet.settled(2_900))
        assertTrue("3 s is", quiet.settled(3_000))
    }

    @Test
    fun aMigratingQuietTimerStartsAtTheLastMovingFrame() {
        val quiet = SignQuiet(firstSignMs = 3_000, quietMs = 2_000)
        quiet.see(0, moved = false)
        quiet.see(500, moved = true)
        assertFalse("0.5 s quiet", quiet.settled(1_000))
        // A twitch at 1.5 s is a new last movement; the quiet timer starts again from there.
        quiet.see(1_500, moved = true)
        assertFalse("1.9 s after the twitch", quiet.settled(3_400))
        assertTrue("2 s after the twitch", quiet.settled(3_500))
    }

    @Test
    fun aNewWindowDoesNotInheritTheLastOnesGrace() {
        val quiet = SignQuiet()
        quiet.see(0, moved = false)
        assertTrue(quiet.settled(3_000))
        quiet.reset()
        assertFalse("a fresh window", quiet.settled(9_000))
        quiet.see(9_000, moved = false)
        assertFalse("grace starts with its first frame", quiet.settled(9_500))
        assertTrue(quiet.settled(12_000))
    }

    @Test
    fun aSignIsHandedOutBeforeTheWindowSettles() {
        val capture = SignCapture(SignWindow.ANSWER, limitMs = Long.MAX_VALUE)
        val script = gone(3) + moving(15) + gone(70)
        val frames = script.mapIndexed(::frame)
        val handed = ArrayList<List<LandmarkFrame>>()
        var settle: LandmarkFrame? = null
        for (f in frames) {
            handed += capture.add(f)
            if (settle == null && capture.settled()) settle = f
        }
        assertEquals("the one sign is read", 1, handed.size)
        val lastMoved = frames.first { it.rightHand != null && it.timestampMs >= 400 }.timestampMs
        assertTrue("the window did settle", settle != null)
        val quietFor = settle!!.timestampMs - handed.single().last().timestampMs
        assertTrue("settled $quietFor ms after the sign", quietFor >= 2_000)
        assertTrue("not much later than 2 s", quietFor < 2_100)
        assertTrue(lastMoved > 0)
    }

    @Test
    fun theTwoSecondsAreCountedFromTheHandsStoppingNotTheSignClosing() {
        // A held sign closes 300 ms after it stops moving; settling must still give the person the full two seconds.
        val capture = SignCapture(SignWindow.ANSWER, limitMs = Long.MAX_VALUE)
        val script = gone(3) + moving(12) + still(70)
        val frames = script.mapIndexed(::frame)
        val settled = settledAt(capture, frames)
        assertTrue("the window settled", settled != null)
        val closedAt = frames[14].timestampMs
        assertTrue("settled at ${settled!!.timestampMs - closedAt} ms", settled.timestampMs - closedAt >= 2_000)
    }

    @Test
    fun twoSecondsOfSilenceMidAnswerDoesNotSplitItButTheWindowStillEnds() {
        // A person who pauses for three seconds has finished; the signs already read are kept, not thrown away.
        val capture = SignCapture(SignWindow.ANSWER, limitMs = Long.MAX_VALUE)
        val script = gone(3) + moving(12) + gone(70) + moving(12) + still(3)
        val frames = script.mapIndexed(::frame)
        val handed = ArrayList<List<LandmarkFrame>>()
        var settle: LandmarkFrame? = null
        for (f in frames) {
            handed += capture.add(f)
            if (settle == null && capture.settled()) settle = f
        }
        assertEquals("only the first sign is read", 1, handed.size)
        assertTrue("and then the window ends", settle != null)
    }

    @Test
    fun aNarrativeIsGivenLongerToThink() {
        val narrative = SignCapture(SignWindow.NARRATIVE, limitMs = Long.MAX_VALUE)
        val frames = (gone(3) + moving(12) + gone(70)).mapIndexed(::frame)
        assertTrue("a short answer would have ended", SignCapture().let { c -> frames.any { f -> c.add(f); c.settled() } })
        assertFalse("five seconds is the narrative's patience", frames.any { f -> narrative.add(f); narrative.settled() })
        val later = (3 + 12 + 70 until 3 + 12 + 180).map { frame(it, null) }
        assertTrue("and it settles after five", later.any { f -> narrative.add(f); narrative.settled() })
    }

    @Test
    fun aWindowThatFilledItsCapDoesNotAlsoSettle() {
        val capture = SignCapture(SignWindow.ANSWER, limitMs = 500)
        val frames = (0 until 40).map { frame(it, 0.2f + it * 0.03f) }
        frames.forEach(capture::add)
        assertTrue("capped", capture.full)
        assertFalse("the cap closes the window once", capture.settled())
    }
}
