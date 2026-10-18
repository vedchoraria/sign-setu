package app.signsetu.ui.session

import androidx.compose.material.icons.filled.Star
import app.signsetu.camera.SkeletonOnly
import app.signsetu.assistant.Suggestion
import app.signsetu.assistant.Assistant
import android.app.Activity
import android.content.res.Resources
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.minimumInteractiveComponentSize
import app.signsetu.ui.components.OfficerCard
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.forSignLanguage
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportText
import app.signsetu.ui.avatar.MiniFace
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.FilterObscuredTouches
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.start.roleTitle
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.SunMode
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material.icons.filled.PlayArrow
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow
import java.text.DateFormat
import java.util.Date
import androidx.compose.material.icons.filled.Phone
import app.signsetu.pair.PairedPane
import app.signsetu.ui.pair.PairedBar
import app.signsetu.ui.pair.linkText
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import app.signsetu.data.content.PhraseSlot

private enum class Sheet { BOARD_SHOW, BOARD_ATTACH, GESTURE, LOG, CHANGE, DICTIONARY }

/** The phrase bank sits beside the triage groups as one more group (FR-62). */
private const val PHRASES = "phrases"
private const val WORD_SEP = "\u0001"
internal val DraftsSaver = listSaver<MutableMap<String, String>, String>(save = { m -> m.flatMap { listOf(it.key, it.value) } }, restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { put(it[0], it[1]) } } })

/** Prepared questions an officer needs most often, offered on the main page until each has been asked. */
private val QuickQuestions = listOf("s2", "n1", "n2", "n3", "h2", "f2", "i2", "l1")

/**
 * The officer's side of the phone. The main page is quick two-way transcription, laid out like a messaging app: the
 * conversation, a few prepared questions, a composer that signs typed text, and Read signs for a signed answer (FR-15).
 * Every other way to ask is one tap away on the second page.
 */
