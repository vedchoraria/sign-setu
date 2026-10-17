package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/**
 * The bundled isolated-sign classifier (D-25), trained by tools/ml on INCLUDE. Features and forward pass must match
 * tools/ml/features.py and export.py exactly.
 */
class LandmarkSignReader(spec: String, weights: ByteArray, override val words: Map<String, String> = emptyMap()) : SignReader {
    @Serializable
    private class Spec(val frames: Int, val features: Int, val hidden: Int, val kernel: Int, val labels: List<String>, val norm: String = "shoulders")

    private val s = Json { ignoreUnknownKeys = true }.decodeFromString<Spec>(spec)
    override val labels: List<String> = s.labels
    private val t = s.frames
    private val h = s.hidden
    private val k = s.kernel
    private val embedW: FloatArray
    private val embedB: FloatArray
    private val conv1W: FloatArray
    private val conv1B: FloatArray
    private val conv2W: FloatArray
    private val conv2B: FloatArray
    private val headW: FloatArray
    private val headB: FloatArray

    init {
        require(s.features == FEATURES) { "feature count" }
        val buf = ByteBuffer.wrap(weights).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        fun take(n: Int) = FloatArray(n).also { buf.get(it) }
        embedW = take(h * FEATURES); embedB = take(h)
        conv1W = take(h * h * k); conv1B = take(h)
        conv2W = take(h * h * k); conv2B = take(h)
        headW = take(labels.size * 2 * h); headB = take(labels.size)
        require(!buf.hasRemaining()) { "weights size" }
    }

    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val z = logits(frames) ?: return emptyList()
        val top = z.max()
        val e = FloatArray(z.size) { exp(z[it] - top) }
        val sum = e.sum()
        return e.indices.sortedByDescending { e[it] }.map { SignGuess(labels[it], e[it] / sum) }
    }

    /** Raw class scores, or null when no frame shows the shoulders. */
    fun logits(frames: List<LandmarkFrame>): FloatArray? {
        val x = features(frames) ?: return null
        var a = FloatArray(h * t)
        for (j in 0 until t) for (o in 0 until h) {
            var v = embedB[o]
            for (i in 0 until FEATURES) v += embedW[o * FEATURES + i] * x[j * FEATURES + i]
            a[o * t + j] = max(v, 0f)
        }
        a = conv(a, conv1W, conv1B)
        a = conv(a, conv2W, conv2B)
        val pooled = FloatArray(2 * h)
        for (c in 0 until h) {
            var sum = 0f
            var top = a[c * t]
            for (j in 0 until t) { sum += a[c * t + j]; top = max(top, a[c * t + j]) }
            pooled[c] = sum / t
            pooled[h + c] = top
        }
        return FloatArray(labels.size) { o ->
            var v = headB[o]
            for (i in 0 until 2 * h) v += headW[o * 2 * h + i] * pooled[i]
            v
        }
    }

    private fun conv(input: FloatArray, w: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(h * t)
        val pad = k / 2
        for (o in 0 until h) for (j in 0 until t) {
            var v = b[o]
            for (i in 0 until h) for (q in 0 until k) {
                val src = j + q - pad
                if (src in 0 until t) v += w[(o * h + i) * k + q] * input[i * t + src]
            }
            out[o * t + j] = max(v, 0f)
        }
        return out
    }

    fun features(frames: List<LandmarkFrame>): FloatArray? = if (s.norm == "hands") handFeatures(frames, t) else signFeatures(frames, t)
}

private const val FEATURES = 98

/** Shoulder-centred, shoulder-width-scaled landmarks, resampled to [t] frames of 98 values. */
fun signFeatures(frames: List<LandmarkFrame>, t: Int): FloatArray? {
        if (frames.isEmpty()) return null
        val ky = 1f / frames.first().aspect
        var cx = 0f; var cy = 0f; var scale = 0f; var n = 0
        for (f in frames) {
            val p = f.pose?.takeIf { it.size > 16 } ?: continue
            cx += (p[11].x + p[12].x) / 2; cy += (p[11].y + p[12].y) / 2 * ky
            scale += hypot(p[11].x - p[12].x, (p[11].y - p[12].y) * ky); n++
        }
        if (n == 0 || scale / n < 1e-6f) return null
        cx /= n; cy /= n; scale /= n
        val out = FloatArray(t * FEATURES)
        val last = frames.size - 1
        for (j in 0 until t) {
            val f = frames[(j * last * 2 + (t - 1)) / (2 * (t - 1))]
            val row = j * FEATURES
            fun put(at: Int, x: Float, y: Float) { out[row + at] = (x - cx) / scale; out[row + at + 1] = (y * ky - cy) / scale }
            f.leftHand?.takeIf { it.size == 21 }?.let { hand -> hand.forEachIndexed { i, p -> put(2 * i, p.x, p.y) }; out[row + 96] = 1f }
            f.rightHand?.takeIf { it.size == 21 }?.let { hand -> hand.forEachIndexed { i, p -> put(42 + 2 * i, p.x, p.y) }; out[row + 97] = 1f }
            f.pose?.takeIf { it.size > 16 }?.let { p -> for (i in 0 until 6) put(84 + 2 * i, p[11 + i].x, p[11 + i].y) }
        }
        return out
}

/** As tools/ml/features.py featurize_hands: centred on all hand points, scaled by mean wrist to middle-knuckle length. */
fun handFeatures(frames: List<LandmarkFrame>, t: Int): FloatArray? {
    if (frames.isEmpty()) return null
    val ky = 1f / frames.first().aspect
    var cx = 0f; var cy = 0f; var points = 0; var scale = 0f; var hands = 0
    for (f in frames) for (h in listOfNotNull(f.leftHand?.takeIf { it.size == 21 }, f.rightHand?.takeIf { it.size == 21 })) {
        for (p in h) { cx += p.x; cy += p.y * ky; points++ }
        scale += hypot(h[0].x - h[9].x, (h[0].y - h[9].y) * ky); hands++
    }
    if (hands == 0 || scale / hands < 1e-6f) return null
    cx /= points; cy /= points; scale /= hands
    val out = FloatArray(t * FEATURES)
    val last = frames.size - 1
    for (j in 0 until t) {
        val f = frames[(j * last * 2 + (t - 1)) / (2 * (t - 1))]
        val row = j * FEATURES
        fun put(at: Int, x: Float, y: Float) { out[row + at] = (x - cx) / scale; out[row + at + 1] = (y * ky - cy) / scale }
        f.leftHand?.takeIf { it.size == 21 }?.let { hand -> hand.forEachIndexed { i, p -> put(2 * i, p.x, p.y) }; out[row + 96] = 1f }
        f.rightHand?.takeIf { it.size == 21 }?.let { hand -> hand.forEachIndexed { i, p -> put(42 + 2 * i, p.x, p.y) }; out[row + 97] = 1f }
    }
    return out
}
