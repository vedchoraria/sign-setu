package app.signsetu.ui.session

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Question
import app.signsetu.data.content.Signability
import app.signsetu.data.content.complete
import app.signsetu.data.content.pick
import app.signsetu.data.content.signabilitySpans
import app.signsetu.data.content.templatesFor
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing

/** Colour for what the character will do with a word; spelled words stay plain so they stand out by contrast. */
@Composable
private fun signabilityColor(kind: Signability): Color? {
    val answer = LocalAnswerColors.current
    return when (kind) {
        Signability.SIGN -> answer.yes
        Signability.GESTURE -> answer.selection
        Signability.PICTURE -> MaterialTheme.colorScheme.tertiary
        Signability.SPELL -> null
    }
}

private fun Signability.label() = when (this) {
    Signability.SIGN -> R.string.complete_sign
    Signability.GESTURE -> R.string.complete_gesture
    Signability.PICTURE -> R.string.complete_picture
    Signability.SPELL -> R.string.complete_spell
}

/** Tints each typed word by what the character will do with it, so a word about to be fingerspelled is visible. */
@Composable
fun rememberSignabilityTint(content: ContentBundle): VisualTransformation {
    val tints = Signability.entries.associateWith { signabilityColor(it)?.copy(alpha = 0.18f) }
    return remember(content, tints) {
        VisualTransformation { text ->
            val styled = buildAnnotatedString {
                append(text)
                content.signabilitySpans(text.text).forEach { (range, kind) ->
                    tints[kind]?.let { addStyle(SpanStyle(background = it), range.first, range.last + 1) }
                }
            }
            TransformedText(styled, OffsetMapping.Identity)
        }
    }
}

/** The word being typed: the text after the last space. */
fun currentToken(text: String): String = text.substringAfterLast(' ').substringAfterLast('\n')

/** Vocabulary completions for the word being typed and reviewed phrase templates about it, directly above the field. */
@Composable
fun CompletionRows(content: ContentBundle, text: String, officerLang: String, onComplete: (String) -> Unit, onTemplate: (Phrase, PhraseSlot) -> Unit) {
    val token = currentToken(text)
    val completions = remember(content, token) { content.complete(token) }
    val templates = remember(content, token, completions) {
        content.templatesFor(token).ifEmpty { completions.firstOrNull()?.let { content.templatesFor(it.form) }.orEmpty() }
    }
    if (completions.isEmpty() && templates.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (completions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = Spacing.lg)) {
            items(completions, key = { it.form }) { c ->
                val name = stringResource(R.string.complete_word, c.form, stringResource(c.kind.label()))
                val dot = signabilityColor(c.kind) ?: colors.outline
                Surface(
                    onClick = { onComplete(text.dropLast(token.length) + c.form + " ") },
                    shape = RoundedCornerShape(50),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.height(36.dp).semantics { contentDescription = name },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Text(c.form, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (templates.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = Spacing.lg)) {
            items(templates, key = { (p, s) -> "${p.id}:${s.card}" }) { (p, s) ->
                Surface(
                    onClick = { onTemplate(p, s) },
                    shape = RoundedCornerShape(50),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier.height(36.dp),
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(s.text.pick(officerLang) ?: s.card, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Under an answer bubble: whose meaning it is, the place of the picture chosen, its timing, and Say it back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnswerMeta(grade: Grade?, trail: Trail?, position: Pair<Int, Int>?, onSayBack: (() -> Unit)?, cue: String? = null) {
    val res = LocalResources.current
    val colors = MaterialTheme.colorScheme
    val answer = LocalAnswerColors.current
    FlowRow(Modifier.padding(start = 4.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        grade?.let { g ->
            val (bg, fg) = when (g) {
                Grade.CONFIRMED -> answer.yesContainer to answer.onYesContainer
                Grade.SAID_NO -> answer.noContainer to answer.onNoContainer
                Grade.OFFICER_READ, Grade.MACHINE_READ -> colors.surfaceContainerHigh to colors.onSurface
                Grade.UNCLEAR -> colors.tertiaryContainer to colors.onTertiaryContainer
                Grade.SAID -> colors.secondaryContainer to colors.onSecondaryContainer
            }
            Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
                Text(stringResource(g.label()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        val line = listOfNotNull(
            cue,
            position?.let { (n, of) -> stringResource(R.string.choice_position, n, of) },
            trail?.let { trailText(res, it) },
        ).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        onSayBack?.let { TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.say_back), style = MaterialTheme.typography.labelMedium) } }
    }
}

/** A gentle flag on a bubble whose answer changed or came slowly: worth a second look, nothing more. */
@Composable
fun trailBorder(trail: Trail?): BorderStroke? = if (trail?.unsteady == true) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null

/** The officer edits their reading of an answer line; the subject's own words are pre-filled as written. */
@Composable
fun SayBackDialog(e: SessionEvent, draft: String, onDraft: (String) -> Unit, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var words by rememberSaveable(e.id) { mutableStateOf(draft) }
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.say_back_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.say_back_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = words,
                    onValueChange = { words = it; onDraft(it) },
                    singleLine = true,
                    // SEC-05: the keyboard should not learn from what is typed about a subject.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSend(words) }, enabled = words.isNotBlank()) { Text(stringResource(R.string.officer_show)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Questions queued for one handover, numbered in the order they will be asked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunTray(run: List<Question>, officerLang: String, onRemove: (Question) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val remove = stringResource(R.string.run_remove)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm).fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.run_title, run.size), style = MaterialTheme.typography.labelSmall, color = colors.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                run.forEachIndexed { i, q ->
                    Surface(shape = RoundedCornerShape(50), color = colors.surfaceContainerLowest, modifier = Modifier.heightIn(min = 48.dp)) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Ellipsised so a long question never pushes its remove button out of the chip.
                            Text("${i + 1}  ${q.text.pick(officerLang) ?: q.id}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            IconButton(onClick = { onRemove(q) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = remove, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A chip that a long press adds to the question run. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.runnable(onClick: () -> Unit, onLongClick: (() -> Unit)?, longClickLabel: String?): Modifier =
    combinedClickable(role = Role.Button, onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick)
