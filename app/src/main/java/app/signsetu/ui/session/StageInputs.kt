package app.signsetu.ui.session

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.painterResource
import app.signsetu.pair.TEXT_CAP
import androidx.compose.foundation.layout.RowScope

internal class DrawStroke(val points: List<Offset>, val color: Color = Ink)

/** Dark brown text and ink on white cards and paper. */
internal val Ink = Color(0xFF2B2118)

/** Dark ink, and red, blue, green and brown, so a person can mark where it hurts or what colour something was. */
private val InkColors = listOf(Ink, Color(0xFFD32F2F), Color(0xFF1E5BD8), Color(0xFF2E7D32), Color(0xFF8D5524))

private fun Offset.fraction(size: IntSize) = Offset(x / size.width.coerceAtLeast(1), y / size.height.coerceAtLeast(1))
private fun Offset.scaled(size: IntSize) = Offset(x * size.width, y * size.height)

/** The subject's own words, typed large on the stage; the keyboard opens at once and Done sends them. */
@Composable
internal fun WritingPanel(
    content: ContentBundle,
    lang: String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    pauseRow: @Composable () -> Unit,
    modifier: Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val send = { if (text.isNotBlank()) onDone(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = TileEdge, shadowElevation = 2.dp, modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value = text,
                // Input past the cap is ignored, never cut later: the receiver logs what was sent.
                onValueChange = { if (it.length <= TEXT_CAP) text = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink),
                // nothing the subject writes is learned or suggested by the keyboard.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxSize().padding(20.dp).focusRequester(focus),
            )
        }
        if (text.length > TEXT_CAP - 200) {
            Text(stringResource(R.string.write_count, text.length, TEXT_CAP), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.End))
        }
        pauseRow()
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BackButton(content, lang, onBack)
            DoneButton(content, lang, enabled = text.isNotBlank(), onClick = send)
        }
    }
}

/** Done in a writing or drawing panel; it greys out until there is something to send. */
@Composable
private fun RowScope.DoneButton(content: ContentBundle, lang: String, enabled: Boolean, onClick: () -> Unit) {
    val answer = LocalAnswerColors.current
    val word = content.subjectText("done", lang)
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (enabled) answer.yes else Off,
        modifier = Modifier.weight(1f).height(56.dp).named(R.string.stage_done, word == null),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYes)
            word?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = answer.onYes, modifier = Modifier.padding(start = Spacing.sm)) }
        }
    }
}

/** Back to the question's answers, for a subject who opened writing or drawing by mistake. */
@Composable
private fun BackButton(content: ContentBundle, lang: String, onBack: () -> Unit) {
    val name = stringResource(R.string.stage_back)
    val stage = LocalStageColors.current
    PressableSurface(
        onClick = onBack,
        shape = CircleShape,
        color = stage.tool,
        border = stage.toolEdge,
        modifier = Modifier.size(56.dp).semantics {
            role = Role.Button
            contentDescription = content.subjectText("back", lang) ?: name
        },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = stage.onTool) }
    }
}

/** A name for a control whose word is missing in the subject's language, so a screen reader still has one. */
@Composable
private fun Modifier.named(@StringRes id: Int, needed: Boolean): Modifier {
    val name = stringResource(id)
    return semantics {
        role = Role.Button
        if (needed) contentDescription = name
    }
}

@Composable
internal fun DrawingPanel(content: ContentBundle, lang: String, strokes: SnapshotStateList<DrawStroke>, onDone: (ByteArray) -> Unit, onBack: (() -> Unit)?, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(Ink) }
    val paper = Color.White
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = paper,
            border = TileEdge,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            // Kept in canvas fractions, so a drawing survives the canvas changing size.
                            onDragEnd = { strokes.add(DrawStroke(current.map { it.fraction(canvasSize) }, ink)); current = emptyList() },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                (strokes.map { s -> DrawStroke(s.points.map { it.scaled(canvasSize) }, s.color) } + DrawStroke(current, ink)).forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, s.color, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        val stage = LocalStageColors.current
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            InkColors.forEach { c ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.RadioButton) { ink = c }.semantics { selected = ink == c },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(if (ink == c) 40.dp else 32.dp).background(c, CircleShape).border(if (ink == c) 3.dp else 0.dp, Color.White, CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            // Undo takes back one line; a slip of the finger should not cost the whole picture.
            PressableSurface(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, shape = CircleShape, color = stage.tool, border = stage.toolEdge,
                modifier = Modifier.size(48.dp).semantics { contentDescription = content.subjectText("undo", lang) ?: "Undo" },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_undo), contentDescription = null, tint = stage.onTool) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            onBack?.let { BackButton(content, lang, it) }
            PressableSurface(
                onClick = { strokes.clear() }, shape = RoundedCornerShape(50), color = stage.tool, border = stage.toolEdge,
                modifier = Modifier.weight(1f).height(56.dp).named(R.string.stage_clear, content.subjectText("clear", lang) == null),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = stage.onTool)
                    content.subjectText("clear", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
            DoneButton(content, lang, enabled = strokes.isNotEmpty()) {
                if (strokes.isNotEmpty()) onDone(renderPng(strokes.map { s -> DrawStroke(s.points.map { it.scaled(canvasSize) }, s.color) }, canvasSize, paper.toArgb()))
            }
        }
    }
}

private fun renderPng(strokes: List<DrawStroke>, size: IntSize, paper: Int): ByteArray {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(paper)
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 8 * density
    }
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        paint.color = s.color.toArgb()
        val path = android.graphics.Path().apply {
            moveTo(s.points[0].x, s.points[0].y)
            s.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

/** After a requested drawing: thanks, the way back to the officer, and a way to add to it. */
@Composable
internal fun HandBackPanel(content: ContentBundle, lang: String, onDrawMore: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically)) {
        Surface(shape = CircleShape, color = LocalAnswerColors.current.yes, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, tint = LocalAnswerColors.current.onYes, modifier = Modifier.size(56.dp)) }
        }
        content.subjectText("give_back", lang)?.let {
            Text(it, fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, textAlign = TextAlign.Center, color = stage.onBubble)
        }
        PressableSurface(
            onClick = onDrawMore, shape = RoundedCornerShape(50), color = stage.tool, border = stage.toolEdge,
            modifier = Modifier.height(56.dp).widthIn(min = 160.dp).named(R.string.stage_draw, content.subjectText("draw_more", lang) == null),
        ) {
            Row(Modifier.padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Create, contentDescription = null, tint = stage.onTool)
                content.subjectText("draw_more", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
    }
}
