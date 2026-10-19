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
    private class Spec(val frames: Int, val features: Int, val hidden: Int, val kernel: Int, val labels: List<String>, val norm: String = "shoulders", val dtype: String = "float32")

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
        val flat = weightFloats(weights, s.dtype)
        var at = 0
        fun take(n: Int) = flat.copyOfRange(at, at + n).also { at += n }
        embedW = take(h * FEATURES); embedB = take(h)
        conv1W = take(h * h * k); conv1B = take(h)
        conv2W = take(h * h * k); conv2B = take(h)
        headW = take(labels.size * 2 * h); headB = take(labels.size)
        require(at == flat.size) { "weights size" }
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
        embed(x, embedW, embedB, a, h, t)
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

    private fun conv(input: FloatArray, w: FloatArray, b: FloatArray) = convRelu(input, w, b, h, t, k, 1)

    fun features(frames: List<LandmarkFrame>): FloatArray? = if (s.norm == "hands") handFeatures(frames, t) else signFeatures(frames, t)
}

private const val FEATURES = 98

/** relu(embedding) of [t] frames of [FEATURES] values into [out] (channel-major). Zero inputs, absent hands, are skipped: adding 0 changes nothing. */
internal fun embed(x: FloatArray, w: FloatArray, bias: FloatArray, out: FloatArray, h: Int, t: Int, features: Int = FEATURES) {
    for (j in 0 until t) for (o in 0 until h) {
        var v = bias[o]
        val row = o * features
        for (i in 0 until features) { val xi = x[j * features + i]; if (xi != 0f) v += w[row + i] * xi }
        out[o * t + j] = max(v, 0f)
    }
}

/**
 * relu of a 1-D convolution over [t] steps, channel-major, same padding, step spacing [d]. The inner loop runs over time
 * with the valid range worked out once, and adds in the same order as a per-output sum, so results are identical.
 */
internal fun convRelu(input: FloatArray, w: FloatArray, b: FloatArray, h: Int, t: Int, k: Int, d: Int): FloatArray {
    val out = FloatArray(h * t)
    for (o in 0 until h) {
        val base = o * t
        java.util.Arrays.fill(out, base, base + t, b[o])
        for (i in 0 until h) for (q in 0 until k) {
            val wv = w[(o * h + i) * k + q]
            val shift = (q - k / 2) * d
            val from = max(0, -shift)
            val to = minOf(t, t - shift)
            val src = i * t + shift
            for (j in from until to) out[base + j] += wv * input[src + j]
        }
        for (j in 0 until t) out[base + j] = max(out[base + j], 0f)
    }
    return out
}

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

/** Little-endian weights as floats; "float16" files hold half precision (NFR-05). */
internal fun weightFloats(weights: ByteArray, dtype: String): FloatArray {
    val bytes = ByteBuffer.wrap(weights).order(ByteOrder.LITTLE_ENDIAN)
    if (dtype != "float16") return FloatArray(weights.size / 4).also { bytes.asFloatBuffer().get(it) }
    val shorts = bytes.asShortBuffer()
    return FloatArray(weights.size / 2) { halfToFloat(shorts.get(it)) }
}

/** IEEE 754 half precision to float, in plain Kotlin so the JVM tests and every Android version read the same values. */
internal fun halfToFloat(h: Short): Float {
    val bits = h.toInt() and 0xffff
    val sign = (bits and 0x8000) shl 16
    val exp = (bits ushr 10) and 0x1f
    val mant = bits and 0x3ff
    return Float.fromBits(when {
        exp == 0 && mant == 0 -> sign
        exp == 0 -> { // subnormal: normalise
            var e = -1; var m = mant
            do { e++; m = m shl 1 } while (m and 0x400 == 0)
            sign or ((127 - 15 - e) shl 23) or ((m and 0x3ff) shl 13)
        }
        exp == 0x1f -> sign or 0x7f800000 or (mant shl 13)
        else -> sign or ((exp - 15 + 127) shl 23) or (mant shl 13)
    })
}
