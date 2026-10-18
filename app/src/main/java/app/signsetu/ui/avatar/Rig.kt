package app.signsetu.ui.avatar

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * A 2D upper-body rig for the two characters (05-avatar-and-gestures §5.1).
 * Character space is 200 x 200 units, y down, the character facing the viewer: the character's right hand is on
 * the viewer's left. Gestures are keyframes in assets/content/gestures.json, written in character terms
 * ("outward", "own right cheek"), so both characters play the same file.
 */

enum class Side(val sign: Float) { R(-1f), L(1f) }

/** Finger curls run from 0 (straight) to 1 (fully bent); `thumbAcross` moves the thumb from beside the hand over the palm. */
data class HandShape(
    val thumb: Float, val index: Float, val middle: Float, val ring: Float, val pinky: Float,
    val thumbAcross: Float = 0f, val spread: Float = 0f,
) {
    val curls get() = floatArrayOf(thumb, index, middle, ring, pinky)

    companion object {
        val ALL: Map<String, HandShape> = mapOf(
            "RELAX" to HandShape(0.25f, 0.2f, 0.28f, 0.34f, 0.4f, 0.2f),
            "FLAT" to HandShape(0f, 0f, 0f, 0f, 0f, 0.15f),
            "OPEN" to HandShape(0f, 0f, 0f, 0f, 0f, 0f, 1f),
            "FIST" to HandShape(0.55f, 1f, 1f, 1f, 1f, 1f),
            "POINT" to HandShape(0.55f, 0f, 1f, 1f, 1f, 1f),
            "TWO" to HandShape(0.55f, 0f, 0f, 1f, 1f, 1f, 0.6f),
            "THREE" to HandShape(0.55f, 0f, 0f, 0f, 1f, 1f, 0.4f),
            "FOUR" to HandShape(0.9f, 0f, 0f, 0f, 0f, 1f, 0.35f),
            "PINKY" to HandShape(0.55f, 1f, 1f, 1f, 0f, 1f),
            "THUMB" to HandShape(0f, 1f, 1f, 1f, 1f, 0f),
            "Y" to HandShape(0f, 1f, 1f, 1f, 0f, 0f, 0.4f),
            "BUNCH" to HandShape(0.45f, 0.5f, 0.5f, 0.5f, 0.52f, 0.7f, -1f),
            "CUP" to HandShape(0.3f, 0.35f, 0.35f, 0.38f, 0.4f, 0.35f, -0.3f),
            "PINCH" to HandShape(0.45f, 0.55f, 1f, 1f, 1f, 0.6f),
            "CLAW" to HandShape(0.35f, 0.6f, 0.6f, 0.62f, 0.65f, 0.3f, 0.3f),
            "SALUTE" to HandShape(0.1f, 0f, 0f, 0f, 0f, 0.6f, -0.2f),
            "WALK_A" to HandShape(0.55f, 0f, 0.45f, 1f, 1f, 1f, 0.4f),
            "WALK_B" to HandShape(0.55f, 0.45f, 0f, 1f, 1f, 1f, 0.4f),
            "RUB" to HandShape(0.7f, 0.55f, 0.55f, 0.6f, 0.65f, 0.9f, -0.6f),
            "HOOK" to HandShape(0.55f, 0.5f, 1f, 1f, 1f, 1f),
        )
    }
}

@Serializable
data class HandKey(
    /** Anchor on the body ("mouth", "cheek", "chest") or "rest". Sided anchors use this hand's side. */
    val at: String = "rest",
    /** Offset in units; +dx is outward, towards this hand's own side. */
    val dx: Float = 0f,
    val dy: Float = 0f,
    val shape: String = "RELAX",
    /** 1 palm towards the viewer, 0 seen from the thumb side, -1 back of the hand towards the viewer. */
    val roll: Float = -1f,
    /** Degrees; 0 is fingers up, positive tips the fingers outward. Null follows the forearm. */
    val angle: Float? = null,
    val elbow: String = "out",
    /** A recorded elbow as an offset from this side's shoulder, +ex outward; replaces [elbow] when both keys have it. */
    val ex: Float? = null,
    val ey: Float? = null,
    /** Which part of the hand reaches the anchor: "wrist", "palm", "tip" (index fingertip) or "thumb". */
    val touch: String = "wrist",
    /** Something held, drawn with the hand: "stick". */
    val prop: String? = null,
)

