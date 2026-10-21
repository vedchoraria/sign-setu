package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.BodyPointing
import app.signsetu.camera.Head
import app.signsetu.camera.HeadGestures
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.PalmGestures
import app.signsetu.camera.ThumbGestures
import app.signsetu.camera.raisedPalm
import app.signsetu.camera.region
import app.signsetu.camera.shrug
import app.signsetu.camera.thumb
import app.signsetu.sign.HomeSigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTest {
    // A right hand with its wrist at w; image y grows downwards. Fingers curled unless open; the thumb points up (1),
    // down (-1), out (0) or out and up but more out than up (2).
    private fun hand(w: Offset, thumb: Int = 0, open: Boolean = false, point: Boolean = false): List<Offset> {
        val p = MutableList(21) { w }
        val xs = listOf(-0.03f, 0f, 0.03f, 0.06f)
        for ((f, x) in xs.withIndex()) {
            val base = 5 + 4 * f
            val straight = open || (point && f == 0)
            p[base] = w + Offset(x, -0.09f)
            p[base + 1] = w + Offset(x, -0.12f)
            p[base + 2] = w + Offset(x, if (straight) -0.15f else -0.10f)
            p[base + 3] = w + Offset(x, if (straight) -0.18f else -0.05f)
        }
        p[1] = w + Offset(-0.04f, -0.03f)
        p[2] = w + Offset(-0.06f, -0.06f)
        when (thumb) {
            1 -> { p[3] = w + Offset(-0.06f, -0.12f); p[4] = w + Offset(-0.06f, -0.18f) }
            -1 -> { p[3] = w + Offset(-0.06f, 0f); p[4] = w + Offset(-0.06f, 0.06f) }
            2 -> { p[3] = w + Offset(-0.13f, -0.10f); p[4] = w + Offset(-0.20f, -0.15f) }
            else -> { p[3] = w + Offset(-0.10f, -0.07f); p[4] = w + Offset(-0.14f, -0.08f) }
        }
        return p
    }

    private fun flipY(h: List<Offset>, w: Offset) = h.map { Offset(it.x, 2 * w.y - it.y) }

    // Shoulders at y 0.4, x 0.35 and 0.65; nose 0.5,0.25; mouth corners 0.47/0.53,0.31; hips at y 0.8.
    private val pose = MutableList(33) { Offset(0.5f, 0.5f) }.apply {
        this[0] = Offset(0.5f, 0.25f); this[9] = Offset(0.47f, 0.31f); this[10] = Offset(0.53f, 0.31f)
        this[11] = Offset(0.65f, 0.4f); this[12] = Offset(0.35f, 0.4f)
        this[13] = Offset(0.7f, 0.55f); this[14] = Offset(0.3f, 0.55f)
        this[15] = Offset(0.68f, 0.68f); this[16] = Offset(0.32f, 0.68f)
        this[23] = Offset(0.6f, 0.8f); this[24] = Offset(0.4f, 0.8f)
    }

    private fun frame(t: Long, right: List<Offset>? = null, face: List<Offset>? = null) =
        LandmarkFrame(t, rightHand = right, pose = pose, face = face, aspect = 1f)

    @Test
    fun thumbUpAndDownReadAsNodAndShake() {
        val w = Offset(0.5f, 0.6f)
        val up = hand(w, thumb = 1)
        assertEquals(Head.Nod, thumb(up, 1f))
        assertEquals(Head.Shake, thumb(flipY(up, w), 1f))
        assertNull(thumb(hand(w, thumb = 0), 1f))
        assertNull(thumb(hand(w, thumb = 2), 1f))
        assertNull(thumb(hand(w, thumb = 1, open = true), 1f))
    }

    @Test
    fun aThumbHeldHalfASecondProposesOnce() {
        val g = ThumbGestures()
        val up = hand(Offset(0.5f, 0.6f), thumb = 1)
        val heads = (0L..1500L step 100).map { g.mark(frame(it, up)).head }
        assertEquals(listOf(Head.Nod), heads.filterNotNull())
        assertEquals(5, heads.indexOf(Head.Nod))
    }

    @Test
    fun pointingFindsMouthHeadAndStomach() {
        fun at(tip: Offset): String? {
            val w = tip + Offset(0f, 0.18f)
            return region(frame(0, hand(w, point = true)))
        }
        assertEquals("mouth", at(Offset(0.5f, 0.31f)))
        assertEquals("head", at(Offset(0.52f, 0.18f)))
        assertEquals("stomach", at(Offset(0.5f, 0.66f)))
        assertNull(region(frame(0, hand(Offset(0.5f, 0.6f)))))
    }

    @Test
    fun aRaisedOpenPalmProposesStopAfterItsHold() {
        val palm = hand(Offset(0.5f, 0.45f), open = true)
        assertTrue(raisedPalm(frame(0, palm)))
        assertFalse(raisedPalm(frame(0, hand(Offset(0.5f, 0.45f)))))
        val g = PalmGestures()
        val stops = (0L..1500L step 100).map { g.mark(frame(it, palm)).stopPalm }
        assertEquals(1, stops.count { it })
        assertEquals(8, stops.indexOf(true))
        assertTrue(BodyPointing().mark(frame(0, palm)).pointsAt == null)
    }

    @Test
    fun openHandsLowAndWideAreAShrug() {
        // Fingers pointing out to the sides: rotate each open hand so its fingers run horizontally.
        fun flatHand(w: Offset, dir: Float) = hand(w, open = true).map { p -> Offset(w.x + dir * (w.y - p.y), w.y + (p.x - w.x) * 0.2f) }
        val left = flatHand(Offset(0.8f, 0.6f), 1f)
        val right = flatHand(Offset(0.2f, 0.6f), -1f)
        assertTrue(shrug(LandmarkFrame(0, leftHand = left, rightHand = right, pose = pose, aspect = 1f)))
        assertFalse(shrug(LandmarkFrame(0, leftHand = hand(Offset(0.6f, 0.6f), open = true), rightHand = hand(Offset(0.4f, 0.6f), open = true), pose = pose, aspect = 1f)))
        // Flat and low but close together is not wide enough.
        assertFalse(shrug(LandmarkFrame(0, leftHand = flatHand(Offset(0.55f, 0.6f), 1f), rightHand = flatHand(Offset(0.45f, 0.6f), -1f), pose = pose, aspect = 1f)))
    }

    @Test
    fun aNoseBobbingUpAndDownIsANod() {
        fun face(dy: Float, dx: Float = 0f) = List(468) { Offset(0.5f, 0.3f) }.toMutableList().apply {
            this[33] = Offset(0.45f, 0.28f); this[263] = Offset(0.55f, 0.28f); this[1] = Offset(0.5f + dx, 0.32f + dy)
        }
        val g = HeadGestures()
        val nod = listOf(0f, 0.02f, 0f, 0.02f, 0f, 0.02f, 0f).mapIndexed { i, d -> g.mark(frame(i * 150L, face = face(d))).head }
        assertEquals(Head.Nod, nod.filterNotNull().firstOrNull())
        val h = HeadGestures()
        val shake = listOf(0f, 0.02f, 0f, 0.02f, 0f, 0.02f, 0f).mapIndexed { i, d -> h.mark(frame(i * 150L, face = face(0f, d))).head }
        assertEquals(Head.Shake, shake.filterNotNull().firstOrNull())
        // Looking down once is not a nod; a nose bobbing on a diagonal is neither.
        fun heads(vararg moves: Pair<Float, Float>) = HeadGestures().let { g -> moves.mapIndexed { i, (dy, dx) -> g.mark(frame(i * 150L, face = face(dy, dx))).head } }
        assertNull(heads(0f to 0f, *Array(6) { 0.02f to 0f }).firstOrNull { it != null })
        assertNull(heads(*Array(7) { (if (it % 2 == 0) 0f else 0.02f).let { d -> d to d } }).firstOrNull { it != null })
        // A wobble: the head rolls, the eye line tilts, the nose stays centred between the eyes.
        fun rolled(a: Float) = List(468) { Offset(0.5f, 0.3f) }.toMutableList().apply {
            val (c, s) = kotlin.math.cos(a) to kotlin.math.sin(a)
            this[33] = Offset(0.5f - 0.05f * c, 0.28f - 0.05f * s); this[263] = Offset(0.5f + 0.05f * c, 0.28f + 0.05f * s)
            this[1] = Offset(0.5f - 0.04f * s, 0.28f + 0.04f * c)
        }
        val w = HeadGestures()
        val wobble = listOf(0f, 0.3f, -0.3f, 0.3f, -0.3f, 0.3f, 0f).mapIndexed { i, a -> w.mark(frame(i * 150L, face = rolled(a))).head }
        assertNull(wobble.firstOrNull { it != null })
    }
}

