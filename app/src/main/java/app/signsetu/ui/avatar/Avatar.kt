package app.signsetu.ui.avatar

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class Character { CHILD, ADULT }

/** The part of the 200-unit character space a view shows, in character units. */
enum class AvatarView(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** To the hips: both hands stay inside at rest, at waist level and flat on the head. */
    Body(-10f, 0f, 220f, 258f),
    Head(0f, 0f, 200f, 200f),
}

/** Width over height of [AvatarView.Body]; callers size the avatar with it. */
const val AVATAR_ASPECT = 220f / 258f

/** The face that marks a question in ISL: raised brows for yes-no, furrowed brows for a question word. */
enum class QuestionKind { YES_NO, WH }

/** Colours of one character. Clothes are plain and regionally neutral (05-avatar-and-gestures §4). */
internal class Look(
    val skin: Color, val skinShade: Color, val line: Color, val outline: Color,
    val hair: Color, val hairShine: Color,
    val top: Color, val topShade: Color, val trim: Color, val trousers: Color,
    val iris: Color, val lip: Color, val blush: Color,
    val longSleeves: Boolean,
)

private val ChildLook = Look(
    skin = Color(0xFFC98D5E), skinShade = Color(0xFFAE7345), line = Color(0xFF7C4A27), outline = Color(0xFF4A2A14),
    hair = Color(0xFF2A1F1B), hairShine = Color(0xFF4A3A33),
    top = Color(0xFF2E9C93), topShade = Color(0xFF227E77), trim = Color(0xFFF6BE4B), trousers = Color(0xFF3D4A6B),
    iris = Color(0xFF3A2416), lip = Color(0xFF8C3A32), blush = Color(0x44F07C64),
    longSleeves = false,
)

private val AdultLook = Look(
    skin = Color(0xFFBC7E52), skinShade = Color(0xFF9F6842), line = Color(0xFF6E4123), outline = Color(0xFF3E2412),
    hair = Color(0xFF1E1816), hairShine = Color(0xFF3A302C),
    top = Color(0xFF3B4A6E), topShade = Color(0xFF2A3654), trim = Color(0xFF8394C2), trousers = Color(0xFF2B3448),
    iris = Color(0xFF2E1D12), lip = Color(0xFF7E3A33), blush = Color(0x22F07C64),
    longSleeves = true,
)

/** Draws a character and plays [gesture] once each time [playKey] changes. Speed below 1 plays slowly. With
 * [idle] the character breathes and blinks; the system "remove animations" setting turns that off. */
@Composable
fun Avatar(
    character: Character,
    gesture: Gesture?,
    playKey: Any?,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    idle: Boolean = true,
    atSeconds: Float? = null,
    glance: Boolean = false,
    view: AvatarView = AvatarView.Body,
    question: QuestionKind? = null,
    onFinished: () -> Unit = {},
) {
    val child = character == Character.CHILD
    val build = if (child) ChildBuild else AdultBuild
    val look = if (child) ChildLook else AdultLook
    val timeline = remember(gesture, build) { Timeline(gesture, build) }
    val clock = remember { mutableFloatStateOf(0f) }
    val idleClock = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val finished by rememberUpdatedState(onFinished)
    val gaze = remember { Animatable(0f) }
    // The elbow drawn last frame, so a two-bone solve keeps its bend instead of flipping across the shoulder line.
    val elbows = remember { LastElbows() }

    LaunchedEffect(playKey, timeline, speed, atSeconds) {
        gaze.snapTo(0f)
        clock.floatValue = atSeconds ?: 0f
        if (atSeconds != null) return@LaunchedEffect
        if (timeline.duration > 0f) {
            val start = withFrameNanos { it }
            while (clock.floatValue < timeline.duration) {
                withFrameNanos { now -> clock.floatValue = (now - start) / 1e9f * speed }
            }
            finished()
        }
        if (glance) gaze.animateTo(1f, tween(durationMillis = 500, delayMillis = 300))
    }
    val idleOn = idle && !still && atSeconds == null
    if (idleOn) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            // 30 fps idle cadence provides fluid breathing, micro-expressions and eye darts
            var last = start
            while (true) withFrameNanos { now ->
                if (now - last >= IDLE_FRAME_NS) {
                    idleClock.floatValue = (now - start) / 1e9f
                    last = now
                }
            }
        }
    }

    Spacer(
        modifier.drawWithCache {
            val kit = Kit(build, look, child)
            onDrawBehind {
                // State is read here, in the draw phase, so animation frames redraw without recomposing.
                val g = gaze.value
                val t = clock.floatValue
                val d = timeline.duration
                val playing = d > 0f && t < d
                val e = if (d > 0f) envelope(t, d) else 0f
                var pose = timeline.pose(t)
                if (g > 0f) pose = pose.copy(nod = pose.nod + g * 0.5f, gaze = g)
                if (question != null && e > 0f) pose = questionFace(pose, question, e)
                if (playing && !still) {
                    val f = pose.face
                    if (f.mouthOpen < 0.2f && f.mouthRound < 0.5f) {
                        val talk = 0.07f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 2.3f)) * e
                        val extraSmile = 0.05f * (0.5f + 0.5f * cos(t * 2f * PI.toFloat() * 1.5f)) * e
                        pose = pose.copy(face = f.copy(mouthOpen = f.mouthOpen + talk, smile = f.smile + extraSmile))
                    }
                }
                val blink = when {
                    still -> 1f
                    playing && atSeconds == null -> blinkAt(t)
                    idleOn -> blinkAt(idleClock.floatValue)
                    else -> 1f
                }
                drawCharacter(kit, view, pose, if (idleOn) idleClock.floatValue else 0f, t, blink, !still, elbows)
            }
        },
    )
}