@Serializable
data class HeadKey(val tilt: Float = 0f, val nod: Float = 0f, val turn: Float = 0f)

@Serializable
data class BodyKey(val lean: Float = 0f, val shrug: Float = 0f, val hunch: Float = 0f, val shiver: Float = 0f)

@Serializable
data class Key(
    val t: Float,
    val R: HandKey? = null,
    val L: HandKey? = null,
    val face: String? = null,
    val head: HeadKey? = null,
    val body: BodyKey? = null,
)

@Serializable
data class Gesture(val id: String, val keys: List<Key>)

/** Facial controls, each roughly 0 to 1; presets in [Face.PRESETS]. */
data class Face(
    val browRaise: Float = 0f,
    val browWorry: Float = 0f,
    val browFrown: Float = 0f,
    val eyeOpen: Float = 1f,
    val eyeSmile: Float = 0f,
    val smile: Float = 0.35f,
    val mouthOpen: Float = 0f,
    val mouthRound: Float = 0f,
) {
    companion object {
        val PRESETS = mapOf(
            "neutral" to Face(),
            "smile" to Face(smile = 0.8f, eyeSmile = 0.25f),
            "happy" to Face(smile = 1f, eyeSmile = 0.6f, mouthOpen = 0.35f, browRaise = 0.2f),
            "ask" to Face(browRaise = 0.85f, smile = 0.2f, mouthOpen = 0.15f, eyeOpen = 1.1f),
            "gentle" to Face(browWorry = 0.45f, smile = 0.45f),
            "concern" to Face(browWorry = 0.8f, smile = -0.25f),
            "wince" to Face(browFrown = 0.8f, eyeOpen = 0.45f, smile = -0.5f, mouthOpen = 0.1f),
            "sleepy" to Face(eyeOpen = 0.1f, smile = 0.3f, browRaise = -0.2f),
            "round" to Face(mouthRound = 0.8f, mouthOpen = 0.4f, browRaise = 0.3f),
            "cold" to Face(browWorry = 0.6f, eyeOpen = 0.6f, smile = -0.3f, mouthOpen = 0.12f),
            "hot" to Face(browWorry = 0.3f, eyeOpen = 0.7f, mouthOpen = 0.3f, smile = 0f),
        )
    }

    fun lerp(o: Face, f: Float) = Face(
        lerp(browRaise, o.browRaise, f), lerp(browWorry, o.browWorry, f), lerp(browFrown, o.browFrown, f),
        lerp(eyeOpen, o.eyeOpen, f), lerp(eyeSmile, o.eyeSmile, f), lerp(smile, o.smile, f),
        lerp(mouthOpen, o.mouthOpen, f), lerp(mouthRound, o.mouthRound, f),
    )
}

/** A resolved hand: wrist position in character space and everything needed to draw it. */
data class HandPose(
    val x: Float, val y: Float,
    val shape: HandShape,
    val roll: Float,
    /** Screen angle in degrees, NaN to follow the forearm. */
    val angle: Float,
    val elbowOut: Float,
    val prop: String? = null,
    /** Recorded elbow minus shoulder on screen, NaN to solve it from [elbowOut]. */
    val elbowX: Float = Float.NaN,
    val elbowY: Float = Float.NaN,
)

/** The elbow for the (possibly shifted) shoulder [sx], [sy]: recorded if the keys carry it, else the two-bone solve. */
fun HandPose.elbowAt(sx: Float, sy: Float, build: Build, side: Side): Pair<Float, Float> =
    if (elbowX.isNaN()) solveElbow(sx, sy, x, y, build.upperArm, build.foreArm, side, elbowOut) else (sx + elbowX) to (sy + elbowY)

