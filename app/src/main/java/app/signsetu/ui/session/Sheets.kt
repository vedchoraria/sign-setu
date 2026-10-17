package app.signsetu.ui.session

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import app.signsetu.ui.components.BrandButton
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.theme.LocalStageColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.PictureCard
import app.signsetu.ui.start.LanguagePicker
import app.signsetu.ui.start.RoleChoice
import app.signsetu.ui.start.JuvenileNotice
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import app.signsetu.ui.components.FilterObscuredTouches
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.toMutableStateList
import app.signsetu.data.session.EventSource
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SkeletonPlayer
import app.signsetu.sign.rememberReplayState
import kotlin.math.roundToInt

private const val FAVOURITES = "*favourites"

/** Fills a phrase-bank sentence: the officer picks what it is about (FR-62). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseSheet(phrase: Phrase, content: ContentBundle, lang: String, onPick: (PhraseSlot) -> Unit, onDismiss: () -> Unit) {
    OfficerSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg)) {
            SheetTitle(phrase.label.pick(lang) ?: phrase.id)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(top = Spacing.md),
            ) {
                items(phrase.slots, key = { it.card }) { slot ->
                    PictureCard(slot.card, content.cards[slot.card]?.label?.pick(lang), onClick = { onPick(slot) }, minSize = 104.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSheet(
    content: ContentBundle,
    lang: String,
    initial: List<String>,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    onUse: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onDraft: (List<String>) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Favourites open first once the officer has starred any (FR-19).
    var category by rememberSaveable { mutableStateOf(if (favourites.isNotEmpty()) FAVOURITES else null) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val cards = content.cards.values.filter { c ->
        (category == null || c.category == category || (category == FAVOURITES && c.id in favourites)) &&
            (query.isBlank() || c.label.values.any { it.contains(query.trim(), ignoreCase = true) })
    }
    OfficerSheet({ onDraft(chosen.toList()); onDismiss() }) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = Spacing.lg)) {
            SheetTitle(stringResource(R.string.board_title))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.board_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.md)) {
                item {
                    FilterChip(
                        selected = category == FAVOURITES,
                        onClick = { category = FAVOURITES },
                        label = { Text(stringResource(R.string.board_favourites)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.board_all)) }) }
                items(content.categories, key = { it.id }) { c ->
                    FilterChip(selected = category == c.id, onClick = { category = c.id }, label = { Text(c.label.pick(lang) ?: c.id) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(cards, key = { it.id }) { card ->
                    Box {
                        PictureCard(
                            cardId = card.id,
                            label = card.label.pick(lang),
                            selected = card.id in chosen,
                            onClick = { if (card.id in chosen) chosen.remove(card.id) else chosen.add(card.id) },
                            minSize = 104.dp,
                        )
                        val starred = card.id in favourites
                        IconToggleButton(
                            checked = starred,
                            onCheckedChange = { onFavourite(card.id) },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 6.dp).size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.board_favourite),
                                // Both at least 3:1 on the picture well (NFR-21).
                                tint = if (starred) Color(0xFFB86E00) else Color(0xFF8A7760),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            BrandButton(
                onClick = { onUse(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.md).height(52.dp),
            ) { Text(stringResource(R.string.board_use, chosen.size)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GestureSheet(content: ContentBundle, lang: String, onPick: (LexiconEntry, List<LexiconEntry>) -> Unit, onDismiss: () -> Unit) {
    var location by rememberSaveable { mutableStateOf<BodyLocation?>(null) }
    var action by rememberSaveable { mutableStateOf<HandAction?>(null) }
    val results = content.lookup(location, action)
    OfficerSheet(onDismiss) {
        LazyColumn(
            Modifier.fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SheetTitle(stringResource(R.string.gl_title)) }
            item { SectionLabel(stringResource(R.string.gl_where)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    BodyMap(location, onSelect = { location = it }, selectedLabel = location?.let { stringResource(it.label()) })
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BodyLocation.entries.forEach { l ->
                        FilterChip(selected = location == l, onClick = { location = if (location == l) null else l }, label = { Text(stringResource(l.label())) })
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.gl_what)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HandAction.entries.forEach { a ->
                        FilterChip(selected = action == a, onClick = { action = if (action == a) null else a }, label = { Text(stringResource(a.label())) })
                    }
                }
            }
            if (location != null || action != null) {
                item { SectionLabel(stringResource(R.string.gl_results)) }
                if (results.isEmpty()) {
                    item { Text(stringResource(R.string.gl_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(results, key = { it.id }) { entry ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(
                            headlineContent = { Text(entry.label.pick(lang) ?: entry.id) },
                            supportingContent = { Text(entry.how.pick(lang) ?: entry.how["en"].orEmpty()) },
                            leadingContent = { GesturePreview(content, entry) },
                            trailingContent = { TextButton(onClick = { onPick(entry, results) }) { Text(stringResource(R.string.gl_show_back)) } },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(events: List<SessionEvent>, note: String, onNote: (String) -> Unit, onAddNote: (String) -> Unit, onDismiss: () -> Unit) {
    OfficerSheet(onDismiss) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = Spacing.lg).imePadding()) {
            SheetTitle(stringResource(R.string.officer_log))
            if (events.isEmpty()) {
                Text(stringResource(R.string.log_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                    // Prompt rows are the machine layer of the record; the report prints them in full.
                    items(events.withQuestions().filter { it.first.type != EventType.PROMPT }.asReversed(), key = { it.first.id }) { (e, question) ->
                        EventRow(e, question = question)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = onNote,
                    placeholder = { Text(stringResource(R.string.log_note_hint)) },
                    // SEC-05: the keyboard should not learn or suggest from what is noted about a subject.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onAddNote(note); onNote("") },
                    enabled = note.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Text(stringResource(R.string.log_add)) }
            }
        }
    }
}

/**
 * Pairs each answer with its question, so the log reads "Water? Yes" rather than a bare "Yes": by the shown item's id
 * when the answer names it, otherwise the question shown before it.
 */
