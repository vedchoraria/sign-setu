package app.signsetu.data.content

import kotlinx.serialization.Serializable

/* Keyframe motion as the content files store it; the rig in ui.avatar plays it on either character. */

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

/** The face that marks a question in ISL: raised brows for yes-no, furrowed brows for a question word. */
enum class QuestionKind { YES_NO, WH }

internal val RestKey = HandKey()

/** Plays [parts] one after another as a single gesture. Each part's first key spells out everything it leaves
 * out. */
fun chain(id: String, parts: List<Gesture>, gap: Float = 0.3f): Gesture {
    var offset = 0f
    var last: Key? = null
    val keys = parts.filter { it.keys.isNotEmpty() }.flatMap { part ->
        val sorted = part.keys.sortedBy { it.t }
        // A part with no hands (a picture beat) holds the previous pose; dropping to rest mid-sentence reads as an end.
        val hold = last?.takeIf { sorted.all { k -> k.R == null && k.L == null } }
        val shifted = if (hold != null) sorted.map { it.copy(R = hold.R, L = hold.L, t = it.t + offset) } else {
            val first = sorted.first().let {
                it.copy(
                    R = it.R ?: last?.R ?: RestKey,
                    L = it.L ?: last?.L ?: RestKey,
                    face = it.face ?: last?.face ?: "neutral",
                    head = it.head ?: last?.head ?: HeadKey(),
                    body = it.body ?: last?.body ?: BodyKey(),
                )
            }
            (listOf(first) + sorted.drop(1)).map { it.copy(t = it.t + offset) }
        }
        offset += sorted.last().t + gap
        last = shifted.last()
        shifted
    }
    return Gesture(id, keys)
}
