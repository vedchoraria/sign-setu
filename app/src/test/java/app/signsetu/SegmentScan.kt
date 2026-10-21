package app.signsetu

import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.STILL_SPEED
import app.signsetu.sign.moving

/**
 * The reference for SignCapture: one full scan of a finished stream, split at pauses of [pauseMs], as index ranges.
 * A sign still open at the end is returned only when [final].
 */
fun segmentSigns(frames: List<LandmarkFrame>, final: Boolean, pauseMs: Long = 400, minMs: Long = 200): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    var lastActive = -1
    var activeFrames = 0
    fun close() {
        if (frames[lastActive].timestampMs - frames[start].timestampMs >= minMs && activeFrames >= 3) out.add(start..lastActive)
        start = -1
        activeFrames = 0
    }
    for (i in frames.indices) {
        val f = frames[i]
        if (start >= 0 && f.timestampMs - frames[lastActive].timestampMs >= pauseMs) close()
        if (moving(frames.getOrNull(i - 1), f, STILL_SPEED)) {
            if (start < 0) start = maxOf(0, i - 1)
            lastActive = i
            activeFrames++
        }
    }
    if (final && start >= 0) close()
    return out
}