class SessionSignTest {
    private val pose = List(33) { Offset(0.5f, 0.6f) }.toMutableList().apply { this[11] = Offset(0.6f, 0.4f); this[12] = Offset(0.4f, 0.4f) }

    // A right hand that sweeps from x0 to x1 over 20 frames at height y.
    private fun sweep(x0: Float, x1: Float, y: Float) = (0 until 20).map { i ->
        val x = x0 + (x1 - x0) * i / 19f
        LandmarkFrame(i * 40L, rightHand = List(21) { k -> Offset(x + k * 0.002f, y - k * 0.003f) }, pose = pose, aspect = 1f)
    }

    @Test
    fun aTaughtSignIsOfferedWhenSignedAgainAndOthersAreNot() {
        val home = HomeSigns()
        assertNull(home.match(sweep(0.3f, 0.7f, 0.5f)))
        home.teach(sweep(0.3f, 0.7f, 0.5f), "nani")
        assertEquals("nani", home.match(sweep(0.31f, 0.69f, 0.5f))?.word)
        assertNull(home.match(sweep(0.7f, 0.2f, 0.25f)))
        // With two taught, each repeat is offered the nearer one.
        home.teach(sweep(0.7f, 0.3f, 0.3f), "chacha")
        assertEquals("nani", home.match(sweep(0.31f, 0.69f, 0.5f))?.word)
        assertEquals("chacha", home.match(sweep(0.69f, 0.31f, 0.3f))?.word)
    }
}