private const val IDLE_FRAME_NS = 33_333_333L

/** 0 to 1 over the lead-in, 1 while the gesture plays, back to 0 over the lead-out. */
private fun envelope(t: Float, duration: Float) =
    (t / Timeline.LEAD_IN).coerceIn(0f, 1f) * ((duration - t) / Timeline.LEAD_OUT).coerceIn(0f, 1f)

private fun questionFace(p: Pose, kind: QuestionKind, e: Float): Pose {
    val f = p.face
    return when (kind) {
        QuestionKind.YES_NO -> p.copy(
            face = f.copy(browRaise = maxOf(f.browRaise, 0.85f * e), eyeOpen = f.eyeOpen * (1f + 0.1f * e)),
            nod = p.nod - 0.15f * e,
        )
        QuestionKind.WH -> p.copy(
            face = f.copy(browFrown = maxOf(f.browFrown, 0.75f * e), browRaise = f.browRaise * (1f - e), eyeOpen = f.eyeOpen * (1f - 0.12f * e)),
            nod = p.nod + 0.12f * e,
            tilt = p.tilt + 2f * e,
        )
    }
}

/** Everything the draw reuses from frame to frame. Brushes are in character or hand units, so they stay valid
 * under the draw's translate, rotate and scale; paths are rewound and rebuilt each frame. */
internal class Kit(val build: Build, val look: Look, val child: Boolean) {
    val torsoBrush = Brush.verticalGradient(
        listOf(lerp(look.top, Color.White, 0.14f), look.top, look.topShade), startY = build.shoulderY - 20f, endY = 240f,
    )
    val headBrush = Brush.radialGradient(
        listOf(lerp(look.skin, Color.White, 0.13f), look.skin, lerp(look.skin, look.skinShade, 0.7f)),
        center = Offset(build.headX - build.headRx * 0.28f, build.headY - build.headRy * 0.32f),
        radius = build.headRy * 1.35f,
    )
    val hairBrush = (build.headY - build.headRy - if (child) 20f else 19f).let { top ->
        Brush.verticalGradient(listOf(look.hairShine, look.hair, look.hair), startY = top, endY = build.headY)
    }
    /** Child iris, drawn translated to the eye centre. */
    val irisBrush = Brush.radialGradient(listOf(lerp(look.iris, Color(0xFF9A6A45), 0.55f), look.iris), center = Offset(0f, 2.6f), radius = 6.2f * 1.3f)
    /** Back of the hand, darker towards the wrist; in hand units. */
    val backBrush = Brush.verticalGradient(listOf(look.skin, look.skin, lerp(look.skin, look.skinShade, 0.45f)), startY = -12f, endY = 0f)
    val palmTone = lerp(look.skin, Color(0xFFF3CFAE), 0.45f)
    val sleeve = lerp(look.top, Color.White, 0.1f)
    val chest = lerp(look.skin, look.skinShade, 0.45f)
    val knuckle = lerp(look.skin, Color.White, 0.18f)
    val nail = lerp(look.skin, Color(0xFFFFE9DA), 0.55f)
    val pad = lerp(palmTone, Color.White, 0.15f)
    val halo = Color.White.copy(alpha = 0.35f)
    val rim = look.outline.copy(alpha = 0.9f)
    /** The shadow a front hand casts on the one it touches, so two skins never merge into one shape. */
    val shadow = look.outline.copy(alpha = 0.3f)

