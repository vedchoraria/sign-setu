package app.signsetu.ui.session

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.only
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.theme.StageColors
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.text.TextAutoSize

@Composable
internal fun ChoiceTile(cardId: String, label: String?, chosen: Boolean, onClick: () -> Unit, modifier: Modifier, name: String? = label, proposed: Boolean = false) {
    val answer = LocalAnswerColors.current
    val pulse = proposalPulse(proposed && !chosen)
    val suggested = if (proposed && !chosen) stringResource(R.string.stage_proposed) else null
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = if (chosen) BorderStroke(4.dp, answer.selection) else if (pulse > 0f) BorderStroke(4.dp, answer.selection.copy(alpha = pulse)) else TileEdge,
        modifier = modifier.heightIn(min = 96.dp).semantics {
            role = Role.Button
            (label ?: name)?.let { contentDescription = it }
            selected = chosen
            suggested?.let { stateDescription = it }
        },
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Without a word in their language the picture carries the meaning alone, so it gets the room.
            CardImage(cardId, Modifier.size(if (label != null) 58.dp else 76.dp))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun NumberButton(digit: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answer = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = CircleShape,
        color = if (chosen) answer.selection else Color.White,
        border = if (chosen) null else TileEdge,
        modifier = modifier.aspectRatio(1f).semantics {
            role = Role.Button
            contentDescription = digit
            selected = chosen
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = if (chosen) answer.onSelection else Ink)
        }
    }
}


@Composable
internal fun PauseRow(content: ContentBundle, lang: String, chosenCard: String?, onCard: (String) -> Unit, compact: Boolean = false) {
    val answer = LocalAnswerColors.current
    SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosenCard == "stop", { onCard("stop") }, Modifier.fillMaxWidth(), height = if (compact) 48.dp else 52.dp)
}

/**
 * Yes, No and the answers that keep a conversation honest sit in narrow rails either side of the camera, always in the
 * same place with picture, word and colour (FR-20, NFR-21, ABE 2.104 to 2.108). Every tap is logged after the prompt shown.
 */
@Composable
internal fun Rail(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.width(68.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically), content = content)

@Composable
internal fun ColumnScope.RailCard(cardId: String, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, proposed: Boolean = false, onClick: () -> Unit) =
    RailButton(label, name, color, onColor, chosen, false, onClick, proposed) { CardImage(cardId, Modifier.size(if (label != null) 28.dp else 34.dp)) }

@Composable
internal fun ColumnScope.RailIcon(icon: ImageVector, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean = false, tool: Boolean = false, proposed: Boolean = false, onClick: () -> Unit) =
    RailIcon(rememberVectorPainter(icon), label, name, color, onColor, chosen, tool, proposed, onClick)

@Composable
internal fun ColumnScope.RailIcon(icon: Painter, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean = false, tool: Boolean = false, proposed: Boolean = false, onClick: () -> Unit) =
    RailButton(label, name, color, onColor, chosen, tool, onClick, proposed) { Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(26.dp)) }

@Composable
private fun ColumnScope.RailButton(label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, tool: Boolean, onClick: () -> Unit, proposed: Boolean = false, icon: @Composable () -> Unit) {
    val pulse = proposalPulse(proposed && !chosen)
    // The pulse is the only sign of a proposal, so screen readers hear it too.
    val suggested = if (proposed && !chosen) stringResource(R.string.stage_proposed) else null
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = color,
        border = when {
            chosen -> BorderStroke(3.dp, onColor)
            pulse > 0f -> BorderStroke(4.dp, onColor.copy(alpha = pulse))
            tool -> BorderStroke(1.dp, onColor.copy(alpha = 0.16f))
            else -> null
        },
        // 68 dp fits an icon over two lines of Hindi; friendly touch target for children and mute users
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().height(68.dp)
            .semantics {
                role = Role.Button
                contentDescription = label ?: name
                selected = chosen
                suggested?.let { stateDescription = it }
            },
    ) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            label?.let { Text(it, fontFamily = Baloo, fontWeight = FontWeight.Bold, lineHeight = 12.sp, color = onColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp)) }
        }
        // A tick as well as the ring: the chosen answer never relies on colour or a thin line alone.
        if (chosen) Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp).background(onColor, CircleShape).padding(1.dp))
        }
    }
}

/** A picture over its word, for the three answers that keep a conversation honest. */
@Composable
internal fun StripAnswer(cardId: String, label: String?, name: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answer = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = answer.unsure,
        border = if (chosen) BorderStroke(3.dp, answer.onUnsure) else null,
        modifier = modifier.height(76.dp).semantics {
            role = Role.Button
            contentDescription = label ?: name
            selected = chosen
        },
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CardImage(cardId, Modifier.size(if (label != null) 28.dp else 44.dp))
            label?.let {
                Text(it, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, color = answer.onUnsure, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


@Composable
internal fun SmallAnswer(
    cardId: String, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, onClick: () -> Unit, modifier: Modifier, height: Dp = 52.dp,
) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color,
        border = if (chosen) BorderStroke(3.dp, onColor) else null,
        modifier = modifier.height(height).semantics {
            role = Role.Button
            contentDescription = label ?: name
            selected = chosen
        },
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CardImage(cardId, Modifier.size(if (label != null) 30.dp else 40.dp))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = onColor, modifier = Modifier.padding(start = Spacing.sm)) }
        }
    }
}

/** Buttons squash softly and tick in the hand when pressed, so a child sees and feels the tap land. */
@Composable
internal fun PressableSurface(
    onClick: () -> Unit,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    // A deaf child cannot hear the tap land; a short tick in the hand confirms it.
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onClick() },
        shape = shape,
        color = color,
        border = border,
        shadowElevation = shadowElevation,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content,
    )
}

@Composable
internal fun LanguageGrid(content: ContentBundle, chosen: String?, modifier: Modifier, onLanguage: (String) -> Unit) {
    val answer = LocalAnswerColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        content.languages.chunked(3).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { l ->
                    val selected = chosen == l.code
                    PressableSurface(
                        onClick = { onLanguage(l.code) },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = if (selected) BorderStroke(4.dp, answer.selection) else TileEdge,
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(l.name, fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.english, fontSize = 11.sp, color = Color(0xFF6B5E50), maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal val StageColors.toolEdge get() = BorderStroke(1.dp, onTool.copy(alpha = 0.16f))

/** The ring on an answer a gesture proposed, pulsing until the subject taps; 0 when nothing is proposed. */
@Composable
private fun proposalPulse(on: Boolean): Float =
    if (on) rememberInfiniteTransition(label = "proposed").animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ring").value else 0f
