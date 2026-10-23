package app.signsetu.camera

import android.graphics.Bitmap

/** The per-frame stages that turn a tracker's raw landmarks into what the app reads, in their fixed order. */
internal class FramePipeline {
    private val stable = Stabilizer()
    private val cues = FaceCueTracker()
    private val heads = HeadGestures(windowMs = 900L, swing = 0.10f)
    private val thumbs = ThumbGestures(holdMs = 280L)
    private val pointing = BodyPointing(holdMs = 320L)
    private val palms = PalmGestures(holdMs = 380L)
    private val shrugs = ShrugGestures(holdMs = 350L)

    fun mark(raw: LandmarkFrame, aspect: Float, blurry: Boolean): LandmarkFrame =
        raw.copy(aspect = aspect, blurry = blurry).let(stable::mark).let(cues::mark).let(heads::mark)
            .let(thumbs::mark).let(pointing::mark).let(palms::mark).let(shrugs::mark)
}

/** Judges whether a camera picture is smeared, from its own edges: the variance of a Laplacian over the centre
 * of the frame. */
internal class BlurMeter {
    private var peak = 0f
    private var buf = IntArray(0)

    fun blurry(bitmap: Bitmap): Boolean {
        val w = bitmap.width / 2
        val h = bitmap.height / 2
        if (w < 8 || h < 8) return false
        if (buf.size < w * h) buf = IntArray(w * h)
        bitmap.getPixels(buf, 0, w, bitmap.width / 4, bitmap.height / 4, w, h)
        return judge(laplacianVariance(buf, w, h))
    }

    internal fun judge(sharpness: Float): Boolean {
        peak = maxOf(sharpness, peak * PEAK_DECAY)
        return peak >= MIN_PEAK && sharpness < peak * BLUR_RATIO
    }

    companion object {
        // Calibration knobs; tune on device. A flat wall has no edges to lose, so a low peak never reads as blur.
        const val BLUR_RATIO = 0.4f
        const val PEAK_DECAY = 0.995f
        const val MIN_PEAK = 20f
    }
}

/** Variance of the 4-neighbour Laplacian of the luma of ARGB [px], row-major [w] by [h], sampling every second pixel. */
internal fun laplacianVariance(px: IntArray, w: Int, h: Int): Float {
    fun luma(i: Int): Int { val c = px[i]; return (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8 }
    var n = 0
    var sum = 0.0
    var sumSq = 0.0
    for (y in 1 until h - 1 step 2) for (x in 1 until w - 1 step 2) {
        val i = y * w + x
        val l = (luma(i - 1) + luma(i + 1) + luma(i - w) + luma(i + w) - 4 * luma(i)).toDouble()
        sum += l; sumSq += l * l; n++
    }
    if (n == 0) return 0f
    val mean = sum / n
    return (sumSq / n - mean * mean).toFloat()
}