    val torso = Path()
    val hair = Path()
    val tuft = Path()
    val curve = Path()
    val mouth = Path()
    val quad = Path()

    /** Hand segments, finger (index to pinky, then thumb) by segment: ax, ay, bx, by, width, bend, shade. */
    val seg = FloatArray(5 * 3 * SEG)
    var frontal = false
    var palmFacing = false
    var thumbSide = 1f
    var pw = 13f
    var nearThumb = false
    var palmSide = 1f
    /** Front hand on the other one: thicker, fully opaque outline so the two skins stay apart. */
    var contactStroke = false
}

/** Elbows as drawn on the previous frame, per side, in character space. */
private class LastElbows {
    var r: Pair<Float, Float>? = null
    var l: Pair<Float, Float>? = null
}

private fun DrawScope.drawCharacter(
    k: Kit, view: AvatarView, still: Pose, time: Float, gestureT: Float, blink: Float, secondary: Boolean,
    elbows: LastElbows,
) {
    val b = k.build
    // A resting body is never quite still: a slow sway, a drifting head and now and then a glance aside.
    val pose = still.copy(
        lean = still.lean + sin(time * 2f * PI.toFloat() / 6.5f) * 0.06f,
        tilt = still.tilt + sin(time * 2f * PI.toFloat() / 8.3f) * 1.4f,
        turn = still.turn + glanceAt(time),
    )
    val unit = size.height / view.height
    val ox = (size.width - view.width * unit) / 2f - view.left * unit
    val oy = -view.top * unit
    val breath = sin(time * 2f * PI.toFloat() / 3.8f) * 0.7f
    val shiver = if (pose.shiver > 0f) sin(gestureT * 55f) * pose.shiver * 1.2f else 0f
    var liftR = pose.shrug * 7f - breath * 0.5f
    var liftL = liftR
    var lean = pose.lean
    var tilt = pose.tilt
    if (secondary) {
        // A raised hand lifts its own shoulder, and the body leans a little towards where the hands are.
        liftR += 3f * ((b.shoulderY - pose.r.y) / 90f).coerceIn(0f, 1f)
        liftL += 3f * ((b.shoulderY - pose.l.y) / 90f).coerceIn(0f, 1f)
        val dl = 0.04f * (pose.r.x + pose.l.x - 200f) / 100f
        lean += dl
        tilt -= 1.5f * dl
    }

    clipRect {
    translate(ox + shiver, oy) {
        scale(unit, unit, pivot = Offset.Zero) {
            val bodyDy = breath * 0.4f + pose.hunch * 6f
            // The neck goes under the collar so its base is hidden.
            drawNeck(k, lean * 7f, bodyDy)
            translate(lean * 6f, bodyDy) { drawTorso(k, liftR, liftL) }
            val headDx = lean * 8f + pose.turn * 2f
            val headDy = bodyDy + pose.nod * 6f + pose.hunch * 10f
            translate(headDx, headDy) {
                rotate(tilt + pose.hunch * 8f, pivot = Offset(b.headX, b.neckTop + 4f)) {
                    drawHead(k, pose, blink)
                }
            }
            // The hand placed on the other one draws last, in front, and casts a shadow on the one behind.
            val shiftR = Offset(lean * 6f, bodyDy - liftR)
            val shiftL = Offset(lean * 6f, bodyDy - liftL)
            if (pose.rFront) {
                drawArm(k, pose.l, Side.L, shiftL, elbows = elbows)
                drawArm(k, pose.r, Side.R, shiftR, front = pose.handsTouch, elbows = elbows)
            } else {
                drawArm(k, pose.r, Side.R, shiftR, elbows = elbows)
                drawArm(k, pose.l, Side.L, shiftL, front = pose.handsTouch, elbows = elbows)
            }
        }
    }
    }
}

