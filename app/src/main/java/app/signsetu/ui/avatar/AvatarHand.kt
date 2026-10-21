package app.signsetu.ui.avatar

import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val SEG = 7
private const val PH = 12f
private val LENGTHS = floatArrayOf(9.2f, 10.2f, 9.6f, 7.7f)
private val WIDTHS = floatArrayOf(3.1f, 3.2f, 3.0f, 2.6f)
private val TAPER = floatArrayOf(1f, 0.92f, 0.84f)
private val FINGER_FRACTIONS = floatArrayOf(0.46f, 0.30f, 0.24f)
private val THUMB_FRACTIONS = floatArrayOf(0.55f, 0.45f)
private val FRONT_BENDS = floatArrayOf(85f, 100f, 72f)
private val THUMB_BENDS = floatArrayOf(40f, 80f)
private val SIDE_BENDS = floatArrayOf(80f, 95f, 70f)

private fun curl(shape: HandShape, finger: Int) = when (finger) {
    0 -> shape.index
    1 -> shape.middle
    2 -> shape.ring
    3 -> shape.pinky
    else -> shape.thumb
}

/**
 * Lays the hand out in its own frame: wrist at the origin, fingers towards -y. Seen from the front (palm or back)
 * curled fingers are foreshortened; seen from the side they bend in the plane, which reads better for bunched or
 * cupped hands. The layout mirrors [HandPose.point], so contacts land where they are drawn.
 */
internal fun layoutHand(k: Kit, h: HandPose, side: Side) {
    val roll = h.roll.coerceIn(-1f, 1f)
    val shape = h.shape
    k.frontal = abs(roll) >= 0.35f
    if (k.frontal) {
        val palmFacing = roll > 0f
        val thumbSide = if (palmFacing) -side.sign else side.sign
        val wf = lerp(0.6f, 1f, ((abs(roll) - 0.35f) / 0.65f).coerceIn(0f, 1f))
        val pw = 13f * wf
        k.palmFacing = palmFacing
        k.thumbSide = thumbSide
        k.pw = pw
        for (f in 0..3) {
            val bx = thumbSide * (pw / 2f - 1.8f - f * (pw - 3.6f) / 3f)
            val dir = rad(shape.spread * (f - 1.5f) * -9f * thumbSide)
            frontalChain(k.seg, f, bx, -PH + 1.5f, dir, LENGTHS[f], WIDTHS[f] * (0.82f + 0.18f * wf), curl(shape, f), palmFacing)
        }
        // A thumb beside the hand starts low and stays well clear of the index finger, so it reads as a thumb.
        val beside = shape.thumbAcross < 0.5f
        var deg = 52f - shape.thumbAcross * 112f
        if (beside) deg = maxOf(deg, shape.spread * 13.5f + 28f)
        frontalChain(k.seg, 4, thumbSide * (pw / 2f - 0.5f), if (beside) -2f else -3f, rad(thumbSide * deg), 8.8f, 3.7f, shape.thumb * 0.6f, palmFacing)
    } else {
        // Side view: the palm faces the body's midline unless rotated.
        val palmSide = -side.sign
        val nearThumb = roll >= 0f
        k.palmSide = palmSide
        k.nearThumb = nearThumb
        sideChain(k.seg, 4, palmSide * 2.4f, -3.5f, rad(palmSide * (28f + shape.thumbAcross * 30f)), 8.6f, 3.5f, shape.thumb, palmSide, 0.5f, 0f)
        for (f in 0..3) {
            val depth = if (nearThumb) 3 - f else f
            val dir = rad(shape.spread * (f - 1.5f) * 4f)
            val bx = palmSide * (0.6f - f * 0.35f) * (if (nearThumb) 1f else -1f)
            sideChain(k.seg, f, bx, -PH + 1.2f + f * 0.8f, dir, LENGTHS[f], 3.2f, curl(shape, f), palmSide, 1f, (3 - depth) * 0.12f)
        }
    }
}

private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

/** The thumb has two segments seen from the front and three, short, from the side. */
private fun Kit.segs(finger: Int) = if (finger == 4 && frontal) 2 else 3
private fun at(finger: Int, j: Int) = (finger * 3 + j) * SEG

private fun frontalChain(buf: FloatArray, finger: Int, x: Float, y: Float, dir: Float, length: Float, width: Float, curl: Float, palmFacing: Boolean) {
    val thumb = finger == 4
    val fractions = if (thumb) THUMB_FRACTIONS else FINGER_FRACTIONS
    val bends = if (thumb) THUMB_BENDS else FRONT_BENDS
    val ux = sin(dir)
    val uy = -cos(dir)
    var px = x
    var py = y
    var phi = 0f
    for (j in fractions.indices) {
        phi += bends[j] * curl
        val len = length * fractions[j] * cos(rad(phi))
        val i = at(finger, j)
        buf[i] = px; buf[i + 1] = py
        px += ux * len; py += uy * len
        buf[i + 2] = px; buf[i + 3] = py
        buf[i + 4] = width * TAPER[j]
        // Folded segments face away from a back-of-hand view, so they go behind the palm.
        buf[i + 5] = if (!palmFacing && phi > 95f && !thumb) 1f else 0f
        buf[i + 6] = if (phi > 95f) 0.18f else 0f
    }
}