@Composable
fun OfficerPane(
    ui: SessionUi,
    vm: SessionViewModel,
    officerLang: String,
    cameraOn: Boolean,
    onCameraOn: (Boolean) -> Unit,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    assistant: Assistant?,
    onCallInterpreter: (() -> Unit)?,
    onShare: (File) -> Unit,
    onEndSession: () -> Unit,
    canReadSigns: Boolean = false,
    /** Set while a second screen shows the stage (D-26). */
    paired: PairedPane? = null,
    onSecondScreen: (() -> Unit)? = null,
    allowAuto: Boolean = false,
    islReorder: Boolean = true,
) {
    val signLanguage = ui.signLanguage
    val content = remember(vm.content, signLanguage) { vm.content.forSignLanguage(signLanguage) }
    val ink = LocalInk.current
    val subjectLang = ui.session.subjectLang
    val subjectLanguage = content.language(subjectLang)
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    var replaying by rememberSaveable { mutableStateOf<Long?>(null) }
    var askSensitive by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    // Sheets and dialogs take Back first; on the page itself it steps to the first tab, then asks before ending.
    BackHandler { if (page != 0) page = 0 else askEnd = true }
    var export by remember { mutableStateOf<ExportState?>(null) }
    var stopSeen by rememberSaveable { mutableStateOf<Long?>(null) }
    var phrase by remember { mutableStateOf<Phrase?>(null) }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val attached = rememberSaveable(saver = listSaver(save = { l -> l.toList() }, restore = { l -> l.toMutableStateList() })) { mutableStateListOf<String>() }
    // Typed words held by event id ("say:", "sign:") and Show picks ("show"), so dismiss and rotation keep them.
    val drafts = rememberSaveable(saver = DraftsSaver) { mutableStateMapOf<String, String>() }
    // Held here so closing a sheet does not drop what was typed or picked.
    var logNote by rememberSaveable { mutableStateOf("") }
    var boardDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var ownText by rememberSaveable { mutableStateOf("") }
    var suggestion by remember { mutableStateOf<Suggestion?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var noHelp by remember { mutableStateOf(false) }
    val modes = rememberSaveable(ui.session.profile, saver = listSaver(save = { l -> l.map { it.name } }, restore = { l -> l.map(SignMode::valueOf).toMutableStateList() })) {
        defaultModes(ui.session.profile).toMutableStateList()
    }

    val answers = ui.events.count { it.type in AnswerEvents }
    // A request to stop stays on screen until the officer acknowledges it or shows something new.
    val lastStop = ui.events.lastOrNull { it.type == EventType.QUESTION_SHOWN || it.cardId in PauseCards }?.takeIf { it.cardId in PauseCards }

    // The ink header needs light status-bar icons; the rest of the app uses dark ones.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { before?.let { controller.isAppearanceLightStatusBars = it } }
    }

    // Main page: question run and say-back.
    var run by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sayingBackId by rememberSaveable { mutableStateOf<Long?>(null) }
    val sayingBack = ui.events.firstOrNull { it.id == sayingBackId }
    val context = LocalContext.current
    val runQuestions = run.mapNotNull { id -> content.questions.firstOrNull { it.id == id } }
    val sensitiveGroups = content.groups.filter { it.sensitive }.map { it.id }
    val addToRun = { q: Question ->
        when {
            q.group in sensitiveGroups -> Toast.makeText(context, R.string.run_no_sensitive, Toast.LENGTH_SHORT).show()
            q.id in run -> Unit
            run.size >= RUN_MAX -> Toast.makeText(context, R.string.run_full, Toast.LENGTH_SHORT).show()
            else -> run = run + q.id
        }
    }

    val prepareTyped = {
        if (ownText.isNotBlank()) {
            vm.prepare(Pending.FreeText(ownText.trim(), attached.toList(), typedModes(modes, islReorder), suggestion?.caption))
            ownText = ""
            attached.clear()
            suggestion = null
        }
    }
    val send = {
        if (run.isNotEmpty() && ownText.isBlank()) {
            vm.showRun(runQuestions.map { Pending.Ask(it) })
            run = emptyList()
        } else {
            prepareTyped(); if (paired?.allowShow?.invoke() != false) vm.show()
        }
    }
    val read = { prepareTyped(); vm.readSigns() }
    val onTool = { tool: Tool ->
        when (tool) {
            Tool.GESTURE -> sheet = Sheet.GESTURE
            Tool.BOARD -> sheet = Sheet.BOARD_SHOW
            Tool.ATTACH -> sheet = Sheet.BOARD_ATTACH
            Tool.DRAW -> { vm.prepare(Pending.Drawing); page = 0 }
            Tool.LANGUAGE -> { vm.prepare(Pending.LanguageFinder); page = 0 }
            Tool.RULES -> { vm.prepare(Pending.GroundRules()); page = 0 }
            Tool.DICTIONARY -> sheet = Sheet.DICTIONARY // ISL hook: IslDictionarySheet.kt
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().imePadding()) {
        Surface(color = Color.Transparent, contentColor = ink.onBand, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), shadowElevation = 4.dp) {
            Row(
                Modifier.background(ink.brush).statusBarsPadding().fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = stringResource(R.string.officer_change), role = Role.Button) { sheet = Sheet.CHANGE }
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniFace(characterFor(ui.session.profile), 40.dp, Color(0xFFFFDDBE))
                    // Who the person is at a glance: profile, then language and role.
                    Column(Modifier.padding(start = Spacing.md)) {
                        Text(stringResource(profileTitle(ui.session.profile)), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                if (signLanguage == SignLanguage.ASL) "ASL (${stringResource(R.string.asl_experimental_badge)})" else null,
                                subjectLanguage?.let { "${it.name} · ${it.english}" } ?: subjectLang,
                                stringResource(roleTitle(ui.session.role)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = ink.onBandMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                onCallInterpreter?.let { call ->
                    IconButton(onClick = call) { Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.officer_call)) }
                }
                IconButton(onClick = { sheet = Sheet.LOG }) {
                    BadgedBox(badge = { if (answers > 0) Badge { Text("$answers") } }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.officer_log))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.officer_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        FilterObscuredTouches()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_change)) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            onClick = { menu = false; sheet = Sheet.CHANGE },
                        )
                        // Fixed while something is on a second screen, like the profile and language.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_sign_language)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), contentDescription = null) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(signLanguage.name)
                                    if (signLanguage == SignLanguage.ASL) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ) {
                                            Text(
                                                stringResource(R.string.asl_experimental_badge),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = paired == null || ui.onStage == null,
                            onClick = {
                                val langs = SignLanguage.entries.filter { it == SignLanguage.ISL || !vm.content.aslDisabled }
                                vm.setSignLanguage(langs[(langs.indexOf(signLanguage) + 1) % langs.size])
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_rotate)) },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            trailingIcon = { Checkbox(checked = ui.rotated, onCheckedChange = null) },
                            onClick = { vm.toggleRotation() },
                            modifier = Modifier.toggleState(ui.rotated, Role.Checkbox),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_sun)) },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            trailingIcon = { Switch(checked = SunMode.forced.value, onCheckedChange = null) },
                            onClick = { SunMode.forced.value = !SunMode.forced.value },
                            modifier = Modifier.toggleState(SunMode.forced.value, Role.Switch),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_skeleton_only)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), contentDescription = null) },
                            trailingIcon = { Switch(checked = SkeletonOnly.on.value, onCheckedChange = null) },
                            onClick = { SkeletonOnly.on.value = !SkeletonOnly.on.value },
                            modifier = Modifier.toggleState(SkeletonOnly.on.value, Role.Switch),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_camera)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_videocam), contentDescription = null) },
                            trailingIcon = { Switch(checked = cameraOn, onCheckedChange = null) },
                            onClick = { onCameraOn(!cameraOn) },
                            modifier = Modifier.toggleState(cameraOn, Role.Switch),
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.officer_end), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; askEnd = true },
                        )
                    }
                }
            }
        }

        paired?.let { PairedBar(it, ui, content, officerLang) }
        if (signLanguage == SignLanguage.ASL) AslExperimentalBanner()

        PrimaryTabRow(selectedTabIndex = page, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = page == 0, onClick = { page = 0 }, text = { Text(stringResource(R.string.officer_tab_transcribe), style = MaterialTheme.typography.titleSmall) })
            Tab(selected = page == 1, onClick = { page = 1 }, text = { Text(stringResource(R.string.officer_tab_more), style = MaterialTheme.typography.titleSmall) })
        }

        if (page == 1) {
            MorePage(
                ui = ui,
                content = content,
                officerLang = officerLang,
                answers = answers,
                onGroup = { g -> if (g.sensitive && !ui.sensitiveUnlocked) askSensitive = true else vm.selectGroup(g.id) },
                onPhrases = { vm.selectGroup(PHRASES) },
                onQuestion = { q ->
                    val again = (ui.pending as? Pending.Ask)?.question?.id == q.id
                    vm.prepare(if (again) null else Pending.Ask(q))
                    if (!again) page = 0
                },
                onPhrase = { phrase = it },
                onAddToRun = addToRun,
                onTool = onTool,
                onLog = { sheet = Sheet.LOG },
                onChange = { sheet = Sheet.CHANGE },
                onSecondScreen = onSecondScreen,
                onTell = if (canReadSigns) ({ prepareTyped(); vm.readSigns(SignWindow.NARRATIVE); page = 0 }) else null,
                modifier = Modifier.weight(1f),
            )
        } else {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Transcript(ui.events, ui.session.profile, ui.signed, onReplay = { replaying = it }, Modifier.fillMaxSize(), onSayBack = { sayingBackId = it.id })
            // The scope-free overload: inside this Box the Column's AnimatedVisibility does not apply.
            androidx.compose.animation.AnimatedVisibility(
                lastStop != null && lastStop.id != stopSeen && paired == null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = Spacing.lg),
            ) {
                StopBanner(onOk = { stopSeen = lastStop?.id })
            }
        }

        if (allowAuto && ownText.isEmpty()) AutoPanel(vm.auto, ui, content, officerLang)
        Dock(
            ui = ui,
            content = content,
            officerLang = officerLang,
            text = ownText,
            onText = { ownText = it; suggestion = null; noHelp = false },
            assistant = assistant != null,
            canRead = canReadSigns,
            thinking = thinking,
            suggestion = suggestion,
            noHelp = noHelp,
            onAssist = {
                val language = content.language(subjectLang)
                if (assistant != null && language != null && !thinking) {
                    thinking = true
                    scope.launch {
                        suggestion = assistant.suggest(ownText.trim(), language)
                        noHelp = suggestion == null
                        thinking = false
                    }
                }
            },
            onDiscard = { suggestion = null },
            modes = modes,
            onMode = { if (!modes.remove(it)) modes.add(it) },
            attached = attached,
            onPictures = { onTool(if (ownText.isNotEmpty()) Tool.ATTACH else Tool.BOARD) },
            onQuestion = { q -> vm.prepare(if ((ui.pending as? Pending.Ask)?.question?.id == q.id) null else Pending.Ask(q)) },
            onClear = { vm.prepare(null) },
            onSend = send,
            onRead = read,
            run = runQuestions,
            onRunRemove = { q -> run = run - q.id },
            onAddToRun = addToRun,
            onRunBasics = {
                // The opening question has been shown already; the next three safety questions follow it.
                content.questions.filter { it.group == "safety" && it.id != "s1" }.take(3).forEach(addToRun)
            },
            onTemplate = { p, s -> vm.prepare(Pending.PhraseAsk(p, s)); ownText = "" },
        )
        }
    } }

    phrase?.let { p ->
        PhraseSheet(p, content, officerLang, onPick = { slot -> vm.prepare(Pending.PhraseAsk(p, slot)); phrase = null; page = 0 }, onDismiss = { phrase = null })
    }
    sayingBack?.let { e ->
        val key = "say:${e.id}"
        SayBackDialog(
            e, drafts[key] ?: e.text.orEmpty(), onDraft = { drafts[key] = it },
            onSend = { words -> vm.sayBack(e, words, typedModes(modes, islReorder)); drafts.remove(key); sayingBackId = null },
            onDismiss = { sayingBackId = null },
        )
    }
    replaying?.let { id ->
        ui.signed[id]?.let { segments ->
            SignedSheet(
                segments,
                onAccept = { vm.acceptSigned(id, it); drafts.remove("sign:$id"); replaying = null },
                onDismiss = { replaying = null },
                onAsk = { vm.askSubject(id, it); replaying = null },
                draft = drafts["sign:$id"]?.split(WORD_SEP), onDraft = { drafts["sign:$id"] = it.joinToString(WORD_SEP) },
                canAsk = { vm.canAskSubject(id, it) },
            )
        }
    }
    when (sheet) {
        Sheet.BOARD_SHOW -> BoardSheet(
            content, officerLang, drafts["show"]?.split(",")?.filter { it.isNotEmpty() }.orEmpty(), favourites, onFavourite,
            onUse = { vm.prepare(Pending.Cards(it)); drafts.remove("show"); sheet = null; page = 0 },
            onDismiss = { sheet = null }, onDraft = { drafts["show"] = it.joinToString(",") },
        )
        Sheet.BOARD_ATTACH -> BoardSheet(
            content, officerLang, boardDraft?.split(",")?.filter { it.isNotEmpty() } ?: attached.toList(), favourites, onFavourite,
            onUse = { attached.clear(); attached.addAll(it); boardDraft = null; sheet = null },
            onDismiss = { sheet = null }, onDraft = { boardDraft = it.joinToString(",") },
        )
        Sheet.GESTURE -> GestureSheet(content, officerLang, onPick = { entry, found -> vm.prepare(Pending.ShowBack(entry, checkChoices(entry.card, found.map { it.card }))); sheet = null; page = 0 }, onDismiss = { sheet = null })
        Sheet.LOG -> LogSheet(ui.events, logNote, { logNote = it }, onAddNote = vm::addNote, onDismiss = { sheet = null })
        // ISL hook: the dictionary's Show prepares the sign like a question.
        Sheet.DICTIONARY -> IslDictionarySheet(content, officerLang, signLanguage, onShow = { vm.prepare(Pending.Ask(it)); sheet = null; page = 0 }, onDismiss = { sheet = null })
        // Profile and language stay fixed while an item is on a second screen.
        Sheet.CHANGE -> if (paired == null || ui.onStage == null) ChangeSheet(
            content, ui.session.profile, subjectLang, ui.session.role, ui.juvenile,
            onProfile = vm::setProfile, onLanguage = vm::setSubjectLang, onRole = vm::setRole, onJuvenile = vm::setJuvenile, onDismiss = { sheet = null },
        )
        null -> Unit
    }

    if (askSensitive) {
        GuardedAlertDialog(
            onDismissRequest = { askSensitive = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.sensitive_title)) },
            text = { Text(stringResource(R.string.sensitive_body)) },
            confirmButton = {
                TextButton(onClick = { askSensitive = false; vm.unlockSensitive(); vm.selectGroup("sensitive") }) {
                    Text(stringResource(R.string.sensitive_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { askSensitive = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (askEnd) {
        GuardedAlertDialog(
            onDismissRequest = { askEnd = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.end_title)) },
            text = { Text(stringResource(R.string.end_body)) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        askEnd = false
                        export = ExportState.Working
                        scope.launch {
                            val by = shownBy(ui.events)
                            val idle = notActedOn(ui.events)
                            // Lesson-sheet hook: the report's personal details section (PersonalDetails.kt).
                            val section = detailsSection(res, content, officerLang, ui.events)
                            export = runCatching { vm.exportReport(reportText(res, ui, content), recordDetail(res, content, officerLang, ui.events) { eventDetail(res, it, by, idle) }, section) }
                                .fold({ (file, pass) -> ExportState.Ready(file, pass) }, { ExportState.Failed })
                        }
                    }) { Text(stringResource(R.string.end_export)) }
                    OutlinedButton(onClick = { askEnd = false; onEndSession() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.end_no_export), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { askEnd = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    export?.let { state ->
        ExportDialog(
            state = state,
            onShare = { (state as? ExportState.Ready)?.let { onShare(it.file) } },
            onFinish = { (state as? ExportState.Ready)?.file?.delete(); export = null; onEndSession() },
            onDismiss = { export = null },
        )
    }
}

/** The conversation so far: what was shown on the right, the subject's answers on the left, newest at the bottom. */
@Composable
private fun Transcript(events: List<SessionEvent>, profile: Profile, signed: Map<Long, List<SignSegment>>, onReplay: (Long) -> Unit, modifier: Modifier, onSayBack: (SessionEvent) -> Unit = {}) {
    if (events.isEmpty()) {
        Column(modifier.padding(Spacing.xxl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            MiniFace(characterFor(profile), 72.dp, Color(0xFFFFDDBE))
            Text(
                stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                stringResource(R.string.chat_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        return
    }
    // Suggestions and their outcomes belong in the log, not the conversation.
    val newestFirst = remember(events) { events.filter { it.type != EventType.PROMPT && it.type !in AutoRows }.asReversed() }
    val by = remember(events) { shownBy(events) }
    val grades = remember(events) { grades(events) }
    val trails = remember(events) { answerTrails(events) }
    val positions = remember(events) { choicePositions(events) }
    LazyColumn(
        modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(newestFirst, key = { it.id }) { e ->
            Box(Modifier.animateItem()) {
                when (e.type) {
                    EventType.QUESTION_SHOWN -> Shown(e, by[e.id])
                    EventType.NOTE -> Aside(e.text.orEmpty(), note = true)
                    EventType.SYSTEM -> Aside(systemText(e.text), note = false)
                    EventType.LINK -> Aside(linkText(e).orEmpty(), note = false)
                    else -> Answered(
                        e, profile, onReplay = if (e.id in signed) ({ onReplay(e.id) }) else null,
                        grade = grades[e.id], trail = trails[e.id], position = positions[e.id],
                        onSayBack = if (canSayBack(e, grades[e.id])) ({ onSayBack(e) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun systemText(text: String?) = systemText(LocalResources.current, text)

private val AutoRows = setOf(EventType.SUGGESTED, EventType.APPROVED, EventType.SKIPPED, EventType.AUTO_ADVANCED)

private fun timeOf(e: SessionEvent) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.ts))

@Composable
private fun Shown(e: SessionEvent, by: ShownBy? = null) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(ink.brush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(e.text ?: "—", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        val tags = listOfNotNull(
            by?.tag?.let { stringResource(it) },
            if (e.reason == SHOWN_RIGHTS) stringResource(R.string.rights_shown_mark) else null,
        ).joinToString("") { " · $it" }
        Text(
            "${stringResource(e.type.label())} · ${timeOf(e)}$tags",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
        )
    }
}

@Composable
private fun Answered(
    e: SessionEvent,
    profile: Profile,
    onReplay: (() -> Unit)? = null,
    grade: Grade? = null,
    trail: Trail? = null,
    position: Pair<Int, Int>? = null,
    onSayBack: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val answer = LocalAnswerColors.current
    val well = when {
        e.cardId == "yes" -> answer.yesContainer
        e.cardId == "no" -> answer.noContainer
        e.type == EventType.ANSWER_UNCLEAR -> colors.tertiaryContainer
        else -> PictureWell
    }
    Row(Modifier.fillMaxWidth().padding(end = 48.dp), verticalAlignment = Alignment.Bottom) {
        MiniFace(characterFor(profile), 28.dp, Color(0xFFFFDDBE))
        Column(Modifier.padding(start = Spacing.sm)) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp),
                color = colors.surfaceContainerLowest,
                border = trailBorder(trail) ?: BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Row(Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = well, modifier = Modifier.size(40.dp)) {
                        when {
                            e.cardId != null -> CardImage(e.cardId, Modifier.padding(5.dp))
                            e.drawingId != null -> Icon(Icons.Filled.Create, contentDescription = null, modifier = Modifier.padding(10.dp))
                            e.type == EventType.ANSWER_TEXT -> Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(10.dp))
                            else -> Icon(painterResource(R.drawable.ic_hand), contentDescription = null, modifier = Modifier.padding(10.dp))
                        }
                    }
                    val signed = e.type == EventType.ANSWER_SIGNED
                    Text(
                        if (signed) signedDisplay(LocalResources.current, e.text) else e.text ?: stringResource(e.type.label()),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (signed && signedWords(e.text) == null) colors.onSurfaceVariant else colors.onSurface,
                        modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false),
                    )
                    onReplay?.let { play ->
                        IconButton(onClick = play, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.signed_replay), tint = colors.primary)
                        }
                    }
                }
            }
            val mark = if (e.type == EventType.ANSWER_SIGNED) " · ${stringResource(signedMark(e))}" else ""
            Text(
                "${stringResource(e.type.label())}$mark · ${timeOf(e)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp),
            )
            AnswerMeta(grade, trail, position, onSayBack, cueText(LocalResources.current, e))
        }
    }
}

/** Notes and system steps sit between the two sides, like dates in a chat. */
@Composable
private fun Aside(text: String, note: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (note) colors.tertiaryContainer else colors.surfaceContainerHigh,
            contentColor = if (note) colors.onTertiaryContainer else colors.onSurfaceVariant,
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

private enum class Tool { GESTURE, BOARD, DRAW, LANGUAGE, ATTACH, RULES, DICTIONARY }

/** Quick questions and the composer: everything the officer reaches with a thumb. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dock(
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
    val quick = QuickQuestions.mapNotNull { id -> content.questions.firstOrNull { it.id == id } }
        .filter { q -> q.text.pick(officerLang) !in asked || (pending as? Pending.Ask)?.question?.id == q.id }
    Surface(color = colors.surfaceContainerLowest, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(top = Spacing.md)) {
            AnimatedVisibility(!typing && quick.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = Spacing.lg), modifier = Modifier.padding(bottom = Spacing.sm)) {
                    if (run.isEmpty()) item(key = "run_basics") { CategoryChip(stringResource(R.string.run_basics), false, locked = false, onClick = onRunBasics) }
                    items(quick, key = { it.id }) { q ->
                        CategoryChip(q.text.pick(officerLang) ?: q.id, (pending as? Pending.Ask)?.question?.id == q.id, locked = false, onLongClick = { onAddToRun(q) }, longClickLabel = addLabel) { onQuestion(q) }
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
                AssistantCard(suggestion, content.language(subjectLang)?.english ?: subjectLang, onDiscard)
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

/** Every other way to ask, and the session's record: one tap from the main page. Picking something returns there. */
@Composable
private fun MorePage(
    ui: SessionUi,
    content: ContentBundle,
    officerLang: String,
    answers: Int,
    onGroup: (app.signsetu.data.content.Group) -> Unit,
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

/** What Send will show, with what the subject will get (FR-15, FR-17). */
@Composable
private fun Ready(pending: Pending, content: ContentBundle, officerLang: String, subjectLang: String, profile: Profile, onClear: () -> Unit) {
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
                // SEC-05: the keyboard should not learn or suggest from what is typed about a subject.
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
private fun CategoryChip(label: String, selected: Boolean, locked: Boolean, onLongClick: (() -> Unit)? = null, longClickLabel: String? = null, onClick: () -> Unit) {
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

/** The assistant's suggestion, shown before Send; a machine translation is always marked as unchecked (D-24). */
@Composable
private fun AssistantCard(suggestion: Suggestion, languageName: String, onDiscard: () -> Unit) {
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
                Text(suggestion.caption, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.ai_meaning), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Text(suggestion.meaning, style = MaterialTheme.typography.bodyMedium)
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

/** A prepared question or phrase: its picture, its words, and what the subject will get (FR-17). */
@Composable
private fun Suggestion(
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

@Composable
private fun StopBanner(onOk: () -> Unit) {
    val answer = LocalAnswerColors.current
    Surface(shape = MaterialTheme.shapes.large, color = answer.pause, contentColor = answer.onPause, shadowElevation = 4.dp, modifier = Modifier.padding(top = Spacing.md)) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            CardImage("stop", Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(stringResource(R.string.officer_stop_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.officer_stop_body), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOk, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ok), color = answer.onPause) }
        }
    }
}

/** What the subject will get for this question; anything missing is named before the officer shows it (FR-17). */
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

private fun typedModes(modes: List<SignMode>, islReorder: Boolean) = if (islReorder) modes.toSet() else modes.toSet() + SignMode.TYPED_ORDER

@Composable
internal fun describe(p: Pending, content: ContentBundle, lang: String): String = when (p) {
    is Pending.Ask -> p.question.text.pick(lang) ?: p.question.id
    is Pending.FreeText -> if (p.cardIds.isEmpty()) p.text else "${p.text}  ·  ${pluralStringResource(R.plurals.officer_pictures, p.cardIds.size, p.cardIds.size)}"
    is Pending.Cards -> p.cardIds.joinToString { content.cards[it]?.label?.pick(lang) ?: it }
    is Pending.ShowBack -> "${stringResource(R.string.officer_gesture)}: ${p.entry.label.pick(lang) ?: p.entry.id}"
    is Pending.PhraseAsk -> p.slot.text.pick(lang) ?: p.slot.card
    Pending.LanguageFinder -> stringResource(R.string.officer_find_language)
    Pending.Drawing -> stringResource(R.string.officer_draw)
    is Pending.GroundRules -> stringResource(R.string.officer_rules)
}

/** Profiles that sign get ISL signs and spelling; everyone else gets everyday gestures. Both get pictures. */
/** A menu row that toggles: TalkBack hears its state, as a Checkbox or Switch with its own handler would give. */
private fun Modifier.toggleState(on: Boolean, kind: Role) = semantics {
    role = kind
    toggleableState = ToggleableState(on)
}

private fun defaultModes(profile: Profile) =
    if (profile == Profile.ISL_USER) listOf(SignMode.SIGNS, SignMode.SPELL, SignMode.PICTURES) else listOf(SignMode.GESTURES, SignMode.PICTURES)

private val ModeLabels = listOf(
    SignMode.SIGNS to R.string.mode_signs,
    SignMode.GESTURES to R.string.mode_gestures,
    SignMode.SPELL to R.string.mode_spell,
    SignMode.PICTURES to R.string.mode_pictures,
)

private sealed interface ExportState {
    data object Working : ExportState
    data class Ready(val file: File, val passphrase: String) : ExportState
    data object Failed : ExportState
}

/** How the subject answered, by kind, for the report header: what a reader of the record should weigh first. */
internal fun answerSummary(res: Resources, events: List<SessionEvent>): Pair<String, String>? {
    val subject = events.filter { it.source == EventSource.SUBJECT }
    fun n(vararg t: EventType) = subject.count { it.type in t }
    val parts = listOfNotNull(
        n(EventType.ANSWER_CARD).takeIf { it > 0 }?.let { taps ->
            val cued = subject.count { it.type == EventType.ANSWER_CARD && it.reason?.startsWith("cue:") == true }
            if (cued > 0) res.getString(R.string.summary_taps_cued, taps, cued) else res.getString(R.string.summary_taps, taps)
        },
        // A signed answer is logged as the app's reading of it, one per sign window.
        events.count { it.type == EventType.ANSWER_SIGNED && it.source == EventSource.MACHINE }.takeIf { it > 0 }?.let { res.getString(R.string.summary_signed, it) },
        n(EventType.ANSWER_DRAWING).takeIf { it > 0 }?.let { res.getString(R.string.summary_drawn, it) },
        n(EventType.ANSWER_TEXT).takeIf { it > 0 }?.let { res.getString(R.string.summary_written, it) },
        n(EventType.ANSWER_GESTURE).takeIf { it > 0 }?.let { res.getString(R.string.summary_gestured, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.let { res.getString(R.string.report_answers) to it.joinToString(", ") }
}

internal fun reportText(res: Resources, ui: SessionUi, content: ContentBundle): ReportText {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val language = content.language(ui.session.subjectLang)
    return ReportText(
        title = res.getString(R.string.report_title),
        disclaimer = res.getString(R.string.report_disclaimer) + "\n" + res.getString(R.string.report_grade_legend),
        header = listOfNotNull(
            res.getString(R.string.report_started) to time.format(Date(ui.session.startedAt)),
            res.getString(R.string.report_exported) to time.format(Date()),
            // A role the officer never set is left out rather than guessed.
            (res.getString(R.string.report_role) to res.getString(roleTitle(ui.session.role))).takeIf { ui.session.role != SessionRole.NOT_KNOWN },
            res.getString(R.string.report_profile) to res.getString(profileTitle(ui.session.profile)),
            res.getString(R.string.report_language) to (language?.let { "${it.english} (${it.name})" } ?: ui.session.subjectLang),
        ) + ui.events.filter { it.via != null }.let { remote ->
            val clock = DateFormat.getTimeInstance(DateFormat.SHORT)
            if (remote.isEmpty()) emptyList() else listOf(res.getString(R.string.report_second_screen) to res.getString(R.string.report_via_header, clock.format(Date(remote.first().ts)), clock.format(Date(remote.last().ts))))
        } + autoHeader(res, ui.events) + listOfNotNull(answerSummary(res, ui.events)),
        time = res.getString(R.string.report_time),
        event = res.getString(R.string.report_event),
        detail = res.getString(R.string.report_detail),
        inReplyTo = res.getString(R.string.report_in_reply),
        page = { n, total -> res.getString(R.string.report_page, n, total) },
        eventLabel = { res.getString(it.label()) },
        formatTime = { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
        notStatement = res.getString(R.string.report_not_statement),
        readings = res.getString(R.string.report_readings),
    )
}

internal fun eventDetail(res: Resources, e: SessionEvent, by: Map<Long, ShownBy> = emptyMap(), idle: Set<Long> = emptySet()): String = when {
    e.type == EventType.SYSTEM -> systemText(res, e.text)
    e.type == EventType.ANSWER_SIGNED -> "${signedDisplay(res, e.text)} (${res.getString(signedMark(e))})"
    e.drawingId != null -> res.getString(R.string.report_drawing)
    else -> (linkText(res, e) ?: e.text.orEmpty()) + autoSuffix(res, e, by, idle)
}.let { if (e.via != null) "$it (${res.getString(R.string.report_via_stage)})" else it }

@Composable
private fun ExportDialog(state: ExportState, onShare: () -> Unit, onFinish: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Working -> GuardedAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.export_working))
                }
            },
            confirmButton = {},
        )
        is ExportState.Ready -> GuardedAlertDialog(
            // The password is shown once and the file is deleted on Finish, so Back must not close it.
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.export_body))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.export_password),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                state.passphrase,
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp),
                                // Read out character by character, as the officer would.
                                modifier = Modifier.semantics { contentDescription = state.passphrase.toCharArray().joinToString(" ") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = Spacing.sm))
                }
            },
            dismissButton = {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.export_finish), color = MaterialTheme.colorScheme.error) }
            },
        )
        ExportState.Failed -> GuardedAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.export_failed)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun AslExperimentalBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.asl_experimental_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.asl_experimental_advisory),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