fun List<SessionEvent>.withQuestions(): List<Pair<SessionEvent, String?>> {
    // Answers from a second screen name their question, since they can arrive after the next one was shown (D-26).
    val shown = filter { it.type == EventType.QUESTION_SHOWN }.associate { it.id to it.text }
    var question: String? = null
    return map { e ->
        when (e.type) {
            EventType.QUESTION_SHOWN -> question = e.text
            EventType.SYSTEM -> question = null
            else -> Unit
        }
        e to if (e.type in AnswerEvents) (if (e.shownId != null) shown[e.shownId] else question) else null
    }
}

val AnswerEvents = setOf(EventType.ANSWER_CARD, EventType.ANSWER_DRAWING, EventType.ANSWER_GESTURE, EventType.ANSWER_UNCLEAR, EventType.LANGUAGE_CHOSEN, EventType.ANSWER_TEXT, EventType.ANSWER_SIGNED)

/** A signed answer is always marked as the app's reading or the officer's, never as the subject's own words. */
fun signedMark(e: SessionEvent) = if (e.source == EventSource.OFFICER) R.string.signed_officer else R.string.signed_app

/** Words of a signed answer for display; null when no sign has been read yet. The record keeps its "?" marks. */
fun signedWords(text: String?): String? =
    text?.split(' ')?.takeUnless { w -> w.all { it == "?" } }?.joinToString(" ") { if (it == "?") "…" else it }

/** How a signed answer reads on screen and in the export: its words, or how many signs wait to be read. */
fun signedDisplay(res: Resources, text: String?): String =
    signedWords(text) ?: (text?.split(' ')?.size ?: 0).let { res.getQuantityString(R.plurals.signed_unread, it, it) }

