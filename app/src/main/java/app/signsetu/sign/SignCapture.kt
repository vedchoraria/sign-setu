package app.signsetu.sign

import androidx.compose.runtime.Immutable
import app.signsetu.camera.LandmarkFrame
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/** Longest signed answer captured: enough for a short reply, short enough to keep in memory. */
const val SIGN_LIMIT_MS = 20_000L

/** Longest "Tell me" narrative: three minutes of landmarks, without faces, fits comfortably in memory. */
const val NARRATIVE_LIMIT_MS = 180_000L

/**
 * An explicit window in which the camera's frames are analysed; outside one the mirror is preview only.
 * A narrative pauses longer between phrases than a short answer between signs, so its chunks are phrase-sized.
 * [firstSignMs] is the grace before anything at all is signed, and [quietMs] how long quiet hands are given before
 * what was signed is ingested without a tap; a narrative is allowed to think longer between sentences.
 */
enum class SignWindow(
    val limitMs: Long,
    val pauseMs: Long,
    val minMs: Long,
    val keepFace: Boolean,
    val firstSignMs: Long = 3_000,
    val quietMs: Long = 2_000,
) {
    ANSWER(SIGN_LIMIT_MS, pauseMs = 300, minMs = 180, keepFace = true),
    NARRATIVE(NARRATIVE_LIMIT_MS, pauseMs = 700, minMs = 200, keepFace = false, quietMs = 5_000),
}

/**
 * Judges when the subject has stopped giving anything, so a window can be ingested on its own instead of waiting for
 * a tap that may never come. Timestamps are the camera's, so it needs no wall clock and cannot run ahead of the frames.
 * Before any movement the whole window is grace: a person who never raises a hand empties it, they do not hold it open.
 */
class SignQuiet(private val firstSignMs: Long = 3_000, private val quietMs: Long = 2_000) {
    private var firstMs = -1L
    private var lastMovedMs = -1L

    /** Every frame of the window, with whether a hand moved on it. */
    fun see(frameMs: Long, moved: Boolean) {
        if (firstMs < 0) firstMs = frameMs
        if (moved) lastMovedMs = frameMs
    }

    /** True once the hands have been quiet for [quietMs], or nothing has moved at all for [firstSignMs]. */
    fun settled(newestMs: Long): Boolean = when {
        firstMs < 0 -> false
        lastMovedMs >= 0 -> newestMs - lastMovedMs >= quietMs
        else -> newestMs - firstMs >= firstSignMs
    }

    /** Opens a new window: the last one's grace and motion must not carry over. */
    fun reset() {
        firstMs = -1L
        lastMovedMs = -1L
    }
}

/**
 * One sign-sized chunk of a signed answer; [guesses] is null while the reader is still on it. Memory only.
 * [guesses] are already limited to [context], the cards that answer the item on screen; [slower] marks a failed read
 * of fast or badly tracked hands.
 */
@Immutable
class SignSegment(
    val frames: List<LandmarkFrame>,
    val guesses: List<SignGuess>? = null,
    val slower: Boolean = false,
    val context: Set<String> = emptySet(),
) {
    val word: String? get() = guesses?.let { it.firstOrNull()?.word ?: "?" }
    /** Brows raised over most of the sign's face frames. */
    val browsUp: Boolean get() = frames.filter { it.face != null }.let { f -> f.isNotEmpty() && f.count { it.browsUp } * 2 > f.size }
}

/** A "Tell me" account as read: each sign's word at its m:ss from the first sign, "?" after one asked with raised brows. */
fun glossTranscript(segments: List<SignSegment>): String {
    val t0 = segments.firstOrNull()?.frames?.firstOrNull()?.timestampMs ?: return ""
    return segments.joinToString(" ") { s ->
        val sec = ((s.frames.firstOrNull()?.timestampMs ?: t0) - t0) / 1000
        // A sign made with raised brows was asked, not told.
        "[%d:%02d] %s%s".format(sec / 60, sec % 60, s.word ?: "?", if (s.browsUp && s.word != "?") "?" else "")
    }
}

/** Any hand moving faster than [stillSpeed] frame-widths per second, measured over all its points; a hand appearing counts. */
internal fun moving(prev: LandmarkFrame?, f: LandmarkFrame, stillSpeed: Float): Boolean {
    val seconds = if (prev == null || f.timestampMs <= prev.timestampMs) 1f / 30 else (f.timestampMs - prev.timestampMs) / 1000f
    return handMoving(prev?.leftHand, f.leftHand, seconds, stillSpeed) || handMoving(prev?.rightHand, f.rightHand, seconds, stillSpeed)
}

private fun handMoving(a: List<Offset>?, b: List<Offset>?, seconds: Float, stillSpeed: Float): Boolean = when {
    b == null -> false
    a == null || a.size != b.size -> true
    else -> {
        var sum = 0.0
        for (i in a.indices) sum += hypot(b[i].x - a[i].x, b[i].y - a[i].y)
        sum / b.size / seconds > stillSpeed
    }
}