/**
 * A point on this hand in character space: "wrist", "palm", "thumb" or a fingertip ("index" to "pinky").
 * Mirrors the frontal layout in Avatar.drawHand; a hand that follows its forearm (NaN angle) is taken as upright.
 */
fun HandPose.point(part: String, side: Side, handScale: Float): Pair<Float, Float> {
    val pw = 13f
    val thumbSide = if (roll > 0f) -side.sign else side.sign
    val curls = shape.curls
    val (hx, hy) = when (part) {
        "wrist" -> 0f to 0f
        "palm" -> 0f to -6f
        "thumb" -> {
            val a = Math.toRadians((thumbSide * (52f - shape.thumbAcross * 112f)).toDouble())
            val len = 8.8f * (1f - 0.4f * curls[0])
            (thumbSide * (pw / 2f - 0.5f) + sin(a).toFloat() * len) to (-3f - cos(a).toFloat() * len)
        }
        else -> {
            val k = listOf("index", "middle", "ring", "pinky").indexOf(part).coerceAtLeast(0)
            val len = floatArrayOf(9.2f, 10.2f, 9.6f, 7.7f)[k] * (1f - 0.8f * curls[k + 1])
            // Seen from the side the fingers stack on the hand's centre line; the extra 1.5 is the rounded tip.
            val across = if (abs(roll) < 0.35f) 0f else thumbSide * (pw / 2f - 1.8f - k * (pw - 3.6f) / 3f)
            across to (-12f - len)
        }
    }
    val a = Math.toRadians((if (angle.isNaN()) 0f else angle).toDouble())
    val rx = (hx * cos(a) - hy * sin(a)).toFloat() * handScale
    val ry = (hx * sin(a) + hy * cos(a)).toFloat() * handScale
    return (x + rx) to (y + ry)
}

data class Pose(
    val r: HandPose, val l: HandPose,
    val face: Face,
    val tilt: Float, val nod: Float, val turn: Float,
    val lean: Float, val shrug: Float, val hunch: Float, val shiver: Float,
    /** 0 looks ahead, 1 looks down at the answers below the character. */
    val gaze: Float = 0f,
    /** The right hand is placed on the left one, so it draws in front. */
    val rFront: Boolean = false,
)

/** Proportions and anchor points of one character. */
class Build(
    val headX: Float, val headY: Float, val headRx: Float, val headRy: Float,
    val neckTop: Float, val neckWidth: Float,
    val shoulderY: Float, val shoulderHalf: Float,
    val upperArm: Float, val foreArm: Float, val armWidth: Float,
    val handScale: Float,
    private val anchors: Map<String, Pair<Float, Float>>,
) {
    /** Sided anchors ("cheek", "ear", "shoulder") are listed for the right side and mirrored for the left. */
    fun anchor(name: String, side: Side): Pair<Float, Float>? {
        val opposite = name.endsWith("_opp")
        val base = name.removeSuffix("_opp")
        val (x, y) = anchors[base] ?: return null
        val s = if (opposite) (if (side == Side.R) Side.L else Side.R) else side
        return if (base in SIDED) (CENTER + (x - CENTER) * (s.sign / Side.R.sign)) to y else x to y
    }

    fun shoulder(side: Side) = (CENTER + side.sign * shoulderHalf) to shoulderY
    fun rest(side: Side) = (CENTER + side.sign * (shoulderHalf - 6f)) to 206f

    companion object {
        const val CENTER = 100f
        val SIDED = setOf("cheek", "ear", "shoulder", "temple", "side", "eye", "hip", "arm", "nose_side", "hair_side")
    }
}

