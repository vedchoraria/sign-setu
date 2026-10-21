package app.signsetu.ui.session

import androidx.compose.foundation.border
import app.signsetu.assistant.Suggestion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import app.signsetu.ui.components.OfficerCard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.Group
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.pick
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone

internal enum class Tool { GESTURE, BOARD, DRAW, LANGUAGE, ATTACH, RULES, DICTIONARY }

/** Every other way to ask, and the session's record: one tap from the main page. Picking something returns there. */
@Composable
internal fun MorePage(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    answers: Int,
    onGroup: (Group) -> Unit,
    onPhrases: () -> Unit,
    onQuestion: (Question) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onAddToRun: (Question) -> Unit,
    onTool: (Tool) -> Unit,
    onLog: () -> Unit,
    onChange: () -> Unit,
    onSecondScreen: (() -> Unit)?,
    modifier: Modifier,
    onTell: (() -> Unit)? = null,
) {
    val pending = ui.pending
    val subjectLang = ui.session.subjectLang
    val wide = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(stringResource(R.string.more_questions), Modifier.padding(horizontal = Spacing.lg))
        Column {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = Spacing.lg)) {
                items(content.groupsFor(ui.session.role, ui.juvenile).filterNot { it.sensitive }, key = { it.id }) { g ->
                    CategoryChip(g.label.pick(officerLang) ?: g.id, ui.selectedGroup == g.id, locked = false) { onGroup(g) }
                }
                if (content.phrases.isNotEmpty()) {
                    item(key = PHRASES) { CategoryChip(stringResource(R.string.officer_phrases), ui.selectedGroup == PHRASES, locked = false, onClick = onPhrases) }
                }
                // Sensitive groups stay last and locked until confirmed (FR-12).
                items(content.groupsFor(ui.session.role, ui.juvenile).filter { it.sensitive }, key = { it.id }) { g ->
                    CategoryChip(g.label.pick(officerLang) ?: g.id, ui.selectedGroup == g.id, locked = !ui.sensitiveUnlocked) { onGroup(g) }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                if (ui.selectedGroup == PHRASES) {
                    items(content.phrases, key = { it.id }) { p ->
                        Suggestion(
                            cardId = p.slots.firstOrNull()?.card,
                            title = p.label.pick(officerLang) ?: p.id,
                            caption = p.label.pick(subjectLang)?.takeIf { subjectLang != officerLang },
                            hasGesture = true,
                            hasAudio = ui.session.profile != Profile.ISL_USER && p.slots.any { content.phraseAudio(p, it, subjectLang) != null },
                            selected = (pending as? Pending.PhraseAsk)?.phrase?.id == p.id,
                            wide = wide,
                            onClick = { onPhrase(p) },
                        )
                    }
                }
                // Lesson-sheet hook: role and profile filter (Lesson.kt askable).
                items(content.questionsIn(ui.selectedGroup, ui.session.role, ui.session.profile), key = { it.id }) { q ->
                    Suggestion(
                        cardId = q.cards.firstOrNull(),
                        title = q.text.pick(officerLang) ?: q.text.values.first(),
                        caption = q.text.pick(subjectLang)?.takeIf { subjectLang != officerLang },
                        missing = if (q.text.pick(subjectLang) == null) stringResource(R.string.officer_caption_missing, content.language(subjectLang)?.english ?: subjectLang) else null,
                        hasGesture = content.gestureFor(q) != null,
                        hasAudio = content.questionAudio(q.id, subjectLang) != null && ui.session.profile != Profile.ISL_USER,
                        selected = (pending as? Pending.Ask)?.question?.id == q.id,
                        wide = wide,
                        onClick = { onQuestion(q) },
                        onLongClick = { onAddToRun(q) },
                    )
                }
            }
        }
        SectionLabel(stringResource(R.string.more_tools), Modifier.padding(horizontal = Spacing.lg))
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            ToolButton(painterResource(R.drawable.ic_hand), stringResource(R.string.action_gesture), pending is Pending.ShowBack) { onTool(Tool.GESTURE) }
            ToolButton(painterResource(R.drawable.ic_grid), stringResource(R.string.action_board), pending is Pending.Cards) { onTool(Tool.BOARD) }
            ToolButton(rememberVectorPainter(Icons.Filled.Create), stringResource(R.string.action_draw), pending == Pending.Drawing) { onTool(Tool.DRAW) }
            ToolButton(painterResource(R.drawable.ic_translate), stringResource(R.string.action_language), pending == Pending.LanguageFinder) { onTool(Tool.LANGUAGE) }
        }
        SectionLabel(stringResource(R.string.more_session), Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md))
        OfficerCard(Modifier.padding(horizontal = Spacing.lg), padding = 0.dp) {
            // Up to three minutes of signing with nothing on screen to interrupt; replayed later with an interpreter.
            onTell?.let { MoreRow(painterResource(R.drawable.ic_hand), stringResource(R.string.tell_me_long), null, first = true, onClick = it) }
            MoreRow(painterResource(R.drawable.ic_hand), stringResource(if (ui.signLanguage == SignLanguage.ASL) R.string.asl_dictionary else R.string.isl_dictionary), null, first = onTell == null) { onTool(Tool.DICTIONARY) } // ISL hook
            MoreRow(rememberVectorPainter(Icons.AutoMirrored.Filled.List), stringResource(R.string.officer_log), answers.takeIf { it > 0 }?.toString(), onClick = onLog)
            MoreRow(rememberVectorPainter(Icons.Filled.Person), stringResource(R.string.officer_change), null, onClick = onChange)
            onSecondScreen?.let { MoreRow(rememberVectorPainter(Icons.Filled.Phone), stringResource(R.string.second_screen), null, onClick = it) }
            MoreRow(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.officer_rules), null) { onTool(Tool.RULES) }
        }
    }
}

@Composable
private fun MoreRow(icon: Painter, label: String, badge: String?, first: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (!first) HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = Spacing.lg))
        badge?.let { Badge(Modifier.padding(end = Spacing.sm)) { Text(it) } }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** A prepared question or phrase: its picture, its words, and what the subject will get (FR-17). */
@Composable
internal fun Suggestion(
    cardId: String?,
    title: String,
    caption: String?,
    hasGesture: Boolean,
    hasAudio: Boolean,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    missing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.width(if (wide) 272.dp else 200.dp).heightIn(min = if (wide) 148.dp else 104.dp)
            .clip(MaterialTheme.shapes.large).runnable(onClick, onLongClick, onLongClick?.let { stringResource(R.string.run_add) })
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = PictureWell, modifier = Modifier.size(30.dp)) {
                    cardId?.let { CardImage(it, Modifier.padding(3.dp)) }
                }
                Spacer(Modifier.weight(1f))
                if (hasGesture) MediaDot(painterResource(R.drawable.ic_hand), stringResource(R.string.officer_has_gesture))
                if (hasAudio) MediaDot(painterResource(R.drawable.ic_volume), stringResource(R.string.officer_has_audio))
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.padding(start = 4.dp).size(20.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(3.dp))
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                missing != null -> Text(missing, style = MaterialTheme.typography.bodySmall, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                caption != null -> Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                !hasGesture && !hasAudio -> Text(stringResource(R.string.officer_media_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MediaDot(painter: Painter, name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(start = 4.dp).size(22.dp).semantics { contentDescription = name }) {
        Icon(painter, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun ToolButton(icon: Painter, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) colors.primary else colors.secondaryContainer,
            contentColor = if (selected) colors.onPrimary else colors.onSecondaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp).width(80.dp),
        )
    }
}