private fun sideChain(
    buf: FloatArray, finger: Int, x: Float, y: Float, dir: Float, length: Float, width: Float, curl: Float,
    palmSide: Float, scale: Float, shade: Float,
) {
    var px = x
    var py = y
    var a = dir
    for (j in 0..2) {
        a += rad(palmSide * SIDE_BENDS[j] * curl)
        val len = length * FINGER_FRACTIONS[j] * scale
        val i = at(finger, j)
        buf[i] = px; buf[i + 1] = py
        px += sin(a) * len; py -= cos(a) * len
        buf[i + 2] = px; buf[i + 3] = py
        buf[i + 4] = width * TAPER[j]
        buf[i + 5] = 0f
        buf[i + 6] = shade
    }
}

private fun DrawScope.segLine(buf: FloatArray, i: Int, color: Color, extra: Float) =
    drawLine(color, Offset(buf[i], buf[i + 1]), Offset(buf[i + 2], buf[i + 3]), buf[i + 4] + extra, StrokeCap.Round)

/** Which segments of a finger to draw: all, only those folded behind the palm, or the rest. */
private const val ALL = 0
private const val BEHIND = 1
private const val FRONT = 2

private fun picked(buf: FloatArray, i: Int, which: Int) = when (which) {
    BEHIND -> buf[i + 5] > 0f
    FRONT -> buf[i + 5] == 0f
    else -> true
}

/** Each finger gets its own outline so neighbouring fingers stay readable. */
private fun DrawScope.drawFinger(k: Kit, finger: Int, which: Int, tone: Color) {
    val buf = k.seg
    for (j in 0 until k.segs(finger)) at(finger, j).let { if (picked(buf, it, which)) segLine(buf, it, k.rim, 1.6f) }
    for (j in 0 until k.segs(finger)) at(finger, j).let { i ->
        if (picked(buf, i, which)) {
            val shade = buf[i + 6]
            segLine(buf, i, if (shade > 0f) lerp(tone, k.look.skinShade, (shade * 2.5f).coerceAtMost(1f)) else tone, 0f)
        }
    }
}

internal fun DrawScope.drawHandHalo(k: Kit) {
    val buf = k.seg
    if (k.frontal) {
        drawRoundRect(k.halo, Offset(-k.pw / 2f - 1.5f, -PH - 1.5f), Size(k.pw + 3f, PH + 3f), CornerRadius(5.5f))
        thenar(k, k.halo, 1.5f)
    } else {
        drawRoundRect(k.halo, Offset(-4.7f, -PH - 1.5f), Size(9.4f, PH + 3f), CornerRadius(4.5f))
    }
    for (f in 0..4) for (j in 0 until k.segs(f)) segLine(buf, at(f, j), k.halo, 3f)
}

/** The ball of the thumb, so the thumb reads as a separate digit growing from the palm. */
private fun DrawScope.thenar(k: Kit, color: Color, grow: Float) {
    val cx = k.thumbSide * k.pw * 0.32f
    val w = k.pw * 0.45f
    drawOval(color, Offset(cx - w / 2f - grow, -4f - 3.5f - grow), Size(w + grow * 2f, 7f + grow * 2f))
}

