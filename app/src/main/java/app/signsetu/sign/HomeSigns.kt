package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import kotlin.math.sqrt

private const val T = 32
private const val F = 98

/**
 * Signs taught in this session: when the officer reads a sign as a word the reader did not give, its shape is kept and
 * offered first when the subject signs it again. Memory only, gone with the session (SEC-47).
 */
class HomeSigns {
    private val taught = mutableListOf<Pair<String, FloatArray>>()

    @Synchronized
    fun teach(frames: List<LandmarkFrame>, word: String) {
        signFeatures(frames, T)?.let { taught += word.trim() to it }
    }

    @Synchronized
    fun match(frames: List<LandmarkFrame>): SignGuess? {
        if (taught.isEmpty()) return null
        val x = signFeatures(frames, T) ?: return null
        val (word, d) = taught.map { (w, f) -> w to dtw(x, f) }.minBy { it.second }
        return if (d < LIMIT) SignGuess(word, 1f - d / LIMIT) else null
    }

    private companion object {
        // Mean per-frame distance in shoulder widths. On INCLUDE, the same word by other signers has median 7.9 and other
        // words have 10th percentile 6.1, so 6 keeps most repeats by one signer and few strangers; tune on devices.
        const val LIMIT = 6f
    }
}

/** Banded dynamic time warping over [T] frames of [F] features, mean frame distance along the best path. */
internal fun dtw(a: FloatArray, b: FloatArray, band: Int = 4): Float {
    val inf = Float.MAX_VALUE / 4
    val cost = Array(T + 1) { FloatArray(T + 1) { inf } }
    cost[0][0] = 0f
    for (i in 1..T) for (j in maxOf(1, i - band)..minOf(T, i + band)) {
        var s = 0f
        for (k in 0 until F) { val d = a[(i - 1) * F + k] - b[(j - 1) * F + k]; s += d * d }
        cost[i][j] = sqrt(s) + minOf(cost[i - 1][j], cost[i][j - 1], cost[i - 1][j - 1])
    }
    return cost[T][T] / T
}
