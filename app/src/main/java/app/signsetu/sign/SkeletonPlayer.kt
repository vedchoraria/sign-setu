package app.signsetu.sign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.camera.ArmBones
import app.signsetu.camera.FaceOutlines
import app.signsetu.camera.HandBones
import app.signsetu.camera.LandmarkFrame

/** A gap between signs on the replay timeline, so each sign starts visibly apart from the last. */
private const val GAP_MS = 400f

/** Playback of a signed answer's landmarks, signs back to back; [segment] is the sign under the playhead. */
@Stable
class ReplayState(segments: List<SignSegment>) {
    val starts: List<Float>
    val total: Float
    private val timeline: List<Pair<Float, LandmarkFrame>>

    init {
        var offset = 0f
        val s = ArrayList<Float>()
        val t = ArrayList<Pair<Float, LandmarkFrame>>()
        for (seg in segments) {
            val first = seg.frames.firstOrNull()?.timestampMs ?: continue
            s.add(offset)
            seg.frames.forEach { t.add(offset + (it.timestampMs - first) to it) }
            offset += (seg.frames.last().timestampMs - first) + GAP_MS
        }
        starts = s
        total = (offset - GAP_MS).coerceAtLeast(1f)
        timeline = t
    }

    var time by mutableFloatStateOf(0f)
    var speed by mutableFloatStateOf(0.5f)
    var playing by mutableStateOf(false)
    private var until = total

    /** The last frame at or before the playhead; times only grow along the timeline, so it is found by halving. */
    val frame: LandmarkFrame?
        get() {
            var lo = 0
            var hi = timeline.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (timeline[mid].first <= time) lo = mid + 1 else hi = mid
            }
            return timeline.getOrNull(lo - 1)?.second
        }
    val segment: Int get() = starts.indexOfLast { it <= time }

    fun play(segment: Int? = null) {
        if (segment == null) {
            if (time >= total) time = 0f
            until = total
        } else {
            time = starts[segment]
            until = starts.getOrNull(segment + 1)?.minus(GAP_MS) ?: total
        }
        playing = true
    }

    fun seek(fraction: Float) {
        time = fraction.coerceIn(0f, 1f) * total
        until = total
    }

    internal suspend fun run() {
        var last = withFrameNanos { it }
        while (playing && time < until) {
            withFrameNanos { now -> time = minOf(until, time + (now - last) / 1e6f * speed); last = now }
        }
        playing = false
    }
}

@Composable
fun rememberReplayState(segments: List<SignSegment>) = remember(segments) { ReplayState(segments) }

/**
 * The signed answer as a stick figure: hands, arms and the face outlines that carry ISL grammar, seen as by someone
 * facing the subject. Slowed down so fast signing can be read; nothing here is stored.
 */
@Composable
fun SkeletonPlayer(state: ReplayState, modifier: Modifier = Modifier) {
    LaunchedEffect(state, state.playing) { if (state.playing) state.run() }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1F2430), modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            Canvas(Modifier.fillMaxWidth()) { state.frame?.let { drawFigure(it) } }
        }
        val marker = colors.tertiary
        val track = colors.surfaceContainerHighest
        val done = colors.primary
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(state) { detectTapGestures { state.seek(it.x / size.width) } },
        ) {
            val y = size.height / 2
            val h = 6.dp.toPx()
            drawRoundRect(track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(done, Offset(0f, y - h / 2), Size(size.width * state.time / state.total, h), CornerRadius(h / 2))
            state.starts.forEach { s -> drawLine(marker, Offset(size.width * s / state.total, 0f), Offset(size.width * s / state.total, size.height), 3.dp.toPx(), StrokeCap.Round) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { if (state.playing) state.playing = false else state.play() }, modifier = Modifier.heightIn(min = 48.dp)) {
                if (state.playing) {
                    val pause = stringResource(R.string.signed_pause)
                    Text("❚❚", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = pause })
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay))
                }
            }
            Spacer(Modifier.weight(1f))
            listOf(0.25f to "¼×", 0.5f to "½×", 1f to "1×").forEach { (speed, label) ->
                FilterChip(selected = state.speed == speed, onClick = { state.speed = speed }, label = { Text(label) })
            }
        }
    }
}

private val LeftHand = Color(0xFFFFC56B)
private val RightHand = Color(0xFF7FD4C1)
private val Body = Color(0xFFB8C0D0)

private fun DrawScope.drawFigure(f: LandmarkFrame) {
    fun p(o: Offset) = Offset(o.x * size.width, o.y * size.height)
    val stroke = size.minDimension / 80f
    f.pose?.takeIf { it.size > 16 }?.let { pose ->
        for ((a, b) in ArmBones) drawLine(Body, p(pose[a]), p(pose[b]), stroke * 1.6f, StrokeCap.Round)
    }
    f.face?.let { face ->
        for (line in FaceOutlines.all) {
            if (line.any { it >= face.size }) continue
            val path = Path().apply { moveTo(p(face[line[0]]).x, p(face[line[0]]).y); line.drop(1).forEach { val q = p(face[it]); lineTo(q.x, q.y) } }
            drawPath(path, Body, style = Stroke(stroke * 0.7f))
        }
    }
    listOf(f.leftHand to LeftHand, f.rightHand to RightHand).forEach { (hand, color) ->
        if (hand == null || hand.size < 21) return@forEach
        for ((a, b) in HandBones) drawLine(color, p(hand[a]), p(hand[b]), stroke, StrokeCap.Round)
        for (q in hand) drawCircle(Color.White, stroke * 0.8f, p(q))
    }
}
