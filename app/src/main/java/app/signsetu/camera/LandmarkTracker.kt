package app.signsetu.camera

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** The seam for on-device landmark tracking. The app calls [track] on one background thread with one upright
 * RGBA frame at a time, newest frame only. */
interface LandmarkTracker : AutoCloseable {
    fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame?
    /** Loads the model ahead of the first frame, off the main thread; it analyses nothing. */
    fun warm() {}
    override fun close() {}
}

/** Landmarks of one frame in MediaPipe Holistic topology, normalised to the frame (origin top-left, no
 * mirroring). Hands are the subject's own left and right: 21 points each. Face: 468 points. Pose: 33 points. */
@Immutable
data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: List<Offset>? = null,
    val rightHand: List<Offset>? = null,
    val face: List<Offset>? = null,
    val pose: List<Offset>? = null,
    val aspect: Float = 3f / 4f,
    /** Brows raised above this face's running baseline: ISL marks yes/no questions this way. */
    val browsUp: Boolean = false,
    val mouthOpen: Boolean = false,
    /** A nod, head shake or held thumb that ended on this frame: a proposed yes or no. */
    val head: Head? = null,
    /** A picture card id the subject points at, on the frame the hold completes. */
    val pointsAt: String? = null,
    val stopPalm: Boolean = false,
    val shrug: Boolean = false,
    /** A courtesy held on this frame (hands joined, a wave, a hand on the chest, a bow): a proposed word, never an answer. */
    val everyday: Everyday? = null,
    /** The camera picture behind this frame was smeared; the landmarks are kept, the picture is not. */
    val blurry: Boolean = false,
) {
    val hands: List<List<Offset>> get() = listOfNotNull(leftHand, rightHand)
}

val HandBones = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)

/** Pose points of the arms (shoulders 11, 12; elbows 13, 14; wrists 15, 16), for drawing. */
val ArmBones = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16)

/** Face mesh outlines that carry ISL facial grammar: brows, eyes, lips, and the face oval. */
object FaceOutlines {
    val oval = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10)
    val leftBrow = listOf(276, 283, 282, 295, 285, 300, 293, 334, 296, 336)
    val rightBrow = listOf(46, 53, 52, 65, 55, 70, 63, 105, 66, 107)
    val leftEye = listOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466, 263)
    val rightEye = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33)
    val lips = listOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185, 61)
    val all = listOf(oval, leftBrow, rightBrow, leftEye, rightEye, lips)
}

/** Distance between points [a] and [b] with x scaled by [aspect], so both axes are in one unit. */
private fun List<Offset>.dist(a: Int, b: Int, aspect: Float) = Offset((this[a].x - this[b].x) * aspect, this[a].y - this[b].y).getDistance()

/** Each finger's tip and middle joint, index to little finger. */
private val FINGERS = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)

/** Inner-lip gap over mouth width, and mean brow-to-upper-lid height over eye span; null without a full face mesh. */
fun faceCues(frame: LandmarkFrame): Pair<Float, Float>? {
    val f = frame.face?.takeIf { it.size >= 468 } ?: return null
    fun d(a: Int, b: Int) = f.dist(a, b, frame.aspect)
    val eyes = d(33, 263).takeIf { it > 0f } ?: return null
    val mouth = d(13, 14) / d(61, 291).coerceAtLeast(1e-4f)
    val brows = (d(105, 159) + d(334, 386)) / 2f / eyes
    return mouth to brows
}

/** Per-person brow baseline: faces differ, so "raised" is relative to how this face has looked so far. */
class FaceCueTracker {
    private var base = Float.NaN

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        val (mouth, brows) = faceCues(frame) ?: return frame
        val up = !base.isNaN() && brows > base * BROW_RAISE
        // Raised frames barely move the baseline, so a held question does not become the new neutral.
        base = if (base.isNaN()) brows else base + (brows - base) * (if (up) 0.002f else 0.03f)
        return frame.copy(browsUp = up, mouthOpen = mouth > MOUTH_OPEN)
    }

    private companion object {
        // Calibration knobs; tune on device.
        const val BROW_RAISE = 1.15f
        const val MOUTH_OPEN = 0.28f
    }
}

enum class Head { Nod, Shake }