/**
 * Collects one sign window's frames in memory, capped at the window's limit, and hands out each sign once its pause
 * closes it: hands gone or nearly still for the window's pause. Hands leaving the frame briefly do not end the capture.
 * Called from the camera's analysis thread and the main thread, so every entry point is synchronised. Each frame is
 * looked at once, so a long unbroken narrative costs the same per frame as a short answer.
 */
class SignCapture(private val window: SignWindow = SignWindow.ANSWER, private val limitMs: Long = window.limitMs) {
    private val frames = ArrayList<LandmarkFrame>()
    private val quiet = SignQuiet(window.firstSignMs, window.quietMs)
    private var start = -1
    private var lastActive = -1
    private var activeFrames = 0
    var full = false
        private set

    /** Adds a frame and returns the signs it closed, in order. */
    @Synchronized
    fun add(frame: LandmarkFrame): List<List<LandmarkFrame>> {
        if (full) return emptyList()
        frames.add(if (window.keepFace) frame else frame.copy(face = null))
        full = frame.timestampMs - frames[0].timestampMs >= limitMs
        val out = ArrayList<List<LandmarkFrame>>(1)
        val i = frames.lastIndex
        if (start >= 0 && frame.timestampMs - frames[lastActive].timestampMs >= window.pauseMs) close(out)
        val moved = moving(frames.getOrNull(i - 1), frames[i], STILL_SPEED)
        quiet.see(frame.timestampMs, moved)
        if (moved) {
            if (start < 0) start = maxOf(0, i - 1)
            lastActive = i
            activeFrames++
        }
        if (full && start >= 0) close(out)
        return out
    }

    /**
     * True once the subject has stopped giving anything: quiet hands for the window's quiet time, or no movement at all
     * within its first-sign grace. The caller then ingests what was captured instead of waiting for a Done tap.
     */
    @Synchronized
    fun settled(): Boolean = !full && frames.isNotEmpty() && quiet.settled(frames.last().timestampMs)

    /** Closes the answer and returns the signs not yet handed out. */
    @Synchronized
    fun finish(): List<List<LandmarkFrame>> = ArrayList<List<LandmarkFrame>>(1).also { if (start >= 0) close(it); full = true }

    private fun close(out: MutableList<List<LandmarkFrame>>) {
        val longEnough = frames[lastActive].timestampMs - frames[start].timestampMs >= window.minMs && activeFrames >= 3
        // Lowering the arms after a sign is movement too; read as a sign it becomes a phantom word ("husband" then
        // "light" in a dictionary clip, a digit then "key" in every digit clip).
        if (longEnough && !droppingToRest(start, lastActive)) out.add(frames.subList(start, lastActive + 1).toList())
        start = -1
        activeFrames = 0
    }

    /**
     * Whether the chunk is the hands on their way back down, not a sign: short, every hand that is there drops at least
     * half a shoulder width, and they end well below the shoulders, where the arms hang. Needs the pose to measure a
     * shoulder, so a frame without one never drops a sign.
     */
    private fun droppingToRest(a: Int, b: Int): Boolean {
        if (frames[b].timestampMs - frames[a].timestampMs > RETRACT_MAX_MS) return false
        if ((frames[a].pose?.size ?: 0) <= 12) return false
        val ends = frames[b].pose?.takeIf { it.size > 12 } ?: return false
        // y is a share of the frame's height, so a vertical distance needs the same aspect correction as wristReach.
        val aspect = frames[b].aspect.takeIf { it > 1e-3f } ?: 1f
        val width = hypot(ends[11].x - ends[12].x, (ends[11].y - ends[12].y) / aspect)
        if (width < 1e-3f) return false
        val shoulderY = (ends[11].y + ends[12].y) / 2f
        var seen = 0
        for ((from, to) in listOf(frames[a].leftHand to frames[b].leftHand, frames[a].rightHand to frames[b].rightHand)) {
            if (from.isNullOrEmpty() || to.isNullOrEmpty()) continue
            seen++
            val down = (to[0].y - from[0].y) / aspect
            val below = (to[0].y - shoulderY) / aspect
            if (down < RETRACT_DROP * width || below < RETRACT_LOW * width) return false
        }
        return seen > 0
    }
}

internal const val STILL_SPEED = 0.3f

/** Longest chunk that can be a hand dropping back to rest. */
private const val RETRACT_MAX_MS = 500L
/** How far, in shoulder widths, a dropping wrist must travel down. */
private const val RETRACT_DROP = 0.6f
/** How far below the shoulder line (in shoulder widths) the wrist must end for the arm to be hanging. */
private const val RETRACT_LOW = 1.2f
