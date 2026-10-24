package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.analysisDue
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignQuiet
import app.signsetu.sign.STILL_SPEED
import app.signsetu.sign.SignWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SignCaptureTest {

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

    @Test
    fun answerWindowMatchesThePythonEvaluator() {
        // tools/ml/eval_video.py scores what ships only while its segmentation numbers equal the app's.
        val py = File("../tools/ml/eval_video.py").readLines()
        fun const(name: String) = py.first { it.startsWith("$name = ") }.substringAfter("= ").replace("_", "").trim()
        assertEquals(const("PAUSE_MS").toLong(), SignWindow.ANSWER.pauseMs)
        assertEquals(const("MIN_MS").toLong(), SignWindow.ANSWER.minMs)
        assertEquals(const("LIMIT_MS").toLong(), SignWindow.ANSWER.limitMs)
        assertEquals(const("STILL_SPEED").toFloat(), STILL_SPEED, 0f)
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


    /** One frame every 33 ms; each script entry is a hand x position, or null for no hand. */
    private fun frame(i: Int, x: Float?) = LandmarkFrame(i * 33L, rightHand = x?.let(::hand))


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
        // A held sign closes 400 ms after it stops moving; settling must still give the person the full two seconds.
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