/** Nods and shakes from the nose tip against the midpoint of the eyes over the last 1.5 s, in eye spans. */
class HeadGestures(private val windowMs: Long = WINDOW_MS, private val swing: Float = SWING) {
    private class Sample(val ms: Long, val along: Float, val across: Float)

    private val track = ArrayDeque<Sample>()

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        val f = frame.face?.takeIf { it.size >= 468 } ?: return frame.also { track.clear() }
        val eyes = f.dist(33, 263, frame.aspect).takeIf { it > 0f } ?: return frame
        val mid = Offset((f[33].x + f[263].x) / 2f, (f[33].y + f[263].y) / 2f)
        // Along and across the eye line, so a tilted head still separates turning from nodding.
        val ex = Offset((f[263].x - f[33].x) * frame.aspect, f[263].y - f[33].y) / eyes
        val n = Offset((f[1].x - mid.x) * frame.aspect, f[1].y - mid.y) / eyes
        track.addLast(Sample(frame.timestampMs, n.x * ex.x + n.y * ex.y, n.y * ex.x - n.x * ex.y))
        while (frame.timestampMs - track.first().ms > windowMs) track.removeFirst()
        if (track.size < 6) return frame
        val dx = spread { it.along }
        val dy = spread { it.across }
        val head = when {
            dy > swing && dy > 2 * dx && turns { it.across } >= 2 -> Head.Nod
            dx > swing && dx > 2 * dy && turns { it.along } >= 2 -> Head.Shake
            else -> null
        } ?: return frame
        track.clear()
        return frame.copy(head = head)
    }

    private inline fun spread(axis: (Sample) -> Float): Float {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (s in track) {
            val v = axis(s)
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        return hi - lo
    }

    // Direction changes, ignoring jitter under a quarter swing.
    private inline fun turns(axis: (Sample) -> Float): Int {
        var dir = 0
        var n = 0
        var anchor = axis(track.first())
        for (s in track) {
            val x = axis(s)
            val d = x - anchor
            if (abs(d) < swing / 4) continue
            val sign = if (d > 0) 1 else -1
            if (dir != 0 && sign != dir) n++
            dir = sign
            anchor = x
        }
        return n
    }

    private companion object {
        const val WINDOW_MS = 1500L
        // Calibration knob: nose travel in eye spans; tune on device.
        const val SWING = 0.12f
    }
}

/** Reports a reading once it has stayed the same, and non-null, for [holdMs]; then nothing until it changes. */
private class Held<T : Any>(private val holdMs: Long) {
    private var held: T? = null
    private var since = 0L

    fun update(now: T?, ms: Long): T? {
        if (now != held) { held = now; since = ms; return null }
        if (now == null || ms - since < holdMs) return null
        since = Long.MAX_VALUE / 2
        return now
    }
}

/** Thumbs up or down held for half a second, read as a nod or shake for people who do not sign. */
class ThumbGestures(holdMs: Long = 500L) {
    private val hold = Held<Head>(holdMs)

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        if (frame.head != null) return frame
        val now = frame.hands.firstNotNullOfOrNull { thumb(it, frame.aspect) }
        return hold.update(now, frame.timestampMs)?.let { frame.copy(head = it) } ?: frame
    }
}

/** Thumb straight up or down with the four fingers curled; distances are in one unit via [aspect]. */
internal fun thumb(h: List<Offset>, aspect: Float): Head? {
    if (h.size < 21) return null
    fun d(a: Int, b: Int) = h.dist(a, b, aspect)
    val curled = FINGERS.all { (tip, pip) -> d(tip, 0) < d(pip, 0) * 1.1f }
    val span = d(0, 9).takeIf { it > 0f } ?: return null
    val rise = (h[2].y - h[4].y) / span
    val side = abs(h[4].x - h[2].x) * aspect / span
    return when {
        !curled || d(4, 2) < span * 0.5f || side > abs(rise) -> null
        rise > 0.5f -> Head.Nod
        rise < -0.5f -> Head.Shake
        else -> null
    }
}

/** A body part the subject points at with one finger and holds for 0.6 s, as a picture card id (head, mouth,
 * stomach, arm, leg): how a child shows where it hurts. Regions come from the pose, in shoulder widths. */
class BodyPointing(holdMs: Long = 600L) {
    private val hold = Held<String>(holdMs)

    fun mark(frame: LandmarkFrame): LandmarkFrame =
        hold.update(region(frame), frame.timestampMs)?.let { frame.copy(pointsAt = it) } ?: frame
}

