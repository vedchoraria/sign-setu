package app.signsetu.camera

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/** Cleans the tracker's output before anything reads it: a hand or pose lost for a frame or two is held, so a
 * sign is not split by a dropout, and still points are smoothed, so held letters stop shimmering. */
class Stabilizer(private val holdMs: Long = HOLD_MS) {
    private class Track(val points: List<Offset>, val seenAt: Long)

    // Left hand, right hand, pose.
    private val tracks = arrayOfNulls<Track>(3)
    private var lastMs = Long.MIN_VALUE

    fun mark(frame: LandmarkFrame): LandmarkFrame {
        // A new capture starts clean, never from the last person's hands.
        if (frame.timestampMs <= lastMs || frame.timestampMs - lastMs > RESET_MS) tracks.fill(null)
        lastMs = frame.timestampMs
        return frame.copy(
            leftHand = step(frame.leftHand, frame.timestampMs, 0),
            rightHand = step(frame.rightHand, frame.timestampMs, 1),
            pose = step(frame.pose, frame.timestampMs, 2),
        )
    }

    private fun step(now: List<Offset>?, ms: Long, slot: Int): List<Offset>? {
        val prev = tracks[slot]
        if (now == null) {
            if (prev != null && ms - prev.seenAt <= holdMs) return prev.points
            tracks[slot] = null
            return null
        }
        if (prev == null || prev.points.size != now.size) { tracks[slot] = Track(now, ms); return now }
        val seconds = ((ms - prev.seenAt).coerceAtLeast(1)) / 1000f
        var sum = 0f
        for (i in now.indices) sum += hypot(now[i].x - prev.points[i].x, now[i].y - prev.points[i].y)
        val speed = sum / now.size / seconds
        val a = MIN_ALPHA + (1f - MIN_ALPHA) * ((speed - SLOW) / (FAST - SLOW)).coerceIn(0f, 1f)
        val out = if (a >= 1f) now else List(now.size) { i ->
            Offset(prev.points[i].x + (now[i].x - prev.points[i].x) * a, prev.points[i].y + (now[i].y - prev.points[i].y) * a)
        }
        tracks[slot] = Track(out, ms)
        return out
    }

    companion object {
        // Calibration knobs; tune on device. Speeds are frame widths per second, as in SignCapture.
        const val HOLD_MS = 100L
        const val RESET_MS = 1000L
        const val MIN_ALPHA = 0.4f
        const val SLOW = 0.15f
        const val FAST = 0.8f
    }
}
