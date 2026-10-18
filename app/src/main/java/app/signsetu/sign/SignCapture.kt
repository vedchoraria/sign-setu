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
 * An explicit window in which the camera's frames are analysed (D-25); outside one the mirror is preview only.
 * A narrative pauses longer between phrases than a short answer between signs, so its chunks are phrase-sized.
 */
enum class SignWindow(val limitMs: Long, val pauseMs: Long, val minMs: Long, val keepFace: Boolean) {
    ANSWER(SIGN_LIMIT_MS, pauseMs = 300, minMs = 180, keepFace = true),
    NARRATIVE(NARRATIVE_LIMIT_MS, pauseMs = 700, minMs = 200, keepFace = false),
}

/**
 * One sign-sized chunk of a signed answer; [guesses] is null while the reader is still on it. Memory only (D-25).
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

/**
 * Splits a landmark stream into signs at pauses: hands gone or nearly still for [pauseMs]. Returns the index range of
 * each sign. A sign still open at the end is returned only when [final]; closed ranges never change as frames are added.
 * Scanning may start at [from], just after a closed range, and gives the same ranges as a scan from the start.
 */
fun segmentSigns(
    frames: List<LandmarkFrame>,
    final: Boolean,
    pauseMs: Long = 400,
    stillSpeed: Float = 0.3f,
    minMs: Long = 200,
    from: Int = 0,
): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = -1
    var lastActive = -1
    var activeFrames = 0
    fun close() {
        if (frames[lastActive].timestampMs - frames[start].timestampMs >= minMs && activeFrames >= 3) out.add(start..lastActive)
        start = -1
        activeFrames = 0
    }
    for (i in from until frames.size) {
        val f = frames[i]
        if (start >= 0 && f.timestampMs - frames[lastActive].timestampMs >= pauseMs) close()
        if (moving(frames.getOrNull(i - 1), f, stillSpeed)) {
            if (start < 0) start = maxOf(0, i - 1)
            lastActive = i
            activeFrames++
        }
    }
    if (final && start >= 0) close()
    return out
}

/** Any hand moving faster than [stillSpeed] frame-widths per second, measured over all its points; a hand appearing counts. */
private fun moving(prev: LandmarkFrame?, f: LandmarkFrame, stillSpeed: Float): Boolean {
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
 * closes it. Hands leaving the frame briefly do not end the capture. Called from the camera's analysis thread and the
 * main thread, so every entry point is synchronised. Each frame is looked at once, as [segmentSigns] does in one pass,
 * so a long unbroken narrative costs the same per frame as a short answer.
 */
class SignCapture(private val window: SignWindow = SignWindow.ANSWER, private val limitMs: Long = window.limitMs) {
    private val frames = ArrayList<LandmarkFrame>()
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
        if (moving(frames.getOrNull(i - 1), frames[i], STILL_SPEED)) {
            if (start < 0) start = maxOf(0, i - 1)
            lastActive = i
            activeFrames++
        }
        if (full && start >= 0) close(out)
        return out
    }

    /** Closes the answer and returns the signs not yet handed out. */
    @Synchronized
    fun finish(): List<List<LandmarkFrame>> = ArrayList<List<LandmarkFrame>>(1).also { if (start >= 0) close(it); full = true }

    private fun close(out: MutableList<List<LandmarkFrame>>) {
        if (frames[lastActive].timestampMs - frames[start].timestampMs >= window.minMs && activeFrames >= 3) out.add(frames.subList(start, lastActive + 1).toList())
        start = -1
        activeFrames = 0
    }
}

private const val STILL_SPEED = 0.3f
