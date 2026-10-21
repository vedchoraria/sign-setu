package app.signsetu.ui.session

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Star
import app.signsetu.assistant.Suggestion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.components.LocalCloudAi
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.pick
import androidx.compose.runtime.LaunchedEffect
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.Spacing
import androidx.compose.ui.text.input.VisualTransformation
import app.signsetu.data.content.PhraseSlot

/** Prepared questions an officer needs most often, offered on the main page until each has been asked. */
private val QuickQuestions = listOf("s2", "n1", "n2", "n3", "h2", "f2", "i2", "l1")
private const val TRANSLATE_APP = "com.google.android.apps.translate"
/** Pictures carry these before anyone knows the person's language: who we are, safety, help coming, basic needs. */
private val NoLanguageQuestions = listOf("s1", "s5", "s6", "n1", "n2", "n3", "h2")

/** Quick questions and the composer: everything the officer reaches with a thumb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Dock(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    text: String,
    onText: (String) -> Unit,
    assistant: Boolean,
    canRead: Boolean,
    thinking: Boolean,
    suggestion: Suggestion?,
    noHelp: Boolean,
    onAssist: () -> Unit,
    onDiscard: () -> Unit,
    draft: String?,
    onDraftOff: () -> Unit,
    modes: List<SignMode>,
    onMode: (SignMode) -> Unit,
    attached: List<String>,
    onPictures: () -> Unit,
    onQuestion: (Question) -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    onRead: () -> Unit,
    run: List<Question> = emptyList(),
    onRunRemove: (Question) -> Unit = {},
    onAddToRun: (Question) -> Unit = {},
    onRunBasics: () -> Unit = {},
    onTemplate: (Phrase, PhraseSlot) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val typing = text.isNotEmpty()
    val addLabel = stringResource(R.string.run_add)
    val pending = ui.pending
    val subjectLang = ui.session.subjectLang
    val asked = remember(ui.events) { ui.events.filter { it.type == EventType.QUESTION_SHOWN }.mapNotNull { it.text }.toSet() }
    val noLanguage = ui.session.profile == Profile.OTHER_LANGUAGE
    val quick = remember(content, officerLang, asked, pending, noLanguage) {
        (if (noLanguage) (NoLanguageQuestions + QuickQuestions).distinct() else QuickQuestions).mapNotNull { id -> content.questions.firstOrNull { it.id == id } }
            .filter { q -> q.text.pick(officerLang) !in asked || (pending as? Pending.Ask)?.question?.id == q.id }
    }
    Surface(color = colors.surfaceContainerLowest, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(top = Spacing.md)) {
            AnimatedVisibility(!typing && quick.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = Spacing.lg), modifier = Modifier.padding(bottom = Spacing.sm)) {
                    if (noLanguage) item(key = "translate") {
                        val context = LocalContext.current
                        CategoryChip(stringResource(R.string.open_translate), false, locked = false) {
                            // Only opens the app; nothing from the session is passed to it.
                            context.packageManager.getLaunchIntentForPackage(TRANSLATE_APP)?.let(context::startActivity)
                                ?: Toast.makeText(context, R.string.open_translate_missing, Toast.LENGTH_LONG).show()
                        }
                    }
                    if (run.isEmpty()) item(key = "run_basics") { CategoryChip(stringResource(R.string.run_basics), false, locked = false, onClick = onRunBasics) }
                    items(quick, key = { it.id }) { q ->
                        CategoryChip(q.text.pick(officerLang) ?: q.id, (pending as? Pending.Ask)?.question?.id == q.id, locked = false, onLongClick = { onAddToRun(q) }, longClickLabel = addLabel) { onQuestion(q) }
                    }
                }
            }

            // Follow-up prompts: on-device rules, or the optional cloud AI when the officer has switched it on.
            val cloud = LocalCloudAi.current
            val lastAnswerEvent = remember(ui.events) { ui.events.lastOrNull { it.type in AnswerEvents } }
            // Keyed on the answer, not the log size: rows logged after an answer must not drop its suggestions.
            var aiFollowUps by remember(lastAnswerEvent?.id) { mutableStateOf<List<String>>(emptyList()) }
            var aiLoadingFollowUps by remember { mutableStateOf(false) }

            LaunchedEffect(lastAnswerEvent?.id) {
                if (lastAnswerEvent != null && !typing) {
                    aiLoadingFollowUps = true
                    val suggestions = cloud.followUps(
                        lastAnswerEvent.text ?: lastAnswerEvent.cardId ?: "answered",
                        ui.session.profile == Profile.YOUNG_CHILD,
                        subjectNames(ui.events, content, officerLang),
                    )
                    aiFollowUps = suggestions
                    aiLoadingFollowUps = false
                }
            }

            AnimatedVisibility(!typing && aiFollowUps.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                        Text(
                            text = stringResource(R.string.ai_followups_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp),
                    ) {
                        items(aiFollowUps, key = { it }) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                modifier = Modifier
                                    .clickable { onText(prompt) }
                                    .semantics { role = Role.Button; contentDescription = prompt },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("✨", fontSize = 12.sp)
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF92400E),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (typing || attached.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (assistant && typing) AssistChip(thinking, onAssist)
                    ModeLabels.forEach { (mode, label) -> ModeChip(stringResource(label), mode in modes) { onMode(mode) } }
                    attached.forEach { id ->
                        Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, modifier = Modifier.height(32.dp)) {
                            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CardImage(id, Modifier.size(20.dp))
                                Text(content.cards[id]?.label?.pick(officerLang) ?: id, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            } else if (run.isNotEmpty()) {
                RunTray(run, officerLang, onRunRemove)
            } else if (pending != null) {
                Ready(pending, content, officerLang, subjectLang, ui.session.profile, onClear)
            }
            if (typing) CompletionRows(content, text, officerLang, onComplete = onText, onTemplate = onTemplate)
            if (typing && suggestion != null) {
                AssistantCard(suggestion.caption, suggestion.meaning, content.language(subjectLang)?.english ?: subjectLang, onDiscard)
            } else if (typing && draft != null) {
                AssistantCard(draft, null, content.language(subjectLang)?.english ?: subjectLang, onDraftOff)
            } else if (typing && noHelp) {
                Text(stringResource(R.string.ai_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm))
            }

            Composer(
                text = text,
                onText = onText,
                onPictures = onPictures,
                canSend = text.isNotBlank() || pending != null || run.isNotEmpty(),
                onSend = onSend,
                onRead = onRead.takeIf { canRead },
                visualTransformation = rememberSignabilityTint(content),
            )
        }
    }
}

/** What Send will show, with what the subject will get. */
@Composable
internal fun Ready(pending: Pending, content: ContentBundle, officerLang: String, subjectLang: String, profile: Profile, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_ready), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Text(describe(pending, content, officerLang), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (pending is Pending.Ask) MediaSummary(pending.question, content, subjectLang, profile)
                if (pending is Pending.Ask && content.isRights(pending.question)) {
                    Text(stringResource(R.string.rights_notice_officer), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, end = Spacing.sm))
                }
            }
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    onPictures: () -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    onRead: (() -> Unit)?,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPictures, modifier = Modifier.size(48.dp)) {
            Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(if (text.isEmpty()) R.string.action_board else R.string.officer_add_pictures), modifier = Modifier.padding(8.dp))
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                // the keyboard should not learn or suggest from what is typed about a subject.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = visualTransformation,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Spacing.lg, vertical = 13.dp),
                decorationBox = { field ->
                    if (text.isEmpty()) Text(stringResource(R.string.quick_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                },
            )
        }
        // Reading signs is the other half of the conversation, so it sits beside Send; typed text goes first.
        onRead?.let { read ->
            val name = stringResource(R.string.officer_read_signs)
            Surface(
                onClick = read,
                shape = RoundedCornerShape(50),
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.padding(start = 4.dp).height(48.dp).widthIn(min = 48.dp).semantics { contentDescription = name },
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.ic_hand), contentDescription = null, modifier = Modifier.size(22.dp))
                    if (text.isEmpty()) Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) ink.brush else SolidColor(colors.surfaceContainerHighest))
                .clickable(enabled = canSend, role = Role.Button, onClickLabel = stringResource(R.string.officer_show), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.officer_show), tint = if (canSend) Color.White else colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CategoryChip(label: String, selected: Boolean, locked: Boolean, onLongClick: (() -> Unit)? = null, longClickLabel: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.minimumInteractiveComponentSize().height(36.dp).clip(RoundedCornerShape(50)).runnable(onClick, onLongClick, longClickLabel).semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (locked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(14.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AssistChip(thinking: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(ink.brush)
            .clickable(enabled = !thinking, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thinking) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            else Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(stringResource(if (thinking) R.string.ai_working else R.string.ai_suggest), style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** A machine caption shown before Send, always marked as unchecked; [meaning] is the assistant's English back. */
@Composable
private fun AssistantCard(caption: String, meaning: String?, languageName: String, onDiscard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_caption, languageName), style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                Text(caption, style = MaterialTheme.typography.titleSmall)
                if (meaning != null) {
                    Text(stringResource(R.string.ai_meaning), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onDiscard) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (on) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (on) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier.minimumInteractiveComponentSize().height(32.dp).semantics { selected = on },
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** What the subject will get for this question; anything missing is named before the officer shows it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaSummary(question: Question, content: ContentBundle, subjectLang: String, profile: Profile) {
    val items = buildList {
        add(R.string.media_caption to (question.text.pick(subjectLang) != null))
        if (profile != Profile.ISL_USER) add(R.string.media_audio to (content.questionAudio(question.id, subjectLang) != null))
        add(R.string.media_gesture to (content.gestureFor(question) != null))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        items.forEach { (label, present) ->
            val name = stringResource(label)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (present) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (present) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (present) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        if (present) name else stringResource(R.string.media_missing, name),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

private val ModeLabels = listOf(
    SignMode.SIGNS to R.string.mode_signs,
    SignMode.GESTURES to R.string.mode_gestures,
    SignMode.SPELL to R.string.mode_spell,
    SignMode.PICTURES to R.string.mode_pictures,
)
