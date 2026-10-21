package app.signsetu.ui.session

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import app.signsetu.assistant.Suggestion
import app.signsetu.assistant.Assistant
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.signsetu.R
import androidx.compose.runtime.mutableIntStateOf
import app.signsetu.ui.components.LocalCloudAi
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.forSignLanguage
import app.signsetu.data.content.pick
import app.signsetu.data.content.toHindi
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.ui.avatar.MiniFace
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.start.roleTitle
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import app.signsetu.sign.SignWindow
import app.signsetu.pair.PairedPane
import app.signsetu.ui.pair.PairedBar
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

private const val THANK_YOU = "e7"

private enum class Sheet { BOARD_SHOW, BOARD_ATTACH, GESTURE, LOG, CHANGE, DICTIONARY }

/** The phrase bank sits beside the triage groups as one more group. */
private const val WORD_SEP = "\u0001"

/**
 * The officer's side of the phone. The main page is quick two-way transcription, laid out like a messaging app: the
 * conversation, a few prepared questions, a composer that signs typed text, and Read signs for a signed answer.
 * Every other way to ask is one tap away on the second page.
 */
@Composable
fun OfficerPane(
    ui: SessionUi,
    vm: SessionViewModel,
    officerLang: String,
    favourites: Set<String>,
    onFavourite: (String) -> Unit,
    assistant: Assistant?,
    onCallInterpreter: (() -> Unit)?,
    onShare: (File) -> Unit,
    /** Saves the session under the given name, or deletes it when null. */
    onEndSession: (String?) -> Unit,
    canReadSigns: Boolean = false,
    /** Set while a second screen shows the stage. */
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
    var page by rememberSaveable { mutableIntStateOf(0) }
    var replaying by rememberSaveable { mutableStateOf<Long?>(null) }
    var askSensitive by remember { mutableStateOf(false) }
    var askEnd by remember { mutableStateOf(false) }
    var saveAs by rememberSaveable { mutableStateOf("") }
    // Sheets and dialogs take Back first; on the page it steps to the first tab, then leaves with the session still
    // open, to resume from the start screen. Finishing is always a deliberate tap, never Back.
    BackHandler(enabled = page != 0) { page = 0 }
    var export by remember { mutableStateOf<ExportState?>(null) }
    val cloud = LocalCloudAi.current
    var aiReport by remember { mutableStateOf<String?>(null) }
    var aiExtracting by remember { mutableStateOf(false) }
    var stopSeen by rememberSaveable { mutableStateOf<Long?>(null) }
    var phrase by remember { mutableStateOf<Phrase?>(null) }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val attached = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<String>() }
    // Typed words held by event id ("say:", "sign:") and Show picks ("show"), so dismiss and rotation keep them.
    val drafts = vm.drafts
    // Held here so closing a sheet does not drop what was typed or picked.
    var logNote by rememberSaveable { mutableStateOf("") }
    var boardDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var ownText by rememberSaveable { mutableStateOf("") }
    var suggestion by remember { mutableStateOf<Suggestion?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var noHelp by remember { mutableStateOf(false) }
    // The instant Hindi caption: previewed while typing, made again from the final text on Send.
    var draft by remember { mutableStateOf<String?>(null) }
    var draftOff by remember { mutableStateOf(false) }
    fun hindi(text: String): String? {
        val t = text.trim()
        val english = subjectLang == "hi" && officerLang != "hi" && t.any { it in 'a'..'z' || it in 'A'..'Z' } && t.none { it in 'ऀ'..'ॿ' }
        return if (english) content.toHindi(t)?.hi else null
    }
    LaunchedEffect(ownText, subjectLang, content) {
        delay(250)
        draft = withContext(Dispatchers.Default) { hindi(ownText) }
    }
    // Kept by the view model: this pane leaves the screen whenever the stage is shown.
    val modes = remember(ui.session.profile) { vm.modesFor(ui.session.profile, defaultModes(ui.session.profile)) }

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
    val runQuestions = remember(run, content) { run.mapNotNull { id -> content.questions.firstOrNull { it.id == id } } }
    val sensitiveGroups = remember(content) { content.groups.filter { it.sensitive }.map { it.id } }
    val addToRun = { q: Question ->
        when {
            q.group in sensitiveGroups -> Toast.makeText(context, R.string.run_no_sensitive, Toast.LENGTH_SHORT).show()
            q.id in run -> Unit
            run.size >= RUN_MAX -> Toast.makeText(context, R.string.run_full, Toast.LENGTH_SHORT).show()
            else -> run = run + q.id
        }
    }

    // The last thing the officer chose is what Send shows: an item picked after typing leaves the text in the box.
    val prepareTyped = {
        if (ownText.isNotBlank() && (ui.pending == null || ui.pending is Pending.FreeText)) {
            val caption = suggestion?.caption ?: if (draftOff) null else hindi(ownText)
            vm.prepare(Pending.FreeText(ownText.trim(), attached.toList(), typedModes(modes, islReorder), caption))
            ownText = ""
            attached.clear()
            suggestion = null
            draftOff = false
        }
    }
    val counsel by vm.counsellor.counsel.collectAsStateWithLifecycle()
    // The AI counsellor writes the officer's side: on a second screen the officer watches here; alone, it runs the stage (Counsellor.kt).
    val startCounsel: () -> Unit = {
        when {
            ui.session.role == app.signsetu.data.session.Role.ACCUSED -> Toast.makeText(context, R.string.ai_not_for_accused, Toast.LENGTH_LONG).show()
            !cloud.available || !cloud.enrolled -> Toast.makeText(context, R.string.ai_needs_online, Toast.LENGTH_LONG).show()
            else -> {
                page = 0
                vm.counsellor.start(
                    counselPrompt(subjectLanguage?.english ?: subjectLang, ui.session.profile == Profile.YOUNG_CHILD),
                    write = { system, transcript ->
                        val events = vm.ui.value?.events.orEmpty()
                        cloud.ask(system, transcript, subjectNames(events, content, officerLang), 300)
                    },
                    transcript = { counselTranscript(it, content) },
                )
            }
        }
    }
    val send = {
        // Anything the officer sends themselves takes the conversation back from the counsellor.
        if (counsel != Counsel.Off) vm.counsellor.takeOver()
        if (run.isNotEmpty() && ownText.isBlank()) {
            vm.showRun(runQuestions.map { Pending.Ask(it) })
            run = emptyList()
        } else {
            prepareTyped(); if (paired?.allowShow?.invoke() != false) vm.show()
        }
    }
    val read = { if (counsel != Counsel.Off) vm.counsellor.takeOver(); prepareTyped(); vm.readSigns() }
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
                    MiniFace(characterFor(ui), 40.dp, Color(0xFFFFDDBE))
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
                OfficerMenu(
                    ui = ui,
                    vm = vm,
                    canChangeSignLanguage = paired == null || ui.onStage == null,
                    onAiReport = {
                        aiExtracting = true
                        scope.launch {
                            try {
                                aiReport = cloud.report(sessionLog(ui.events), ui.session.role.name, subjectLang, subjectNames(ui.events, content, officerLang), signLanguage)
                            } finally {
                                aiExtracting = false
                            }
                        }
                    },
                    onEnd = { askEnd = true },
                )
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
                onFinish = { askEnd = true },
                onCounsel = if (cloud.available) startCounsel else null,
                onChildAvatar = vm::setChildAvatar,
                onTell = if (canReadSigns) ({ prepareTyped(); vm.readSigns(SignWindow.NARRATIVE); page = 0 }) else null,
                modifier = Modifier.weight(1f),
            )
        } else {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Transcript(ui.events, ui.session.profile, ui.signed, onReplay = { replaying = it }, Modifier.fillMaxSize(), onSayBack = { sayingBackId = it.id }, drawing = vm::drawing)
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

        if (counsel != Counsel.Off) CounsellorPanel(counsel, vm.counsellor, onResume = startCounsel)
        else if (allowAuto && ownText.isEmpty()) AutoPanel(vm.auto, ui, content, officerLang)
        Dock(
            ui = ui,
            content = content,
            officerLang = officerLang,
            text = ownText,
            onText = {
                if (ownText.isBlank() && it.isNotBlank() && ui.pending != null && ui.pending !is Pending.FreeText) vm.prepare(null)
                ownText = it; suggestion = null; noHelp = false; if (it.isBlank()) draftOff = false
            },
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
            draft = draft.takeIf { !draftOff },
            onDraftOff = { draftOff = true },
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
        if (saveAs.isEmpty()) saveAs = ui.session.title ?: defaultTitle(res, ui.session.startedAt)
        val startExport: () -> Unit = {
            askEnd = false
            export = ExportState.Working
            scope.launch {
                val by = shownBy(ui.events)
                val idle = notActedOn(ui.events)
                // Lesson-sheet hook: the report's personal details section (PersonalDetails.kt).
                val section = detailsSection(res, content, officerLang, ui.events)
                // The officer waits behind a dialog that cannot be closed, so a slow network gets 8 s, then no summary.
                val reportSummary = aiReport ?: runCatching {
                    withTimeoutOrNull(8_000) { cloud.report(sessionLog(ui.events), ui.session.role.name, subjectLang, subjectNames(ui.events, content, officerLang), signLanguage) }
                }.getOrNull()
                export = runCatching { vm.exportReport(reportText(res, ui, content), recordDetail(res, content, officerLang, ui.events) { eventDetail(res, it, by, idle) }, section, reportSummary) }
                    .fold({ (file, pass) -> ExportState.Ready(file, pass) }, { ExportState.Failed })
            }
        }
        // The everyday "Thank you." from the lesson sheet closes the conversation on the stage.
        val thanks = content.question(THANK_YOU)
        WrapUpDialog(
            summary = remember(ui.events) { wrapUp(ui.events, ui.session.startedAt, System.currentTimeMillis()) },
            title = saveAs,
            onTitle = { saveAs = it },
            onThank = thanks?.takeIf { paired?.allowShow?.invoke() != false }?.let { q -> { askEnd = false; vm.prepare(Pending.Ask(q)); vm.show() } },
            onExport = startExport,
            onSave = { askEnd = false; onEndSession(saveAs) },
            onDelete = { askEnd = false; onEndSession(null) },
            onDismiss = { askEnd = false },
        )
    }
    val modal = askEnd || export != null || aiExtracting || aiReport != null || sheet != null || askSensitive || phrase != null || sayingBack != null || replaying != null
    SideEffect { OfficerModal.open.value = modal }
    DisposableEffect(Unit) { onDispose { OfficerModal.open.value = false } }
    aiReport?.let { AiReportDialog(it, onDismiss = { aiReport = null }) }
    if (aiExtracting) AiPreparingDialog()

    export?.let { state ->
        ExportDialog(
            state = state,
            onShare = { (state as? ExportState.Ready)?.let { onShare(it.file) } },
            onFinish = { (state as? ExportState.Ready)?.file?.delete(); export = null; onEndSession(saveAs) },
            onDismiss = { export = null },
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
private fun defaultModes(profile: Profile) =
    if (profile == Profile.ISL_USER) listOf(SignMode.SIGNS, SignMode.SPELL, SignMode.PICTURES) else listOf(SignMode.GESTURES, SignMode.PICTURES)

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