internal fun DrawScope.drawHand(k: Kit, h: HandPose) {
    val look = k.look
    val buf = k.seg
    val shape = h.shape
    if (!k.frontal) {
        drawRoundRect(k.rim, Offset(-4f, -PH - 0.8f), Size(8f, PH + 1.6f), CornerRadius(3.8f))
        drawRoundRect(look.skin, Offset(-3.2f, -PH), Size(6.4f, PH), CornerRadius(3f))
        // The palm's edge shows its lighter tone on the side it faces.
        drawRoundRect(k.palmTone, Offset(if (k.palmSide > 0f) 2f else -3.2f, -PH + 1f), Size(1.2f, PH - 2f), CornerRadius(0.6f))
        if (!k.nearThumb) drawFinger(k, 4, ALL, look.skin)
        for (d in 0..3) drawFinger(k, if (k.nearThumb) 3 - d else d, ALL, look.skin)
        if (k.nearThumb) drawFinger(k, 4, ALL, look.skin)
        return
    }
    val across = shape.thumbAcross > 0.5f
    val pw = k.pw
    if (!k.palmFacing) {
        if (!across) {
            drawFinger(k, 4, ALL, look.skin)
            if (shape.thumb < 0.5f) nail(k, 4, 1)
        }
        for (f in 0..3) drawFinger(k, f, BEHIND, look.skin)
        thenar(k, k.rim, 0.8f)
        thenar(k, look.skin, 0f)
        drawRoundRect(k.rim, Offset(-pw / 2f - 0.8f, -PH - 0.8f), Size(pw + 1.6f, PH + 1.6f), CornerRadius(4.8f))
        drawRoundRect(k.backBrush, Offset(-pw / 2f, -PH), Size(pw, PH), CornerRadius(4f))
        // Seen from the back, a thumb across the palm is hidden behind the fingers.
        if (across) drawFinger(k, 4, ALL, look.skin)
        for (f in 3 downTo 0) drawFinger(k, f, FRONT, look.skin)
        for (f in 0..3) {
            val i = at(f, 0)
            val c = curl(shape, f)
            val base = Offset(buf[i], buf[i + 1])
            if (c > 0.45f) {
                drawCircle(look.skin, 1.9f, Offset(base.x, -PH + 1f))
                drawCircle(look.line, 1.9f, Offset(base.x, -PH + 1f), style = Stroke(0.5f), alpha = 0.5f)
            }
            drawCircle(k.knuckle, 0.45f * buf[i + 4], Offset(base.x, if (c > 0.45f) -PH + 1f else base.y))
            if (c < 0.35f) {
                crease(k, f, 1, 0.55f)
                crease(k, f, 2, 0.55f)
                nail(k, f, 2)
            }
        }
    } else {
        if (!across) drawFinger(k, 4, ALL, k.palmTone)
        thenar(k, k.rim, 0.8f)
        thenar(k, k.palmTone, 0f)
        drawRoundRect(k.rim, Offset(-pw / 2f - 0.8f, -PH - 0.8f), Size(pw + 1.6f, PH + 1.6f), CornerRadius(4.8f))
        drawRoundRect(k.palmTone, Offset(-pw / 2f, -PH), Size(pw, PH), CornerRadius(4f))
        drawArc(look.skinShade, 200f, 110f, false, Offset(-pw * 0.35f, -PH * 0.75f), Size(pw * 0.7f, PH * 0.9f), style = Stroke(0.9f), alpha = 0.8f)
        for (f in 3 downTo 0) {
            drawFinger(k, f, ALL, k.palmTone)
            if (curl(shape, f) < 0.6f) {
                crease(k, f, 1, 0.4f)
                crease(k, f, 2, 0.4f)
                val i = at(f, 2)
                val w = buf[i + 4]
                val c = lerpOffset(Offset(buf[i], buf[i + 1]), Offset(buf[i + 2], buf[i + 3]), 0.6f)
                drawOval(k.pad, Offset(c.x - 0.3f * w, c.y - 0.4f * w), Size(0.6f * w, 0.8f * w))
            }
        }
        if (across) drawFinger(k, 4, ALL, k.palmTone)
    }
}

/** A short crease across a finger at the start of segment [j]. */
private fun DrawScope.crease(k: Kit, finger: Int, j: Int, alpha: Float) {
    val buf = k.seg
    val i = at(finger, j)
    val dx = buf[i + 2] - buf[i]
    val dy = buf[i + 3] - buf[i + 1]
    val len = hypot(dx, dy)
    if (len < 0.3f) return
    val half = 0.55f * buf[i + 4] / 2f
    val nx = -dy / len * half
    val ny = dx / len * half
    drawLine(k.look.skinShade, Offset(buf[i] - nx, buf[i + 1] - ny), Offset(buf[i] + nx, buf[i + 1] + ny), 0.35f, StrokeCap.Round, alpha = alpha)
}

/** A nail on segment [j] from 35% to 92% of its length, with a small highlight near the tip. */
private fun DrawScope.nail(k: Kit, finger: Int, j: Int) {
    val buf = k.seg
    val i = at(finger, j)
    val a = Offset(buf[i], buf[i + 1])
    val b = Offset(buf[i + 2], buf[i + 3])
    val len = hypot(b.x - a.x, b.y - a.y)
    if (len < 0.5f) return
    val nw = 0.6f * buf[i + 4]
    // Round caps add half the width at each end, so the line is inset by that much.
    var t0 = 0.35f + nw / 2f / len
    var t1 = 0.92f - nw / 2f / len
    if (t1 < t0) { t0 = (t0 + t1) / 2f; t1 = t0 }
    val p0 = lerpOffset(a, b, t0)
    val p1 = lerpOffset(a, b, t1)
    drawLine(k.look.skinShade, p0, p1, nw + 0.5f, StrokeCap.Round, alpha = 0.6f)
    drawLine(k.nail, p0, p1, nw, StrokeCap.Round)
    drawCircle(Color.White, 0.18f * buf[i + 4], lerpOffset(a, b, 0.8f), alpha = 0.5f)
}

internal fun lerpOffset(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
