package app.signsetu.ui.session

import androidx.compose.foundation.layout.only
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.painterResource
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SIGN_LIMIT_MS
import app.signsetu.sign.SignSegment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.text.TextAutoSize

@Composable
internal fun Backdrop(modifier: Modifier) {
    val stage = LocalStageColors.current
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(stage.skyTop, stage.skyBottom)))
        // A low sun and a soft floor give the character a place to stand, without anything to look at instead.
        drawCircle(stage.glow, size.minDimension * 0.42f, Offset(size.width * 0.86f, size.height * 0.16f), alpha = 0.55f)
        drawCircle(stage.glow, size.minDimension * 0.18f, Offset(size.width * 0.08f, size.height * 0.42f), alpha = 0.35f)
        drawOval(stage.ground, Offset(-size.width * 0.2f, size.height * 0.82f), Size(size.width * 1.4f, size.height * 0.5f))
    }
}

private val BubbleTail = 12.dp

/** Each side of the character: the replay pill on the left, a held card or letter tile on the right. */
internal val SideBand = 88.dp

/** A speech bubble whose tail points down at the character. */
private object BubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { BubbleTail.toPx() }
        val r = with(density) { 22.dp.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height - tail, CornerRadius(r, r)))
            moveTo(size.width / 2f - tail, size.height - tail - 1f)
            lineTo(size.width / 2f + tail * 0.2f, size.height)
            lineTo(size.width / 2f + tail, size.height - tail - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
internal fun Bubble(text: String, large: Boolean = false, compact: Boolean = false, highlight: IntRange? = null, onWordTap: ((Int) -> Unit)? = null) {
    val stage = LocalStageColors.current
    val styled = buildAnnotatedString {
        append(text)
        highlight?.takeIf { it.last < text.length }?.let {
            addStyle(SpanStyle(color = stage.onHighlight, background = stage.highlight, fontWeight = FontWeight.Bold), it.first, it.last + 1)
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val replayName = stringResource(R.string.word_replay)
    // The caller's lambda changes every frame of typed-text playback; the tap detector must not restart with it.
    val onTap by rememberUpdatedState(onWordTap)
    val tap = if (onWordTap == null) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { p -> layout?.let { onTap?.invoke(it.getOffsetForPosition(p)) } } }
        .semantics { onClick(replayName) { onTap?.invoke(0); true } }
    // Line height in em so a caption shrunk to fit gains lines instead of clipping.
    val style = when {
        large -> MaterialTheme.typography.headlineMedium
        compact -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }.copy(lineHeight = 1.2.em)
    Surface(
        shape = BubbleShape,
        color = stage.bubble,
        contentColor = stage.onBubble,
        border = BorderStroke(1.dp, stage.onBubble.copy(alpha = 0.18f)),
        shadowElevation = 3.dp,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        // A right or a question must be read to its end: the text shrinks to fit rather than being cut off.
        Text(
            styled,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = if (large) 6 else 5,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp + BubbleTail).then(tap),
        )
    }
}

/** The picture the question is about, held up beside the character. */
@Composable
internal fun HeldCard(cardId: String, label: String?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.rotate(-4f).size(width = SideBand, height = if (label != null) 108.dp else SideBand),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardImage(cardId, Modifier.fillMaxWidth().aspectRatio(1f))
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The word being fingerspelled, its current letter large, so a reader can follow the hands. */
@Composable
internal fun LetterTile(letters: String, current: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp, modifier = modifier.rotate(-4f).size(SideBand, 108.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                letters.getOrNull(current)?.uppercase() ?: "",
                fontFamily = Baloo,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                color = Ink,
            )
            Text(
                buildAnnotatedString {
                    letters.uppercase().forEachIndexed { i, c ->
                        withStyle(SpanStyle(color = if (i == current) Ink else Color(0xFFB8A99A), fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)) { append(c) }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun OfficerHandle(onClick: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    // Small and quiet: the subject should not be drawn to it; the officer knows where it is.
    Box(
        modifier
            .padding(6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = stage.handle, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.show_officer), tint = stage.onHandle, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
internal fun ReplayControls(pace: Float, onReplay: () -> Unit, onPace: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = stage.tool.copy(alpha = 0.9f),
        contentColor = stage.onTool,
        border = BorderStroke(1.dp, stage.onTool.copy(alpha = 0.14f)),
        modifier = modifier.width(56.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay), modifier = Modifier.size(26.dp))
            }
            val shown = when { pace < 0.6f -> "½×"; pace < 0.9f -> "¾×"; else -> "1×" }
            val paceName = stringResource(R.string.show_pace, shown)
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onPace).semantics { contentDescription = paceName }, contentAlignment = Alignment.Center) {
                Text(
                    shown,
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The mirror: the subject sees their own hands; choices sit at the bottom edge of it. */
@Composable
internal fun MirrorWindow(
    camera: @Composable (((LandmarkFrame) -> Unit)?) -> Unit,
    cameraOn: Boolean,
    choices: List<String>,
    numbers: Boolean,
    chosenCard: String?,
    content: ContentBundle,
    lang: String,
    onCard: (String) -> Unit,
    modifier: Modifier,
    proposedCard: String? = null,
) {
    Box(modifier.mirrorFrame()) {
        if (cameraOn) {
            // Outside a sign window the mirror is preview only: no frame is analysed (D-25).
            camera(null)
        } else {
            MirrorPlaceholder()
        }
        if (choices.isNotEmpty()) key(choices) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (numbers) {
                    // Five to a row leaves each key under 48 dp in the camera window of a 360 dp phone.
                    choices.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 40 * choices.indexOf(id)
                                NumberButton(id.removePrefix("n"), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    choices.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 70 * choices.indexOf(id)
                                ChoiceTile(id, content.cards[id]?.label?.pick(lang), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)), name = content.cards[id]?.label?.pick("en"), proposed = proposedCard == id)
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.mirrorFrame(): Modifier {
    val stage = LocalStageColors.current
    val shape = RoundedCornerShape(20.dp)
    // The letterbox around the whole camera frame is the window colour, so the window reads as one calm pane.
    return fillMaxWidth()
        .shadow(3.dp, shape)
        .clip(shape)
        .background(stage.window)
        .border(4.dp, stage.windowFrame.copy(alpha = 0.4f), shape)
}

/**
 * A sign window: a large mirror with the hand and arm skeleton, a dot per sign seen, and Done. Only landmarks are kept,
 * in memory (D-25). A short answer shows its time bar and, after a failed read, the "sign slower" cue; a [narrative]
 * shows neither, so nothing interrupts the person while they tell what happened.
 */
@Composable
internal fun SigningPanel(
    camera: @Composable (((LandmarkFrame) -> Unit)?) -> Unit,
    segments: List<SignSegment>,
    content: ContentBundle,
    lang: String,
    narrative: Boolean,
    onFrame: (LandmarkFrame) -> Unit,
    modifier: Modifier,
) {
    val answer = LocalAnswerColors.current
    val used = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!narrative) used.animateTo(1f, tween(SIGN_LIMIT_MS.toInt(), easing = LinearEasing)) }
    Box(modifier.mirrorFrame()) {
        camera(onFrame)
        if (!narrative) {
            LinearProgressIndicator(
                progress = { used.value },
                color = answer.yes,
                trackColor = Color.White.copy(alpha = 0.3f),
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).height(6.dp).clip(CircleShape),
            )
            SignSlowerCue(segments.lastOrNull()?.takeIf { it.slower }, content, lang, Modifier.align(Alignment.TopCenter).padding(top = 30.dp, start = Spacing.sm, end = Spacing.sm))
        }
        SeenSigns(segments, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.md))
    }
}

/** Entrance for things put in front of the subject: a short rise and settle, so a new picture is noticed. */
@Composable
internal fun rememberPopIn(delayMillis: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    return progress
}

internal fun Modifier.popIn(progress: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    scaleX = 0.7f + 0.3f * p
    scaleY = 0.7f + 0.3f * p
    translationY = (1f - p) * 24.dp.toPx()
}

@Composable
internal fun MirrorPlaceholder() {
    val stage = LocalStageColors.current
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF4A4352), stage.window))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(56.dp))
    }
}

/** What to do now, in one short line over the top of the camera window. */
@Composable
internal fun StageHint(text: String, modifier: Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center, maxLines = 3,
        modifier = modifier.fillMaxWidth().padding(Spacing.sm)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(horizontal = Spacing.sm, vertical = 6.dp),
    )
}