internal fun region(frame: LandmarkFrame): String? {
    val p = frame.pose?.takeIf { it.size >= 25 } ?: return null
    fun u(o: Offset) = unit(frame, o)
    val sw = shoulderWidth(frame) ?: return null
    val mid = (u(p[11]) + u(p[12])) / 2f
    for ((hand, other) in listOf(frame.leftHand to (12 to 14 to 16), frame.rightHand to (11 to 13 to 15))) {
        val h = hand?.takeIf { it.size >= 21 }?.map(::u) ?: continue
        fun d(a: Int, b: Int) = (h[a] - h[b]).getDistance()
        val pointing = d(8, 0) > d(6, 0) * 1.15f && d(12, 0) < d(10, 0) * 1.1f && d(16, 0) < d(14, 0) * 1.1f
        if (!pointing) continue
        val tip = h[8]
        val mouth = (u(p[9]) + u(p[10])) / 2f
        val hips = if (p[23].y in 0f..1f && p[24].y in 0f..1f) (u(p[23]) + u(p[24])) / 2f else null
        val (sh, el) = other.first
        val wr = other.second
        return when {
            (tip - mouth).getDistance() < 0.22f * sw -> "mouth"
            (tip - u(p[0])).getDistance() < 0.7f * sw && tip.y < mouth.y -> "head"
            near(tip, u(p[sh]), u(p[el]), 0.2f * sw) || near(tip, u(p[el]), u(p[wr]), 0.2f * sw) -> "arm"
            hips != null && tip.y > hips.y + 0.15f * sw -> "leg"
            tip.y > mid.y + 0.55f * sw && (hips == null || tip.y < hips.y + 0.15f * sw) && abs(tip.x - mid.x) < 0.6f * sw -> "stomach"
            else -> null
        }
    }
    return null
}

/** Whether [p] lies within [r] of the segment [a] to [b]. */
private fun near(p: Offset, a: Offset, b: Offset, r: Float): Boolean {
    val ab = b - a
    val len = ab.x * ab.x + ab.y * ab.y
    if (len < 1e-8f) return (p - a).getDistance() < r
    val t = (((p - a).x * ab.x + (p - a).y * ab.y) / len).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance() < r
}

/** An open palm raised to the face or chest and held 0.8 s: the everyday "stop", proposed as Stop. */
class PalmGestures(holdMs: Long = 800L) {
    private val hold = Held<Boolean>(holdMs)

    fun mark(frame: LandmarkFrame): LandmarkFrame =
        if (hold.update(raisedPalm(frame).takeIf { it }, frame.timestampMs) != null) frame.copy(stopPalm = true) else frame
}

internal fun raisedPalm(frame: LandmarkFrame): Boolean {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return false
    val shoulders = (p[11].y + p[12].y) / 2f
    val sw = shoulderWidth(frame) ?: return false
    return frame.hands.any { h ->
        if (h.size < 21) return@any false
        fun d(a: Int, b: Int) = h.dist(a, b, frame.aspect)
        val open = FINGERS.all { (tip, pip) -> d(tip, 0) > d(pip, 0) * 1.1f } && d(4, 0) > d(3, 0)
        val upright = h[12].y < h[0].y - 0.5f * d(0, 9)
        open && upright && h[0].y < shoulders + 0.6f * sw
    }
}

/** Both hands open, held low and wide apart for 0.6 s: the shrug many people use for "I don't know". */
class ShrugGestures(holdMs: Long = 600L) {
    private val hold = Held<Boolean>(holdMs)

    fun mark(frame: LandmarkFrame): LandmarkFrame =
        if (hold.update(shrug(frame).takeIf { it }, frame.timestampMs) != null) frame.copy(shrug = true) else frame
}