val ChildBuild = Build(
    headX = 100f, headY = 76f, headRx = 40f, headRy = 42f,
    neckTop = 110f, neckWidth = 17f,
    shoulderY = 140f, shoulderHalf = 39f,
    upperArm = 38f, foreArm = 35f, armWidth = 12f,
    handScale = 1.85f,
    anchors = mapOf(
        "hairline" to (100f to 40f), "forehead" to (100f to 56f), "eye" to (84f to 74f), "nose" to (100f to 85f),
        "nose_side" to (95f to 86f), "mouth" to (100f to 99f), "chin" to (100f to 114f), "cheek" to (78f to 92f),
        "ear" to (58f to 80f), "temple" to (66f to 62f), "hair_side" to (64f to 48f), "head_top" to (100f to 30f),
        "chest" to (100f to 156f), "belly" to (100f to 188f), "shoulder" to (61f to 140f), "side" to (56f to 176f),
        "front" to (100f to 168f), "arm" to (58f to 166f), "hip" to (70f to 196f), "low" to (100f to 196f),
    ),
)

val AdultBuild = Build(
    headX = 100f, headY = 70f, headRx = 31f, headRy = 37f,
    neckTop = 104f, neckWidth = 17f,
    shoulderY = 136f, shoulderHalf = 44f,
    upperArm = 41f, foreArm = 38f, armWidth = 13f,
    handScale = 2.0f,
    anchors = mapOf(
        "hairline" to (100f to 38f), "forehead" to (100f to 52f), "eye" to (88f to 67f), "nose" to (100f to 78f),
        "nose_side" to (96f to 80f), "mouth" to (100f to 91f), "chin" to (100f to 104f), "cheek" to (84f to 84f),
        "ear" to (68f to 72f), "temple" to (73f to 58f), "hair_side" to (72f to 46f), "head_top" to (100f to 33f),
        "chest" to (100f to 154f), "belly" to (100f to 188f), "shoulder" to (56f to 136f), "side" to (52f to 178f),
        "front" to (100f to 166f), "arm" to (53f to 168f), "hip" to (66f to 196f), "low" to (100f to 196f),
    ),
)

private val RestKey = HandKey()

/**
 * Plays [parts] one after another as a single gesture (FR-62). Each part's first key spells out everything it
 * leaves out, so it starts from the same pose as when it plays alone rather than holding the previous part's.
 */
fun chain(id: String, parts: List<Gesture>, gap: Float = 0.3f): Gesture {
    var offset = 0f
    var last: Key? = null
    val keys = parts.filter { it.keys.isNotEmpty() }.flatMap { part ->
        val sorted = part.keys.sortedBy { it.t }
        // A part with no hands (a picture beat) holds the previous pose; dropping to rest mid-sentence reads as an end.
        val hold = last?.takeIf { sorted.all { k -> k.R == null && k.L == null } }
        val shifted = if (hold != null) sorted.map { it.copy(R = hold.R, L = hold.L, t = it.t + offset) } else {
            val first = sorted.first().let {
                it.copy(R = it.R ?: RestKey, L = it.L ?: RestKey, face = it.face ?: "neutral", head = it.head ?: HeadKey(), body = it.body ?: BodyKey())
            }
            (listOf(first) + sorted.drop(1)).map { it.copy(t = it.t + offset) }
        }
        offset += sorted.last().t + gap
        last = shifted.last()
        shifted
    }
    return Gesture(id, keys)
}

/** Plays a gesture: eases in from rest, through the keys, and back to rest. */
class Timeline(val gesture: Gesture?, private val build: Build) {
    private val keys: List<Key> = gesture?.keys.orEmpty().sortedBy { it.t }
    val duration: Float = if (keys.isEmpty()) 0f else LEAD_IN + keys.last().t + LEAD_OUT

