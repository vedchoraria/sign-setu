package app.signsetu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.signsetu.data.content.ContentRepository
import androidx.compose.foundation.background
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.SubjectCardMin

val LocalCloudAi = staticCompositionLocalOf<app.signsetu.assistant.CloudAi> { error("CloudAi not provided") }
val LocalContentRepository = staticCompositionLocalOf<ContentRepository> { error("ContentRepository not provided") }

@Composable
fun CardImage(cardId: String, modifier: Modifier = Modifier) {
    val repo = LocalContentRepository.current
    val image by produceState(repo.cachedCardImage(cardId), cardId) { value = value ?: repo.cardImage(cardId) }
    // Fade in only when the image arrives late; a cached one is already opaque.
    val alpha by animateFloatAsState(if (image != null) 1f else 0f, tween(180), label = "card_fade")
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) Box(Modifier.fillMaxSize().shimmer())
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) }
    }
}

/** A subject-facing picture card. Large, labelled, and selectable; selection shows a check, not just a colour. */
@Composable
fun PictureCard(
    cardId: String,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minSize: Dp = SubjectCardMin,
) {
    val answer = LocalAnswerColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "card_press")
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = minSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                role = Role.Button
                this.selected = selected
                label?.let { contentDescription = it }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) BorderStroke(4.dp, answer.selection) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Box {
            Column(
                Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CardImage(cardId, Modifier.fillMaxWidth().aspectRatio(1f).background(PictureWell, MaterialTheme.shapes.medium).padding(Spacing.sm))
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
            if (selected) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = answer.selection,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm).size(28.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

/** The officer-side white card: border instead of shadow, so it holds in dark theme and in sunlight. */
@Composable
fun OfficerCard(modifier: Modifier = Modifier, padding: Dp = Spacing.lg, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(if (padding > 0.dp) Spacing.md else 0.dp), content = content)
    }
}

/** One option of a single choice: tinted fill, 2 dp border and a check when selected, never colour alone. */
@Composable
fun ChoiceTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).selectable(selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Box(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), contentAlignment = Alignment.Center) {
            content()
            if (selected) {
                Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(2.dp))
                }
            }
        }
    }
}