internal fun shrug(frame: LandmarkFrame): Boolean {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return false
    val l = frame.leftHand?.takeIf { it.size >= 21 } ?: return false
    val r = frame.rightHand?.takeIf { it.size >= 21 } ?: return false
    fun u(o: Offset) = unit(frame, o)
    val sw = shoulderWidth(frame) ?: return false
    val shoulders = (p[11].y + p[12].y) / 2f
    fun open(h: List<Offset>): Boolean {
        fun d(a: Int, b: Int) = (u(h[a]) - u(h[b])).getDistance()
        return FINGERS.all { (tip, pip) -> d(tip, 0) > d(pip, 0) * 1.1f }
    }
    // Low (below the shoulders), wide (wrists further apart than the shoulders), fingers out to the sides, not up.
    fun flat(h: List<Offset>) = abs(u(h[12]).y - u(h[0]).y) < 0.6f * (u(h[12]) - u(h[0])).getDistance()
    return open(l) && open(r) && flat(l) && flat(r) && l[0].y > shoulders && r[0].y > shoulders &&
        (u(l[0]) - u(r[0])).getDistance() > 1.2f * sw
}

/** Courteous gestures that are neither signs nor one of the stage's answer cards. */
enum class Everyday { NAMASTE, WAVE, THANKS, BOW }

/** The everyday courtesies people offer with or without a sign language, each held long enough to be deliberate:
 * hands joined at the chest for [Everyday.NAMASTE], an open hand swinging for [Everyday.WAVE], a hand laid on the
 * chest for [Everyday.THANKS] and a shallow [Everyday.BOW]. Every one is a proposal; only the subject's tap logs it. */
class EverydayGestures(private val holdMs: Long = 480L) {
    private val hold = Held<Everyday>(holdMs)
    private val wave = WaveDetector()
    private val bow = BowDetector()

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        if (frame.everyday != null) return frame
        // One at a time, most specific first: joined hands are never read as a wave or a hand on the chest.
        val seen = when {
            namaste(frame) -> Everyday.NAMASTE
            handOnChest(frame) -> Everyday.THANKS
            wave.mark(frame) != null -> Everyday.WAVE
            bow.mark(frame) != null -> Everyday.BOW
            else -> null
        }
        return hold.update(seen, frame.timestampMs)?.let { frame.copy(everyday = it) } ?: frame
    }
}

private fun unit(frame: LandmarkFrame, o: Offset) = Offset(o.x * frame.aspect, o.y)

/** Shoulder width in aspect-corrected units; null without shoulders to measure against. */
private fun shoulderWidth(frame: LandmarkFrame): Float? {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return null
    return (unit(frame, p[11]) - unit(frame, p[12])).getDistance().takeIf { it > 1e-4f }
}

/** Both hands joined palm to palm at the chest, fingers up: the namaste greeting. */
internal fun namaste(frame: LandmarkFrame): Boolean {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return false
    val l = frame.leftHand?.takeIf { it.size >= 21 } ?: return false
    val r = frame.rightHand?.takeIf { it.size >= 21 } ?: return false
    val sw = shoulderWidth(frame) ?: return false
    val lh = l.map { unit(frame, it) }
    val rh = r.map { unit(frame, it) }
    val midX = (unit(frame, p[11]).x + unit(frame, p[12]).x) / 2f
    val shoulders = (p[11].y + p[12].y) / 2f
    // The wrists meet at the body's midline, between the chin and the belly.
    val joined = (lh[0] - rh[0]).getDistance() < 0.45f * sw && abs((lh[0].x + rh[0].x) / 2f - midX) < 0.5f * sw
    val chest = lh[0].y > shoulders - 0.25f * sw && lh[0].y < shoulders + 1.1f * sw
    // Fingers point up out of each wrist, so two hands hanging by the sides are not a greeting.
    fun upright(h: List<Offset>) = h[0].y - h[12].y > 0.35f * (h[12] - h[0]).getDistance().coerceAtLeast(1e-4f)
    return joined && chest && upright(lh) && upright(rh)
}

/** An open hand laid flat on the chest: thanks, and the hand-on-heart many people use for the same thing. */
internal fun handOnChest(frame: LandmarkFrame): Boolean {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return false
    val sw = shoulderWidth(frame) ?: return false
    val midX = (unit(frame, p[11]).x + unit(frame, p[12]).x) / 2f
    val shoulders = (p[11].y + p[12].y) / 2f
    return frame.hands.any { h ->
        if (h.size < 21) return@any false
        val hh = h.map { unit(frame, it) }
        fun d(a: Int, b: Int) = (hh[a] - hh[b]).getDistance()
        val flat = FINGERS.all { (tip, pip) -> d(tip, 0) > d(pip, 0) * 1.05f }
        flat && hh[12].y < hh[0].y && abs(hh[0].x - midX) < 0.4f * sw &&
            hh[0].y > shoulders + 0.15f * sw && hh[0].y < shoulders + 1.0f * sw
    }
}