    fun pose(seconds: Float): Pose {
        if (keys.isEmpty()) return restPose(build)
        val t = seconds - LEAD_IN
        val rest = restFrame()
        val frames = listOf(rest.copy(t = -LEAD_IN)) + filled() + rest.copy(t = keys.last().t + LEAD_OUT)
        val i = frames.indexOfLast { it.t <= t }.coerceAtLeast(0)
        val a = frames[i]
        val b = frames.getOrElse(i + 1) { a }
        if (flows(frames, i)) return flow(frames, i, ((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f))
        // Each move lands a little early and holds, so a sign's shape reads before the next one starts.
        val hold = minOf(0.08f, 0.25f * (b.t - a.t))
        val raw = if (b.t > a.t) ((t - a.t) / (b.t - a.t - hold)).coerceIn(0f, 1f) else 0f
        return blend(a, b, raw)
    }

    private data class Frame(
        val t: Float, val r: HandKey, val l: HandKey, val face: Face, val head: HeadKey, val body: BodyKey,
    )

    private fun restFrame() = Frame(0f, RestKey, RestKey, Face(), HeadKey(), BodyKey())

    /** Each key holds the previous key's values for anything it leaves out. */
    private fun filled(): List<Frame> {
        var prev = restFrame()
        return keys.map { k ->
            Frame(
                t = k.t,
                r = k.R ?: prev.r,
                l = k.L ?: prev.l,
                face = k.face?.let { Face.PRESETS[it] } ?: prev.face,
                head = k.head ?: prev.head,
                body = k.body ?: prev.body,
            ).also { prev = it }
        }
    }

    private fun measured(f: Frame) = f.r.shape.startsWith("c:") || f.l.shape.startsWith("c:")

    /** Recorded keys close together sample one movement, so the hands pass through them without stopping. */
    private fun flows(frames: List<Frame>, i: Int) =
        i in 0 until frames.lastIndex && frames[i + 1].t - frames[i].t < FLOW && measured(frames[i]) && measured(frames[i + 1])

    /** Catmull-Rom through the recorded keys: velocity carries across a key, and is zero where a run starts or ends. */
    private fun flow(frames: List<Frame>, i: Int, u: Float): Pose {
        val base = blend(frames[i], frames[i + 1], u)
        fun key(k: Int, side: Side) = if (side == Side.R) frames[k].r else frames[k].l
        fun track(k: Int, side: Side): FloatArray {
            val key = key(k, side)
            val (x, y) = resolve(key, side, null)
            val s = handShape(key.shape) ?: HandShape.ALL.getValue("RELAX")
            val e = recordedElbow(key, side) ?: solvedElbow(x to y, side, elbow(key.elbow))
            return floatArrayOf(x, y, key.roll, (key.angle ?: 0f) * side.sign, s.thumb, s.index, s.middle, s.ring, s.pinky, s.thumbAcross, s.spread, e.first, e.second)
        }
        fun at(side: Side, h: HandPose): HandPose {
            val p1 = track(i, side)
            val p2 = track(i + 1, side)
            val p0 = if (flows(frames, i - 1)) track(i - 1, side) else null
            val p3 = if (flows(frames, i + 1)) track(i + 2, side) else null
            val t1 = frames[i].t
            val t2 = frames[i + 1].t
            val v = FloatArray(p1.size) { j ->
                val m1 = p0?.let { (p2[j] - it[j]) / (t2 - frames[i - 1].t) } ?: 0f
                val m2 = p3?.let { (it[j] - p1[j]) / (frames[i + 2].t - t1) } ?: 0f
                hermite(p1[j], p2[j], m1 * (t2 - t1), m2 * (t2 - t1), u)
            }
            fun curl(j: Int) = v[j].coerceIn(0f, 1f)
            val rec = recordedElbow(key(i, side), side) != null || recordedElbow(key(i + 1, side), side) != null
            return h.copy(
                elbowX = if (rec) v[11] else h.elbowX, elbowY = if (rec) v[12] else h.elbowY,
                x = v[0], y = v[1], roll = v[2],
                angle = if (key(i, side).angle != null && key(i + 1, side).angle != null) v[3] else h.angle,
                shape = HandShape(curl(4), curl(5), curl(6), curl(7), curl(8), curl(9), v[10].coerceIn(-1f, 1f)),
            )
        }
        return base.copy(r = at(Side.R, base.r), l = at(Side.L, base.l))
    }

    private fun blend(a: Frame, b: Frame, raw: Float): Pose {
        val f = ease(raw)
        // The hand placed on the other one is solved second; fingerspelling places the right hand on the left.
        val rFollows = a.r.at in OTHER_HAND || b.r.at in OTHER_HAND
        val r: HandPose
        val l: HandPose
        if (rFollows) {
            l = hand(a.l, b.l, raw, Side.L)
            r = hand(a.r, b.r, raw, Side.R, l)
        } else {
            r = hand(a.r, b.r, raw, Side.R)
            l = hand(a.l, b.l, raw, Side.L, r)
        }
        return Pose(
            r = r, l = l,
            face = a.face.lerp(b.face, f),
            tilt = lerp(a.head.tilt, b.head.tilt, f), nod = lerp(a.head.nod, b.head.nod, f), turn = lerp(a.head.turn, b.head.turn, f),
            lean = lerp(a.body.lean, b.body.lean, f), shrug = lerp(a.body.shrug, b.body.shrug, f),
            hunch = lerp(a.body.hunch, b.body.hunch, f), shiver = lerp(a.body.shiver, b.body.shiver, f),
            rFront = rFollows,
        )
    }

    private fun hand(a: HandKey, b: HandKey, raw: Float, side: Side, other: HandPose? = null): HandPose {
        val f = ease(raw)
        // A free move overshoots slightly and settles; contacts and moves to or from rest never pass their target.
        val free = b.touch == "wrist" && b.at !in OTHER_HAND && b.at != "rest" && a.at != "rest"
        val m = if (free) easeOutBack(raw) else f
        val pa = resolve(a, side, other)
        val pb = resolve(b, side, other)
        // ISL hook: handShape (MeasuredShape.kt) also reads measured "c:" shapes from isl_signs.json.
        val sa = handShape(a.shape) ?: HandShape.ALL.getValue("RELAX")
        val sb = handShape(b.shape) ?: HandShape.ALL.getValue("RELAX")
        val angleA = a.angle?.let { it * side.sign } ?: Float.NaN
        val angleB = b.angle?.let { it * side.sign } ?: Float.NaN
        val ea = recordedElbow(a, side)
        val eb = recordedElbow(b, side)
        // A recorded elbow next to a solved one starts or ends at the solve, so it moves without a jump.
        val oa = if (eb != null) ea ?: solvedElbow(pa, side, elbow(a.elbow)) else ea
        val ob = if (ea != null) eb ?: solvedElbow(pb, side, elbow(b.elbow)) else eb
        return HandPose(
            x = lerp(pa.first, pb.first, m),
            y = lerp(pa.second, pb.second, m),
            shape = blendShape(sa, sb, f),
            roll = lerp(a.roll, b.roll, f),
            angle = when {
                angleA.isNaN() && angleB.isNaN() -> Float.NaN
                angleA.isNaN() -> if (f < 0.5f) Float.NaN else angleB
                angleB.isNaN() -> if (f < 0.5f) angleA else Float.NaN
                else -> lerp(angleA, angleB, m)
            },
            elbowOut = lerp(elbow(a.elbow), elbow(b.elbow), f),
            prop = if (f < 0.5f) a.prop else b.prop,
            elbowX = if (oa != null && ob != null) lerp(oa.first, ob.first, m) else Float.NaN,
            elbowY = if (oa != null && ob != null) lerp(oa.second, ob.second, m) else Float.NaN,
        )
    }

    private fun recordedElbow(k: HandKey, side: Side) = if (k.ex != null && k.ey != null) k.ex * side.sign to k.ey else null

    /** The two-bone elbow for wrist [p] as an offset from the unshifted shoulder, like a recorded one. */
    private fun solvedElbow(p: Pair<Float, Float>, side: Side, out: Float): Pair<Float, Float> {
        val (sx, sy) = build.shoulder(side)
        val (ex, ey) = solveElbow(sx, sy, p.first, p.second, build.upperArm, build.foreArm, side, out)
        return (ex - sx) to (ey - sy)
    }

    private fun resolve(k: HandKey, side: Side, other: HandPose?): Pair<Float, Float> {
        val base = when {
            k.at == "rest" -> build.rest(side)
            k.at in OTHER_HAND && other != null -> other.point(k.at.removeSuffix("_opp"), if (side == Side.R) Side.L else Side.R, build.handScale)
            else -> build.anchor(k.at, side) ?: build.rest(side)
        }
        // +dx is outward: towards the viewer's left for the right hand.
        return reachBack(k, (base.first + k.dx * side.sign) to (base.second + k.dy), side)
    }

    /** Moves the target back along the hand so the fingertip or palm, not the wrist, lands on the anchor. */
    private fun reachBack(k: HandKey, point: Pair<Float, Float>, side: Side): Pair<Float, Float> {
        val reach = when (k.touch) {
            "palm" -> 6.5f
            "thumb" -> 9f
            "tip" -> 12f + 9.5f * (1f - 0.75f * (handShape(k.shape)?.index ?: 0f))
            else -> return point
        } * build.handScale
        val a = Math.toRadians(((k.angle ?: 0f) * side.sign).toDouble())
        return (point.first - sin(a).toFloat() * reach) to (point.second + cos(a).toFloat() * reach)
    }

    companion object {
        /** Anchors on the other hand: its wrist, palm centre, or a fingertip. */
        val OTHER_HAND = setOf("wrist_opp", "palm_opp", "thumb_opp", "index_opp", "middle_opp", "ring_opp", "pinky_opp")
        const val LEAD_IN = 0.35f
        const val LEAD_OUT = 0.45f
        const val FLOW = 0.35f

        fun restPose(build: Build): Pose {
            val r = build.rest(Side.R)
            val l = build.rest(Side.L)
            val relax = HandShape.ALL.getValue("RELAX")
            return Pose(
                HandPose(r.first, r.second, relax, -1f, Float.NaN, 1f),
                HandPose(l.first, l.second, relax, -1f, Float.NaN, 1f),
                Face(), 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            )
        }

        private fun elbow(name: String) = when (name) {
            "in" -> -1f
            "down" -> 0f
            else -> 1f
        }
    }
}

fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

private fun ease(f: Float) = if (f < 0.5f) 4 * f * f * f else 1 - (-2 * f + 2).let { it * it * it } / 2

private fun hermite(p1: Float, p2: Float, m1: Float, m2: Float, u: Float): Float {
    val u2 = u * u
    val u3 = u2 * u
    return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2
}

private fun easeOutBack(f: Float): Float {
    val g = f - 1f
    return 1f + 2.1f * g * g * g + 1.1f * g * g
}

private fun blendShape(a: HandShape, b: HandShape, f: Float) = HandShape(
    lerp(a.thumb, b.thumb, f), lerp(a.index, b.index, f), lerp(a.middle, b.middle, f), lerp(a.ring, b.ring, f),
    lerp(a.pinky, b.pinky, f), lerp(a.thumbAcross, b.thumbAcross, f), lerp(a.spread, b.spread, f),
)

/** Two-bone arm: returns the elbow for a shoulder, wrist target and segment lengths. */
fun solveElbow(
    sx: Float, sy: Float, tx: Float, ty: Float, upper: Float, fore: Float, side: Side, elbowOut: Float,
): Pair<Float, Float> {
    val dx = tx - sx
    val dy = ty - sy
    val d = hypot(dx, dy).coerceIn(abs(upper - fore) + 0.01f, upper + fore - 0.01f)
    val base = atan2(dy, dx)
    val offset = acos(((upper * upper + d * d - fore * fore) / (2 * upper * d)).coerceIn(-1f, 1f))
    val e1 = (sx + upper * cos(base + offset)) to (sy + upper * sin(base + offset))
    val e2 = (sx + upper * cos(base - offset)) to (sy + upper * sin(base - offset))
    // "Out" picks the elbow further from the body's midline, "in" the nearer one, "down" the lower one.
    val score = { e: Pair<Float, Float> ->
        val outward = (e.first - Build.CENTER) * side.sign
        if (elbowOut >= 0f) outward * elbowOut + e.second * (1f - elbowOut) else -outward
    }
    // A level reach gives two mirror elbows with equal scores; the margin settles float noise on e1 (rig.ts matches).
    return if (score(e1) >= score(e2) - 1e-3f) e1 else e2
}