private fun glanceAt(time: Float): Float {
    val phase = time % 9f
    if (phase < 5f || phase > 6.4f) return 0f
    val side = if ((time / 9f).toInt() % 2 == 0) 1f else -1f
    return side * 0.28f * sin((phase - 5f) / 1.4f * PI.toFloat())
}

/** 1 open, 0 shut. One blink per 4 s slot at a hashed offset, so gaps vary from 2.6 to 5.4 s; slot 0 blinks at once. */
private fun blinkAt(time: Float): Float {
    val n = (time / 4f).toInt()
    val offset = if (n == 0) 0f else (((n * 1103515245 + 12345) ushr 16) % 1000) / 1000f * 1.4f
    val p = time - n * 4f - offset
    return when {
        p < 0f || p >= 0.13f -> 1f
        p < 0.05f -> 1f - p / 0.05f
        else -> (p - 0.05f) / 0.08f
    }
}

private fun DrawScope.drawTorso(k: Kit, liftR: Float, liftL: Float) {
    val b = k.build
    val look = k.look
    val c = Build.CENTER
    val sh = b.shoulderHalf
    val sy = b.shoulderY
    val nb = b.neckWidth / 2f + 2f
    // The character's right side is on the viewer's left.
    k.torso.apply {
        rewind()
        moveTo(c - sh - 3f, 262f)
        lineTo(c - sh - 3f, 228f)
        cubicTo(c - sh - 3f, 205f, c - sh - 12f, sy + 60f, c - sh - 12f, sy + 30f)
        lineTo(c - sh - 12f, sy + 6f - liftR)
        quadraticTo(c - sh - 12f, sy - 6f - liftR, c - sh, sy - 6f - liftR)
        quadraticTo(c - sh * 0.55f, sy - 15f - liftR, c - nb, sy - 17f - liftR)
        lineTo(c + nb, sy - 17f - liftL)
        quadraticTo(c + sh * 0.55f, sy - 15f - liftL, c + sh, sy - 6f - liftL)
        quadraticTo(c + sh + 12f, sy - 6f - liftL, c + sh + 12f, sy + 6f - liftL)
        lineTo(c + sh + 12f, sy + 30f)
        cubicTo(c + sh + 12f, sy + 60f, c + sh + 3f, 205f, c + sh + 3f, 228f)
        lineTo(c + sh + 3f, 262f)
        close()
    }
    drawPath(k.torso, k.torsoBrush)
    drawPath(k.torso, look.line, style = Stroke(1.1f), alpha = 0.22f)
    // Soft fold shadows give the top some volume without lines.
    drawOval(look.topShade, Offset(c - sh - 6f, sy + 26f), Size(14f, 60f), alpha = 0.55f)
    drawOval(look.topShade, Offset(c + sh - 8f, sy + 26f), Size(14f, 60f), alpha = 0.55f)
    val w = sh + 3f
    if (k.child) {
        drawRect(look.topShade, Offset(c - w, 236f), Size(w * 2f, 3f))
        drawRect(look.trousers, Offset(c - w, 239f), Size(w * 2f, 23f))
    } else {
        drawRect(look.trousers, Offset(c - w, 241f), Size(w * 2f, 21f))
        drawRect(Color(0xFF2B2F3A), Offset(c - w, 236f), Size(w * 2f, 5f))
        drawRoundRect(look.trim, Offset(c - 3f, 236f), Size(6f, 5f), CornerRadius(1f))
    }
    val collar = sy - 17f - (liftR + liftL) / 2f
    if (k.child) {
        drawArc(k.chest, 0f, 180f, true, Offset(c - 12f, collar - 9f), Size(24f, 18f))
        drawArc(look.trim, 10f, 160f, false, Offset(c - 13f, collar - 10f), Size(26f, 21f), style = Stroke(3.6f, cap = StrokeCap.Round))
    } else {
        k.curve.apply { rewind(); moveTo(c - 10f, collar + 0.5f); lineTo(c, collar + 14f); lineTo(c + 10f, collar + 0.5f) }
        drawPath(k.curve, k.chest)
        drawPath(k.curve, look.trim, style = Stroke(3f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNeck(k: Kit, dx: Float, dy: Float) {
    val b = k.build
    val c = Build.CENTER + dx
    val w = b.neckWidth
    val top = b.neckTop + dy
    val base = b.shoulderY - 10f + dy
    drawRoundRect(k.look.skinShade, Offset(c - w / 2, top), Size(w, base - top), CornerRadius(4f))
    drawRoundRect(k.look.skin, Offset(c - w / 2 + 1.5f, top), Size(w * 0.62f, base - top), CornerRadius(4f))
    drawOval(k.look.skinShade, Offset(c - w / 2, top - 2f), Size(w, 11f), alpha = 0.9f)
}

private fun DrawScope.drawHead(k: Kit, pose: Pose, blink: Float) {
    val b = k.build
    val look = k.look
    val child = k.child
    val f = pose.face
    val hx = b.headX
    val hy = b.headY
    val fx = hx + pose.turn * 7f
    val fy = hy + pose.nod * 2.5f

    val earY = hy + if (child) 5f else 3f
    for (s in SIDES) {
        val ex = hx + s * (b.headRx - 1f) - pose.turn * 3f
        drawOval(look.skin, Offset(ex - 6.5f, earY - 8.5f), Size(13f, 17f))
        drawOval(look.skinShade, Offset(ex - 3f + s * 0.5f, earY - 5f), Size(6f, 10f), alpha = 0.7f)
    }
    drawOval(k.headBrush, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2))
    drawOval(look.line, Offset(hx - b.headRx, hy - b.headRy), Size(b.headRx * 2, b.headRy * 2), style = Stroke(1.1f), alpha = 0.28f)

    drawHair(k, pose.turn)

    // Brows carry the question and worry (ISL facial grammar, 05 §4).
    val browY = fy - (if (child) 15f else 13f) - f.browRaise * 5f + f.browFrown * 2f
    val browGap = if (child) 7f else 6f
    val browLen = if (child) 13f else 12f
    for (s in SIDES) {
        val ix = fx + s * browGap
        val iy = browY - f.browWorry * 4f + f.browFrown * 3.5f
        val ox = fx + s * (browGap + browLen)
        val oy = browY + f.browWorry * 1.5f - f.browFrown * 1f + 1f
        // A soft arch reads as friendly; it flattens as the brows furrow.
        val arch = (if (child) 3.2f else 2.4f) * (1f - f.browFrown)
        k.curve.apply { rewind(); moveTo(ix, iy); quadraticTo((ix + ox) / 2f, minOf(iy, oy) - arch, ox, oy) }
        drawPath(k.curve, look.hair, style = Stroke(if (child) 3.4f else 3.2f, cap = StrokeCap.Round))
    }

    // Eyes: large and warm for the child, simple for the adult.
    val open = (f.eyeOpen * blink * (1f - pose.gaze * 0.18f)).coerceIn(0.05f, 1.2f)
    val eyeY = fy - 1f + pose.gaze * (if (child) 2.2f else 0.6f)
    for (s in SIDES) {
        val ex = fx + s * (if (child) 15f else 13f)
        if (f.eyeSmile > 0.55f || open < 0.18f) {
            k.curve.apply {
                rewind(); moveTo(ex - 6f, eyeY + 1f)
                quadraticTo(ex, eyeY - 6f * (if (open < 0.18f) -0.3f else 1f), ex + 6f, eyeY + 1f)
            }
            drawPath(k.curve, look.iris, style = Stroke(2.6f, cap = StrokeCap.Round))
            continue
        }
        if (child) {
            val rx = 6.2f
            val ry = 7.6f * open
            translate(ex, eyeY) { drawOval(k.irisBrush, Offset(-rx, -ry), Size(rx * 2, ry * 2)) }
            if (open > 0.5f) drawArc(look.hair, 215f, 110f, false, Offset(ex - rx - 0.6f, eyeY - ry - 0.8f), Size(rx * 2 + 1.2f, ry * 2 + 1.2f), style = Stroke(1.2f, cap = StrokeCap.Round))
            drawCircle(Color.White, 2.1f * open.coerceAtMost(1f), Offset(ex + 2.2f, eyeY - 2.6f * open))
            drawCircle(Color.White, 1f * open.coerceAtMost(1f), Offset(ex - 2.2f, eyeY + 2.8f * open))
        } else {
            val rx = 5.2f
            val ry = 3.7f * open
            drawOval(Color(0xFFF6EFE6), Offset(ex - rx, eyeY - ry), Size(rx * 2, ry * 2))
            val irisY = eyeY + 0.3f + pose.gaze * 1.2f
            val ir = minOf(3.4f, ry + 0.2f)
            drawCircle(lerp(look.iris, Color(0xFF8A5A36), 0.35f), ir, Offset(ex, irisY))
            drawCircle(look.iris, ir * 0.55f, Offset(ex, irisY))
            drawCircle(Color.White, 1f, Offset(ex + 1.1f, irisY - 1.2f))
            // An upper lid arc instead of a flat line keeps the adult calm rather than sleepy.
            drawArc(look.hair, 195f, 150f, false, Offset(ex - rx - 0.4f, eyeY - ry - 0.6f), Size(rx * 2 + 0.8f, ry * 2 + 1.2f), style = Stroke(1.7f, cap = StrokeCap.Round))
        }
        if (f.eyeSmile > 0.1f) {
            drawOval(look.skin, Offset(ex - 7f, eyeY + 7.5f - f.eyeSmile * 6f), Size(14f, 8f))
        }
    }

    if (child) {
        drawOval(look.blush, Offset(fx - 31f, fy + 8f), Size(14f, 8f))
        drawOval(look.blush, Offset(fx + 17f, fy + 8f), Size(14f, 8f))
    }
    val noseY = fy + if (child) 8f else 9f
    k.curve.apply { rewind(); moveTo(fx - 2.5f, noseY + 2f); quadraticTo(fx, noseY + 4.5f, fx + 2.5f, noseY + 2f) }
    drawPath(k.curve, look.line, style = Stroke(1.6f, cap = StrokeCap.Round), alpha = 0.7f)
    if (!child) drawLine(look.skinShade, Offset(fx + 1f, noseY - 9f), Offset(fx + 2f, noseY + 1f), 1.6f, StrokeCap.Round)

    drawMouth(k, fx, fy + if (child) 19f else 20f, f)
}

private val SIDES = floatArrayOf(-1f, 1f)

private fun DrawScope.drawHair(k: Kit, turn: Float) {
    val b = k.build
    val look = k.look
    val hx = b.headX + turn * 2f
    val top = b.headY - b.headRy
    val rx = b.headRx
    val hair = k.hair
    hair.rewind()
    if (k.child) {
        hair.apply {
            moveTo(hx - rx - 1f, b.headY + 4f)
            // Control points sit high enough that the curve's crest covers the crown.
            cubicTo(hx - rx - 6f, top - 20f, hx + rx + 6f, top - 20f, hx + rx + 1f, b.headY + 4f)
            // Fringe in soft scallops across the forehead.
            lineTo(hx + rx - 3f, b.headY - 12f)
            quadraticTo(hx + rx - 12f, b.headY - 10f, hx + 14f, b.headY - 20f)
            quadraticTo(hx + 8f, b.headY - 13f, hx - 2f, b.headY - 20f)
            quadraticTo(hx - 10f, b.headY - 13f, hx - 17f, b.headY - 21f)
            quadraticTo(hx - rx + 10f, b.headY - 10f, hx - rx + 3f, b.headY - 12f)
            close()
        }
        drawPath(hair, k.hairBrush)
        // A small tuft on top makes the silhouette friendly and unmistakably a child.
        k.tuft.apply {
            rewind()
            moveTo(hx - 2f, top + 2f); quadraticTo(hx + 4f, top - 11f, hx + 12f, top - 6f)
            quadraticTo(hx + 5f, top - 4f, hx + 4f, top + 3f); close()
        }
        drawPath(k.tuft, look.hair)
        drawArc(look.hairShine, 215f, 50f, false, Offset(hx - rx * 0.55f, top + 5f), Size(rx * 0.9f, 22f), style = Stroke(2.6f, cap = StrokeCap.Round))
    } else {
        hair.apply {
            moveTo(hx - rx - 1f, b.headY - 2f)
            cubicTo(hx - rx - 5f, top - 19f, hx + rx + 5f, top - 19f, hx + rx + 1f, b.headY - 2f)
            lineTo(hx + rx - 2f, b.headY - 12f)
            // The front sweeps away from a side part, which reads as a neat grown-up cut.
            quadraticTo(hx + 14f, b.headY - 27f, hx - 7f, b.headY - 23f)
            quadraticTo(hx - rx + 6f, b.headY - 21f, hx - rx + 2f, b.headY - 10f)
            close()
        }
        drawPath(hair, k.hairBrush)
        drawArc(look.hairShine, 200f, 55f, false, Offset(hx - rx * 0.7f, top - 4f), Size(rx * 1.1f, 26f), style = Stroke(2f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMouth(k: Kit, x: Float, y: Float, f: Face) {
    val look = k.look
    val w = if (k.child) 8.5f else 7.5f
    if (f.mouthRound > 0.5f) {
        drawOval(look.lip, Offset(x - 3.6f, y - 4f), Size(7.2f, 8.5f))
        return
    }
    val s = f.smile
    val corner = y - s * 2.2f
    val p = k.mouth
    p.rewind()
    // The low threshold lets the small mouth movement while signing show.
    if (f.mouthOpen > 0.04f) {
        val open = f.mouthOpen * 9f
        p.moveTo(x - w, corner)
        p.quadraticTo(x, y + s * 3f - 1f, x + w, corner)
        p.quadraticTo(x, y + s * 6.5f + open, x - w, corner)
        p.close()
        drawPath(p, look.lip)
        drawOval(Color(0xFFE58A86), Offset(x - w * 0.45f, y + s * 2f + open * 0.45f), Size(w * 0.9f, open * 0.45f + 1f))
    } else {
        p.moveTo(x - w, corner)
        p.quadraticTo(x, y + s * 6.5f, x + w, corner)
        drawPath(p, look.lip, style = Stroke(2.4f, cap = StrokeCap.Round))
    }
}

/** A bone as a quad tapering from width [wa] at [a] to [wb] at [b]. */
private fun DrawScope.quad(k: Kit, a: Offset, b: Offset, wa: Float, wb: Float, color: Color) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy).coerceAtLeast(1e-3f)
    val nx = -dy / len
    val ny = dx / len
    k.quad.apply {
        rewind()
        moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
        lineTo(b.x + nx * wb / 2f, b.y + ny * wb / 2f)
        lineTo(b.x - nx * wb / 2f, b.y - ny * wb / 2f)
        lineTo(a.x - nx * wa / 2f, a.y - ny * wa / 2f)
        close()
    }
    drawPath(k.quad, color)
}

/** Shoulder to wrist with a recorded or two-bone solved elbow, then the hand at the wrist. [front] marks the hand
 *  drawn over the other one, which gets a contact shadow. */
private fun DrawScope.drawArm(
    k: Kit, h: HandPose, side: Side, shift: Offset, front: Boolean = false, elbows: LastElbows,
) {
    val b = k.build
    val look = k.look
    val (sx0, sy0) = b.shoulder(side)
    val s = Offset(sx0 + shift.x, sy0 + shift.y)
    val last = if (side == Side.R) elbows.r else elbows.l
    val (ex, ey) = h.elbowAt(s.x, s.y, b, side, last)
    if (side == Side.R) elbows.r = ex to ey else elbows.l = ex to ey
    val e = Offset(ex, ey)
    val w = Offset(h.x, h.y)
    val aw = b.armWidth
    val wrist = 0.62f * 13f * b.handScale
    val angle = if (h.angle.isNaN()) Math.toDegrees(atan2((w.x - e.x).toDouble(), (-(w.y - e.y)).toDouble())).toFloat() else h.angle
    layoutHand(k, h, side)

    // The adult's sleeve is rolled just past the elbow, so most of the forearm shows.
    val fore = if (look.longSleeves) lerpOffset(e, w, 0.15f) else e
    val foreW = aw * 0.95f
    val elbowW = aw
    // Halo first, under everything, so the forearm and hand separate from a dark shirt or backdrop.
    quad(k, fore, w, foreW + 3f, wrist + 3f, k.halo)
    drawCircle(k.halo, wrist / 2f + 1.5f, w)
    // A front hand resting on the other one casts a short shadow on it, so the silhouettes stay apart.
    if (front) translate(1.4f, 1.8f) { handFrame(w, angle, b.handScale) { drawHandShadow(k) } }
    handFrame(w, angle, b.handScale) { drawHandHalo(k) }

    if (look.longSleeves) {
        quad(k, fore, w, foreW + 1.6f, wrist + 1.6f, k.rim)
        drawCircle(k.rim, wrist / 2f + 0.8f, w)
        quad(k, fore, w, foreW, wrist, look.skin)
        drawCircle(look.skin, wrist / 2f, w)
        val cuff = lerpOffset(e, w, 0.22f)
        quad(k, s, e, aw * 1.2f + 4.4f, elbowW + 4.4f, look.topShade)
        drawCircle(look.topShade, elbowW / 2f + 2.2f, e)
        quad(k, e, cuff, elbowW + 4.4f, foreW + 4.4f, look.topShade)
        quad(k, s, e, aw * 1.2f + 2f, elbowW + 2f, k.sleeve)
        drawCircle(k.sleeve, elbowW / 2f + 1f, e)
        quad(k, e, cuff, elbowW + 2f, foreW + 2f, k.sleeve)
        val len = hypot(w.x - e.x, w.y - e.y).coerceAtLeast(1e-3f)
        val band = lerpOffset(e, w, (0.22f - 4f / len).coerceAtLeast(0.05f))
        quad(k, band, cuff, foreW + 4.4f, foreW + 4.4f, look.topShade)
        quad(k, band, cuff, foreW + 3f, foreW + 3f, look.trim)
    } else {
        quad(k, s, e, aw * 1.2f + 2.4f, elbowW + 2.4f, k.rim)
        drawCircle(k.rim, elbowW / 2f + 1.2f, e)
        quad(k, e, w, foreW + 1.6f, wrist + 1.6f, k.rim)
        drawCircle(k.rim, wrist / 2f + 0.8f, w)
        quad(k, s, e, aw * 1.2f, elbowW, look.skin)
        drawCircle(look.skin, elbowW / 2f, e)
        quad(k, e, w, foreW, wrist, look.skin)
        drawCircle(look.skin, wrist / 2f, w)
        val sleeveEnd = lerpOffset(s, e, 0.5f)
        drawLine(look.topShade, s, sleeveEnd, aw * 1.2f + 4f, StrokeCap.Round)
        drawLine(look.top, s, sleeveEnd, aw * 1.2f + 2.5f, StrokeCap.Round)
        drawLine(look.topShade, lerpOffset(s, e, 0.45f), sleeveEnd, aw * 1.2f + 2.5f, StrokeCap.Butt, alpha = 0.5f)
    }

    if (h.prop == "stick") {
        // A walking stick from the fist to the floor, behind the fingers.
        drawLine(Color(0xFF6B4423), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 262f), 5f, StrokeCap.Round)
        drawLine(Color(0xFF8A5A33), Offset(w.x + 1f, w.y - 6f), Offset(w.x + 4f, 262f), 2f, StrokeCap.Round)
    }
    k.contactStroke = front
    handFrame(w, angle, b.handScale) { drawHand(k, h) }
}

private inline fun DrawScope.handFrame(w: Offset, angle: Float, handScale: Float, crossinline block: DrawScope.() -> Unit) {
    translate(w.x, w.y) {
        rotate(angle, pivot = Offset.Zero) {
            scale(handScale, pivot = Offset.Zero) { block() }
        }
    }
}