/** An open hand above the shoulder line swinging side to side, twice in [windowMs]: a wave. Once the swings are
 * seen the hand stays waving for [latchMs], so the caller can judge how long the greeting lasted. */
private class WaveDetector(
    private val windowMs: Long = 1500L,
    private val swing: Float = 0.22f,
    private val latchMs: Long = 700L,
) {
    private val track = ArrayDeque<Pair<Long, Float>>()
    private var wavingUntil = 0L

    fun mark(frame: LandmarkFrame): Everyday? {
        val x = raisedHandX(frame)
        if (x == null) {
            track.clear()
            wavingUntil = 0L
            return null
        }
        track.addLast(frame.timestampMs to x)
        while (frame.timestampMs - track.first().first > windowMs) track.removeFirst()
        if (track.size >= 5 && track.maxOf { it.second } - track.minOf { it.second } >= swing) {
            var dir = 0
            var turns = 0
            var anchor = track.first().second
            for ((_, v) in track) {
                val d = v - anchor
                if (abs(d) < swing / 4) continue
                val sign = if (d > 0) 1 else -1
                if (dir != 0 && sign != dir) turns++
                dir = sign
                anchor = v
            }
            if (turns >= 2) wavingUntil = frame.timestampMs + latchMs
        }
        return Everyday.WAVE.takeIf { frame.timestampMs < wavingUntil }
    }
}

/** The wrist x of an open hand held above the shoulder line, in shoulder widths; null otherwise. */
private fun raisedHandX(frame: LandmarkFrame): Float? {
    val p = frame.pose?.takeIf { it.size >= 13 } ?: return null
    val sw = shoulderWidth(frame) ?: return null
    val shoulders = (p[11].y + p[12].y) / 2f
    for (h in frame.hands) {
        if (h.size < 21) continue
        val hh = h.map { unit(frame, it) }
        fun d(a: Int, b: Int) = (hh[a] - hh[b]).getDistance()
        val open = FINGERS.all { (tip, pip) -> d(tip, 0) > d(pip, 0) * 1.1f }
        val upright = hh[12].y < hh[0].y - 0.3f * d(0, 9)
        if (open && upright && hh[0].y < shoulders) return hh[0].x / sw
    }
    return null
}

/** The head dropping toward the shoulders and staying there: a shallow bow. The baseline drifts slowly, so sitting
 * closer to or further from the camera is not read as one, and a nod is too quick to count. */
private class BowDetector(
    private val low: Float = 0.78f,
    private val high: Float = 0.9f,
) {
    private var base = 0f
    private var down = false

    fun mark(frame: LandmarkFrame): Everyday? {
        val p = frame.pose?.takeIf { it.size >= 13 } ?: return null
        val sw = shoulderWidth(frame) ?: return null
        val nose = frame.face?.takeIf { it.size > 1 }?.let { unit(frame, it[1]) } ?: unit(frame, p[0])
        val shoulders = (p[11].y + p[12].y) / 2f
        val lift = (shoulders - nose.y) / sw
        if (base == 0f) { base = lift; return null }
        // Two thresholds: the head has to drop well below its usual height and rise well above it again, so a slow
        // shift of the phone or a person settling into a chair is not read as repeated bows.
        if (down) {
            if (lift > base * high) down = false
        } else if (lift < base * low) {
            down = true
        }
        // The reference drifts slowly, and only while the head is up, so a held bow cannot become the new normal.
        if (!down) base = 0.995f * base + 0.005f * lift
        return Everyday.BOW.takeIf { down }
    }
}

/** Open fingers across both hands, thumbs included, as a count shown for an age or a number; null outside 1..10. */
fun countOpenFingers(frame: LandmarkFrame): Int? {
    var count = 0
    for (h in frame.hands) {
        if (h.size < 21) continue
        fun d(a: Int, b: Int) = h.dist(a, b, frame.aspect)
        // A thumb is open when its tip is further than its base from both the little-finger knuckle and the wrist.
        if (d(4, 17) > d(2, 17) * 1.15f && d(4, 0) > d(2, 0) * 1.10f) count++
        for ((tip, pip) in FINGERS) if (d(tip, 0) > d(pip, 0) * 1.12f) count++
    }
    return count.takeIf { it in 1..10 }
}
