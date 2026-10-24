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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import app.signsetu.data.session.EventType
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.avatar.MiniFace
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.signsetu.data.content.Localized

internal enum class Tool { GESTURE, BOARD, DRAW, LANGUAGE, ATTACH, RULES, DICTIONARY }

/** Every other way to ask, and the session's record: one tap from the main page. Picking something returns there. */
@Composable
internal fun MorePage(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    answers: Int,
    onGroup: (Group) -> Unit,
    onQuestion: (Question) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onAddToRun: (Question) -> Unit,
    onTool: (Tool) -> Unit,
    onLog: () -> Unit,
    onChange: () -> Unit,
    onSecondScreen: (() -> Unit)?,
    onFinish: () -> Unit,
    onCounsel: (() -> Unit)?,
    modifier: Modifier,
    onTell: (() -> Unit)? = null,
    onChildAvatar: (Boolean) -> Unit = {},
) {
    val pending = ui.pending
    val subjectLang = ui.session.subjectLang
    val wide = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(stringResource(R.string.more_questions), Modifier.padding(horizontal = Spacing.lg))
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.more_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) null else { { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.more_search_clear)) } } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        )
        val groups = content.groupsFor(ui.session.role, ui.juvenile)
        // Search-only since 0.5.1; a category chip is still a way in when the officer does not know the wording.
        if (query.isBlank()) {
            SectionLabel(stringResource(R.string.more_browse), Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                groups.forEach { g ->
                    val label = g.label.get(officerLang) ?: g.id
                    key(g.id) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { query = label },
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        // A word matches a question's text in any language, or the name of its category.
        fun hit(vararg texts: Localized?) = words.all { w -> texts.any { t -> t?.values?.any { it.lowercase().contains(w) } == true } }
        Column {
            val asked = remember(ui.events) { ui.events.filter { it.type == EventType.QUESTION_SHOWN }.associateBy({ it.id }, { it.itemId }) }
            val askedIds = remember(asked) { asked.values.filterNotNull().toSet() }
            val answeredIds = remember(ui.events, asked) { ui.events.filter { it.type in AnswerEvents }.mapNotNull { e -> e.shownId?.let(asked::get) }.toSet() }
            val phrases = if (words.isEmpty()) emptyList() else content.phrases.filter { p -> hit(p.label, *p.slots.map { it.text }.toTypedArray()) }
            val questions = if (words.isEmpty()) emptyList()
                else groups.flatMap { g -> content.questionsIn(g.id, ui.session.role, ui.session.profile).filter { hit(it.text, g.label) }.map { g to it } }
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (words.isNotEmpty() && phrases.isEmpty() && questions.isEmpty()) {
                    Text(stringResource(R.string.more_search_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                phrases.forEach { p -> key(p.id) {
                    QuestionRow(
                        cardId = p.slots.firstOrNull()?.card,
                        title = p.label.get(officerLang) ?: p.id,
                        caption = p.label.get(subjectLang)?.takeIf { subjectLang != officerLang },
                        hasGesture = true,
                        hasAudio = ui.session.profile != Profile.ISL_USER && p.slots.any { content.phraseAudio(p, it, subjectLang) != null },
                        selected = (pending as? Pending.PhraseAsk)?.phrase?.id == p.id,
                        status = null,
                        onClick = { onPhrase(p) },
                    )
                } }
                questions.forEach { (g, q) -> key(q.id) {
                    QuestionRow(
                        cardId = q.cards.firstOrNull(),
                        title = q.text.get(officerLang) ?: q.text.values.first(),
                        caption = q.text.get(subjectLang)?.takeIf { subjectLang != officerLang },
                        missing = if (q.text.get(subjectLang) == null) stringResource(R.string.officer_caption_missing, content.language(subjectLang)?.english ?: subjectLang) else null,
                        hasGesture = content.gestureFor(q) != null,
                        hasAudio = content.questionAudio(q.id, subjectLang) != null && ui.session.profile != Profile.ISL_USER,
                        selected = (pending as? Pending.Ask)?.question?.id == q.id,
                        status = when (q.id) { in answeredIds -> Asked.ANSWERED; in askedIds -> Asked.ASKED; else -> null },
                        onClick = { if (g.sensitive && !ui.sensitiveUnlocked) onGroup(g) else onQuestion(q) },
                        onLongClick = { onAddToRun(q) },
                    )
                } }
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
            onCounsel?.let { MoreRow(rememberVectorPainter(Icons.Filled.Face), stringResource(R.string.ai_counsellor_start), null, first = true, onClick = it) }
            onTell?.let { MoreRow(painterResource(R.drawable.ic_hand), stringResource(R.string.tell_me_long), null, first = onCounsel == null, onClick = it) }
            MoreRow(rememberVectorPainter(Icons.Filled.Search), stringResource(if (ui.signLanguage == SignLanguage.ASL) R.string.asl_dictionary else R.string.isl_dictionary), null, first = onTell == null && onCounsel == null) { onTool(Tool.DICTIONARY) }
            MoreRow(rememberVectorPainter(Icons.AutoMirrored.Filled.List), stringResource(R.string.officer_log), answers.takeIf { it > 0 }?.toString(), onClick = onLog)
            MoreRow(rememberVectorPainter(Icons.Filled.Person), stringResource(R.string.officer_change), null, onClick = onChange)
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(ui.child, role = Role.Switch, onValueChange = onChildAvatar).padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniFace(Character.CHILD, 24.dp, Color(0xFFFFDDBE))
                Text(stringResource(R.string.more_child_avatar), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = Spacing.lg))
                Switch(checked = ui.child, onCheckedChange = null)
            }
            onSecondScreen?.let { MoreRow(rememberVectorPainter(Icons.Filled.Phone), stringResource(R.string.second_screen), null, onClick = it) }
            MoreRow(rememberVectorPainter(Icons.Filled.Info), stringResource(R.string.officer_rules), null) { onTool(Tool.RULES) }
        }
        OfficerCard(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm), padding = 0.dp) {
            MoreRow(rememberVectorPainter(Icons.AutoMirrored.Filled.ExitToApp), stringResource(R.string.officer_end), null, first = true, onClick = onFinish)
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

internal enum class Asked { ASKED, ANSWERED }

/** A prepared question or phrase as a full-width row, with whether it was asked or answered in this session. */
@Composable
private fun QuestionRow(
    cardId: String?,
    title: String,
    caption: String?,
    hasGesture: Boolean,
    hasAudio: Boolean,
    selected: Boolean,
    status: Asked?,
    onClick: () -> Unit,
    missing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val statusText = status?.let { stringResource(if (it == Asked.ANSWERED) R.string.more_answered else R.string.more_asked) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large).runnable(onClick, onLongClick, onLongClick?.let { stringResource(R.string.run_add) })
            .semantics { this.selected = selected; statusText?.let { stateDescription = it } },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            cardId?.let {
                Surface(shape = RoundedCornerShape(10.dp), color = PictureWell, modifier = Modifier.padding(end = 12.dp).size(40.dp)) {
                    CardImage(it, Modifier.padding(4.dp))
                }
            }
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    missing != null -> Text(missing, style = MaterialTheme.typography.bodySmall, color = colors.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    caption != null -> Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    !hasGesture && !hasAudio -> Text(stringResource(R.string.officer_media_missing), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            if (hasGesture) MediaDot(painterResource(R.drawable.ic_hand), stringResource(R.string.officer_has_gesture))
            if (hasAudio) MediaDot(painterResource(R.drawable.ic_volume), stringResource(R.string.officer_has_audio))
            statusText?.let {
                val done = status == Asked.ANSWERED
                Surface(shape = RoundedCornerShape(50), color = if (done) colors.primary else colors.secondaryContainer, modifier = Modifier.padding(start = 6.dp)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = if (done) colors.onPrimary else colors.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

/** A prepared question or phrase: its picture, its words, and what the subject will get. */
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
