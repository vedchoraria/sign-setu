package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * The bundled ASL fingerspelling reader (format 2), trained by tools/ml/spell_ctc.py: one hand's landmarks to letters
 * by CTC. Features and forward pass must match spell_features in tools/ml/features.py and SpellNet exactly.
 * The letters are a proposal the subject confirms, like every reading.
 */
class CtcSpellReader(spec: String, weights: ByteArray) {
    @Serializable
    private class Spec(
        val format: Int,
        val fps: Int,
        @SerialName("max_frames") val maxFrames: Int,
        val features: Int,
        val hidden: Int,
        val kernel: Int,
        val dilations: List<Int>,
        val alphabet: String,
        val dtype: String = "float32",
    )

    private val s = Json { ignoreUnknownKeys = true }.decodeFromString<Spec>(spec)
    val alphabet: String = s.alphabet
    private val h = s.hidden
    private val k = s.kernel
    private val classes = alphabet.length + 1
    private val embedW: FloatArray
    private val embedB: FloatArray
    private val convW: List<FloatArray>
    private val convB: List<FloatArray>
    private val headW: FloatArray
    private val headB: FloatArray

    init {
        require(s.format == 2 && s.features == FEATURES) { "format" }
        val flat = weightFloats(weights, s.dtype)
        var at = 0
        fun take(n: Int) = flat.copyOfRange(at, at + n).also { at += n }
        embedW = take(h * FEATURES); embedB = take(h)
        val w = ArrayList<FloatArray>(); val b = ArrayList<FloatArray>()
        repeat(s.dilations.size) { w += take(h * h * k); b += take(h) }
        convW = w; convB = b
        headW = take(classes * h); headB = take(classes)
        require(at == flat.size) { "weights size" }
    }

    /** The letters read, empty when no hand was seen. */
    fun read(frames: List<LandmarkFrame>): String = logits(frames)?.let(::decode).orEmpty()

    /** Greedy CTC: the best class per frame, repeats merged, blanks (class 0) dropped. */
    fun decode(logits: Array<FloatArray>): String = buildString {
        var last = -1
        for (row in logits) {
            var best = 0
            for (c in 1 until row.size) if (row[c] > row[best]) best = c
            if (best != 0 && best != last) append(alphabet[best - 1])
            last = best
        }
    }

    /** Per-frame class scores at the model's frame rate, or null when no frame shows a hand. */
    fun logits(frames: List<LandmarkFrame>): Array<FloatArray>? {
        val x = features(frames) ?: return null
        val t = x.size / FEATURES
        var a = FloatArray(h * t)
        embed(x, embedW, embedB, a, h, t, FEATURES)
        for ((n, d) in s.dilations.withIndex()) {
            val c = convRelu(a, convW[n], convB[n], h, t, k, d)
            for (i in c.indices) c[i] += a[i]
            a = c
        }
        return Array(t) { j ->
            FloatArray(classes) { c ->
                var v = headB[c]
                for (o in 0 until h) v += headW[c * h + o] * a[o * t + j]
                v
            }
        }
    }

    /** One hand per frame at [Spec.fps], from the wrist, in wrist to middle-knuckle lengths, plus wrist motion. */
    fun features(frames: List<LandmarkFrame>): FloatArray? {
        if (frames.isEmpty()) return null
        val t0 = frames.first().timestampMs
        val n = minOf((frames.last().timestampMs - t0) * s.fps / 1000 + 1, s.maxFrames.toLong()).toInt()
        if (n <= 0) return null
        val idx = IntArray(n)
        var i = 0
        for (q in 0 until n) {
            val target = t0 + q * 1000L / s.fps
            while (i + 1 < frames.size && abs(frames[i + 1].timestampMs - target) < abs(frames[i].timestampMs - target)) i++
            idx[q] = i
        }
        fun ok(p: List<*>?) = p != null && p.size == 21
        val useLeft = idx.count { ok(frames[it].leftHand) } > idx.count { ok(frames[it].rightHand) }
        val kx = if (useLeft) -1f else 1f
        val ky = 1f / frames.first().aspect
        val out = FloatArray(n * FEATURES)
        var seen = false
        var px = 0f; var py = 0f; var prev = false
        for (q in 0 until n) {
            val f = frames[idx[q]]
            val hand = (if (useLeft) f.leftHand else f.rightHand)?.takeIf { it.size == 21 }
            val wx = (hand?.get(0)?.x ?: 0f) * kx
            val wy = (hand?.get(0)?.y ?: 0f) * ky
            val size = if (hand == null) 0f else hypot(hand[9].x * kx - wx, hand[9].y * ky - wy)
            if (hand == null || size < 1e-6f) { prev = false; continue }
            val row = q * FEATURES
            hand.forEachIndexed { p, pt -> out[row + 2 * p] = (pt.x * kx - wx) / size; out[row + 2 * p + 1] = (pt.y * ky - wy) / size }
            if (prev) { out[row + 42] = (wx - px) / size; out[row + 43] = (wy - py) / size }
            out[row + 44] = 1f
            px = wx; py = wy; prev = true; seen = true
        }
        return out.takeIf { seen }
    }

    private companion object {
        const val FEATURES = 45
    }
}
