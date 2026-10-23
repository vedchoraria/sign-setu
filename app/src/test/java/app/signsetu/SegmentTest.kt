package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentTest {

    private fun hand(x: Float) = List(21) { Offset(x + it * 0.01f, 0.5f) }

    /** Shoulders at y 0.4, one shoulder width (0.2) wide: the frame a drop to rest is measured against. */
    private val shoulders = List(33) { Offset(if (it == 11) 0.4f else 0.6f, 0.4f) }

    /** Both hands at [y], with a pose so the drop to rest can be judged. */
    private fun posed(i: Int, x: Float, y: Float, aspect: Float = 3f / 4f) = LandmarkFrame(
        i * 33L,
        rightHand = List(21) { Offset(x + it * 0.01f, y) },
        leftHand = List(21) { Offset(0.62f + it * 0.01f, y) },
        pose = shoulders,
        aspect = aspect,
    )

    private fun posedStream(vararg script: Pair<Float, Float>) = script.mapIndexed { i, (x, y) -> posed(i, x, y) }

    /** 5 frames of nothing, a 12-frame sign at chest height, 10 still, then the given trailing motion, then nothing. */
    private fun withTrail(trail: List<Pair<Float, Float>>) =
        (0 until 5).map { LandmarkFrame(it * 33L, pose = shoulders) } +
            posedStream(*(0 until 12).map { 0.4f + it * 0.01f to 0.55f }.toTypedArray()) +
            posedStream(*(0 until 10).map { 0.5f to 0.55f }.toTypedArray()) +
            trail.mapIndexed { i, (x, y) -> posed(27 + i, x, y) } +
            (0 until 20).map { LandmarkFrame((27 + trail.size + it) * 33L, pose = shoulders) }

    /** One frame every 33 ms; each script entry is a hand x position, or null for no hand. */
    private fun stream(vararg script: Float?) = script.mapIndexed { i, x -> LandmarkFrame(i * 33L, rightHand = x?.let(::hand)) }

    private fun moving(n: Int, from: Float = 0.2f) = Array<Float?>(n) { from + it * 0.03f }
    private fun still(n: Int, at: Float = 0.5f) = Array<Float?>(n) { at }
    private fun gone(n: Int) = arrayOfNulls<Float>(n)

    @Test
    fun pausesSplitSigns() {
        // Two signs separated by 0.5 s of resting hands (the jump into the rest pose still counts as moving), then hands gone.
        val frames = stream(*gone(5), *moving(15), *still(15), *moving(15), *gone(20))
        assertEquals(listOf(4..20, 34..49), segmentSigns(frames, final = false))
    }

    @Test
    fun shortHoldsStayInsideOneSign() {
        val frames = stream(*moving(10), *still(6), *moving(10), *gone(20))
        assertEquals(1, segmentSigns(frames, final = false).size)
    }

    @Test
    fun anOpenSignClosesOnlyWhenFinal() {
        val frames = stream(*gone(3), *moving(12))
        assertTrue(segmentSigns(frames, final = false).isEmpty())
        assertEquals(1, segmentSigns(frames, final = true).size)
    }

    @Test
    fun twitchesAreNotSigns() {
        fun signs(frames: List<LandmarkFrame>): Int {
            val capture = SignCapture()
            val captured = frames.flatMap { capture.add(it) } + capture.finish()
            assertEquals(segmentSigns(frames, final = true).size, captured.size)
            return captured.size
        }
        // Too short: four moving frames over 0.13 s.
        assertEquals(0, signs(stream(*gone(5), *moving(4), *gone(20))))
        // Too few moving frames: a hand appears, rests, and jumps once, 0.4 s in all.
        assertEquals(0, signs(stream(*gone(5), *still(11), 0.6f, *still(3, at = 0.6f), *gone(20))))
        assertEquals(1, signs(stream(*gone(5), *moving(8), *gone(20))))
    }

    @Test
    fun theHandsDroppingBackToRestAreNotASign() {
        // Ten frames from chest height to hanging: 330 ms, every wrist more than a shoulder width lower. In a clip
        // that is the signer lowering their arms; read as a sign it is a phantom word after every answer.
        val drop = (0 until 10).map { 0.5f to 0.55f + it * 0.03f }
        val capture = SignCapture()
        val signs = withTrail(drop).flatMap { capture.add(it) } + capture.finish()
        assertEquals("only the sign", 1, signs.size)
    }

    @Test
    fun aShortSignThatEndsAtChestHeightIsKept() {
        // A quick sign that moves up and stays in signing space is not a drop, so it is read.
        val up = (0 until 10).map { 0.5f to 0.55f - it * 0.02f }
        val capture = SignCapture()
        val signs = withTrail(up).flatMap { capture.add(it) } + capture.finish()
        assertEquals("both signs", 2, signs.size)
    }

    @Test
    fun aLandscapeFrameIsMeasuredInItsOwnUnits() {
        // A camera frame is taller than it is wide on a phone, wider on a tablet: y is a share of the height, so a vertical drop must be scaled by the aspect before it is compared with a shoulder width.
        val trail = (0 until 10).map { 0.5f to 0.55f + it * 0.015f }
        val frames = withTrail(trail).map { it.copy(aspect = 4f / 3f) }
        val capture = SignCapture()
        val signs = frames.flatMap { capture.add(it) } + capture.finish()
        assertEquals(2, signs.size)
    }

    @Test
    fun withoutAPoseNothingIsDropped() {
        // Shoulders are what makes a drop measurable; without a pose the same stream keeps its old reading.
        val drop = (0 until 10).map { 0.5f to 0.55f + it * 0.03f }
        val frames = withTrail(drop).map { it.copy(pose = null) }
        val capture = SignCapture()
        val signs = frames.flatMap { capture.add(it) } + capture.finish()
        assertEquals("the drop cannot be judged, so it is kept", 2, signs.size)
    }

    @Test
    fun captureHandsOutEachSignOnceAndStopsAtItsCap() {
        val capture = SignCapture(limitMs = 3_000)
        val handed = stream(*gone(5), *moving(15), *gone(20), *moving(15)).flatMap { capture.add(it) }
        assertEquals(1, handed.size)
        assertEquals(1, capture.finish().size)
        assertTrue(capture.finish().isEmpty())
        val capped = SignCapture(limitMs = 1_000)
        val all = stream(*moving(60)).flatMap { capped.add(it) }
        assertTrue(capped.full)
        assertEquals(1, all.size)
        assertTrue(all[0].last().timestampMs <= 1_000 + 33)
    }
}