@Composable
fun EventRow(e: SessionEvent, modifier: Modifier = Modifier, question: String? = null) {
    ListItem(
        overlineContent = {
            val mark = when {
                e.type == EventType.ANSWER_SIGNED -> "  ·  ${stringResource(signedMark(e))}"
                e.reason == SHOWN_RIGHTS -> "  ·  ${stringResource(R.string.rights_shown_mark)}"
                e.via != null -> "  ·  ${stringResource(R.string.report_via_stage)}"
                else -> ""
            }
            Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.ts))}  ·  ${stringResource(e.type.label())}$mark")
        },
        headlineContent = {
            val res = LocalResources.current
            Text(
                when (e.type) {
                    EventType.SYSTEM -> systemText(res, e.text)
                    EventType.ANSWER_SIGNED -> signedDisplay(res, e.text)
                    else -> app.signsetu.ui.pair.linkText(e) ?: e.text ?: "—"
                },
            )
        },
        supportingContent = question?.takeIf { it != e.text }?.let { q -> { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = e.cardId?.let { id ->
            { Surface(shape = RoundedCornerShape(12.dp), color = PictureWell, modifier = Modifier.size(40.dp)) { CardImage(id, Modifier.padding(4.dp)) } }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeSheet(
    content: ContentBundle,
    profile: Profile,
    subjectLang: String,
    role: SessionRole,
    juvenile: Boolean,
    onProfile: (Profile) -> Unit,
    onLanguage: (String) -> Unit,
    onRole: (SessionRole) -> Unit,
    onJuvenile: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OfficerSheet(onDismiss, expanded = false) {
        Column(
            Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle(stringResource(R.string.officer_change))
            RoleChoice(role, onRole, Modifier.padding(bottom = Spacing.sm))
            if (role == SessionRole.ACCUSED) JuvenileNotice(juvenile, enabled = profile != Profile.YOUNG_CHILD, onJuvenile)
            Column(Modifier.selectableGroup()) {
                Profile.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = p == profile, onClick = { onProfile(p) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == profile, onClick = null)
                        Text(stringResource(profileTitle(p)), modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
            SectionLabel(stringResource(R.string.start_language))
            LanguagePicker(content, subjectLang, onPick = onLanguage)
        }
    }
}

/** Every officer sheet: overlay touches filtered (SEC-45), and Material's 32 dp drag handle in a 48 dp touch area (NFR-20). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerSheet(onDismiss: () -> Unit, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
        dragHandle = { Box(Modifier.sizeIn(minWidth = 48.dp), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() } },
        content = {
            FilterObscuredTouches()
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = Spacing.sm)) { Text(stringResource(R.string.back)) }
            content()
        },
    )
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = Spacing.lg).semantics { heading() },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

fun BodyLocation.label() = when (this) {
    BodyLocation.HEAD -> R.string.loc_head
    BodyLocation.FOREHEAD -> R.string.loc_forehead
    BodyLocation.EYES -> R.string.loc_eyes
    BodyLocation.NOSE -> R.string.loc_nose
    BodyLocation.EAR -> R.string.loc_ear
    BodyLocation.FACE -> R.string.loc_face
    BodyLocation.MOUTH -> R.string.loc_mouth
    BodyLocation.CHIN -> R.string.loc_chin
    BodyLocation.CHEST -> R.string.loc_chest
    BodyLocation.BELLY -> R.string.loc_belly
    BodyLocation.ARM -> R.string.loc_arm
    BodyLocation.HANDS -> R.string.loc_hands
    BodyLocation.AWAY -> R.string.loc_away
}

fun HandAction.label() = when (this) {
    HandAction.TOUCH -> R.string.act_touch
    HandAction.STROKE -> R.string.act_stroke
    HandAction.POINT -> R.string.act_point
    HandAction.CIRCLE -> R.string.act_circle
    HandAction.FLAT_HAND -> R.string.act_flat
    HandAction.FIST -> R.string.act_fist
    HandAction.BOTH_HANDS -> R.string.act_both
    HandAction.MOVE -> R.string.act_move
    HandAction.HEAD_MOVE -> R.string.act_head
}

fun EventType.label() = when (this) {
    EventType.QUESTION_SHOWN -> R.string.log_shown
    EventType.ANSWER_CARD -> R.string.log_answer
    EventType.ANSWER_DRAWING -> R.string.log_drawing
    EventType.ANSWER_GESTURE -> R.string.log_gesture
    EventType.ANSWER_UNCLEAR -> R.string.log_unclear
    EventType.LANGUAGE_CHOSEN -> R.string.log_language
    EventType.NOTE -> R.string.log_note
    EventType.SYSTEM -> R.string.log_system
    EventType.ANSWER_TEXT -> R.string.log_typed
    EventType.ANSWER_SIGNED -> R.string.log_signed
    EventType.LINK -> R.string.log_link
    EventType.SUGGESTED -> R.string.log_suggested
    EventType.APPROVED -> R.string.log_approved
    EventType.SKIPPED -> R.string.log_skipped
    EventType.AUTO_ADVANCED -> R.string.log_auto_advanced
    EventType.PROMPT -> R.string.log_prompt
}

/**
 * Replays a signed answer slowly, sign by sign, so the officer can accept a guess or type the word for each sign.
 * Without a sign reader every sign starts blank: the officer transcribes by watching.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignedSheet(
    segments: List<SignSegment>,
    onAccept: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onAsk: ((List<String>) -> Unit)? = null,
    canAsk: (List<String>) -> Boolean = { true },
) {
    val replay = rememberReplayState(segments)
    val words = remember(segments) { segments.map { s -> s.word?.takeIf { it != "?" }.orEmpty() }.toMutableStateList() }
    val colors = MaterialTheme.colorScheme
    OfficerSheet(onDismiss) {
        LazyColumn(
            Modifier.fillMaxHeight(0.92f).imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SheetTitle(stringResource(R.string.log_signed)) }
            item { SkeletonPlayer(replay, Modifier.fillMaxWidth()) }
            if (segments.all { it.guesses.isNullOrEmpty() }) {
                item { Text(stringResource(R.string.signed_manual), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            itemsIndexed(segments) { i, s ->
                val current = replay.segment == i
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (current) colors.surfaceContainerHigh else colors.surfaceContainerLow,
                    border = if (current) BorderStroke(2.dp, colors.primary) else null,
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val name = stringResource(R.string.signed_sign, i + 1)
                            TextButton(onClick = { replay.play(i) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(name, modifier = Modifier.padding(start = Spacing.xs))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            s.guesses.orEmpty().forEach { g ->
                                FilterChip(selected = words[i] == g.word, onClick = { words[i] = g.word }, label = { Text("${g.word}  ${(g.score * 100).roundToInt()}%") })
                            }
                        }
                        OutlinedTextField(
                            value = words[i],
                            onValueChange = { words[i] = it },
                            placeholder = { Text(stringResource(R.string.signed_type)) },
                            singleLine = true,
                            // SEC-05: the keyboard should not learn from what is transcribed about a subject.
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                BrandButton(
                    onClick = { onAccept(words.map { it.trim().ifEmpty { "?" } }) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.signed_accept)) }
            }
            // Only the subject's own pick makes a reading their answer (FR-66).
            onAsk?.let { ask ->
                item {
                    val possible = canAsk(words.toList())
                    OutlinedButton(onClick = { ask(words.toList()) }, enabled = possible, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.signed_ask)) }
                    Text(stringResource(if (possible) R.string.signed_ask_hint else R.string.signed_ask_none), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}

/** The character's pose at the gesture's key moment, with the picture it stands for. */
@Composable
private fun GesturePreview(content: ContentBundle, entry: LexiconEntry) {
    val gesture = content.gestures[entry.id]
    Box(Modifier.size(72.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = LocalStageColors.current.skyTop, modifier = Modifier.fillMaxSize()) {
            if (gesture != null) {
                val keys = gesture.keys.sortedBy { it.t }
                Avatar(Character.CHILD, gesture, entry.id, Modifier.fillMaxSize(), idle = false, atSeconds = Timeline.LEAD_IN + keys[keys.size / 2].t)
            } else {
                CardImage(entry.card, Modifier.padding(10.dp))
            }
        }
        if (gesture != null) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 2.dp, modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)) {
                CardImage(entry.card, Modifier.padding(4.dp))
            }
        }
    }
}
