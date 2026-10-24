package app.signsetu.ui.session

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.onClick
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.LandmarkTracker
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.planText
import app.signsetu.ui.avatar.Timeline
import app.signsetu.data.content.chain
import app.signsetu.data.content.signBack
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import app.signsetu.data.session.Profile
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.data.content.questionKind
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.components.rememberSpeechPlayer
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import androidx.compose.ui.res.painterResource
import app.signsetu.camera.CAMERA_ASPECT
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.movableContentOf
import app.signsetu.data.content.WordBeat
import app.signsetu.data.content.Given

/** the reader's top labels a signed answer's confirm or say-back signs back before asking. */
fun readBackLabels(ui: SessionUi): List<String> {
    val confirm = ui.confirm?.takeIf { it.queue.isNotEmpty() }
    val sayBack = ui.pending as? Pending.FreeText
    return when {
        confirm != null -> listOfNotNull(confirm.read.firstOrNull())
        sayBack?.ref != null -> ui.signed[sayBack.ref].orEmpty().mapNotNull { it.guesses?.firstOrNull()?.word }
        else -> emptyList()
    }
}

/** Profiles whose subjects hear the questions without the officer pressing play (03-ux §3). */
private val AutoSpeech = setOf(Profile.YOUNG_CHILD, Profile.OTHER_LANGUAGE)

/** A No to a reading of the subject's own answer gives them their own way to say it again, never a dead end. */
internal fun reopensInput(pending: Pending?, yes: Boolean) =
    !yes && !pending.asksChoice && (pending is Pending.ShowBack || (pending is Pending.FreeText && pending.ref != null))

/** A reopened input holds the item until the subject submits it; the next run step would close it. */
internal fun advancesRun(pending: Pending?, yes: Boolean) = !reopensInput(pending, yes)

fun characterFor(profile: Profile) = if (profile == Profile.YOUNG_CHILD) Character.CHILD else Character.ADULT
fun characterFor(ui: SessionUi) = if (ui.child) Character.CHILD else Character.ADULT

/** The stage: what the subject sees while the phone faces them. The character on top, a live mirror of their
 * hands below, and the answers within reach of a thumb. */
@Composable
fun Stage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    cameraOn: Boolean,
    largeCaptions: Boolean,
    gestureSpeed: Float,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDrawing: (ByteArray) -> Unit,
    onWritten: (String) -> Unit,
    onSignFrame: (LandmarkFrame) -> Unit,
    onStartSigning: () -> Unit,
    onDoneSigning: () -> Unit,
    /** Null on a second screen, whose only exit is its own cover and end screens. */
    onOfficer: (() -> Unit)?,
    /** Auto mode's next statement, offered only on the subject's own tap. */
    onNext: (() -> Unit)? = null,
    onSignChoice: (SignChoice?) -> Unit = {},
    onNextRule: () -> Unit = {},
    onPauseEnd: () -> Unit = {},
    /** Lesson-sheet answers and phrases (LessonInputs.kt); null hides them. */
    onGiven: ((Given?) -> Unit)? = null,
    /** Called just before a tap that matches a gesture proposal: nod, shake, point or palm. */
    onCue: (String) -> Unit = {},
) {
    val stage = LocalStageColors.current
    val lang = ui.session.subjectLang
    val profile = ui.session.profile
    val pending = ui.pending
    val rule = ruleStep(pending)
    var drawing by remember(pending) { mutableStateOf(pending == Pending.Drawing) }
    // A drawing the officer asked for ends with thanks and a hand-back, never a bare greeting that looks like a reset.
    var drawingSent by remember(pending) { mutableStateOf(false) }
    var writing by remember(pending) { mutableStateOf(false) }
    // Held here, not in the panel: turning a tablet swaps the stage layout and would drop the panel's own state.
    val drawn = remember(pending) { mutableStateListOf<DrawStroke>() }
    // A young child is not asked to write; anyone else, signers above all, can answer in their own words.
    // A ground rule asks for no answer, so only the practice round opens the other ways to answer.
    val canWrite = pending != null && profile != Profile.YOUNG_CHILD && (rule == null || isPractice(pending))
    // The introduction and ground rules only tell; Yes and No under them would ask for an answer nobody asked for.
    val statement = rule != null && !isPractice(pending)
    // "Show me what you did this morning" has no yes or no answer.
    val openEnded = isPractice(pending)
    val signing = ui.reading != null
    val narrative = ui.signWindow == SignWindow.NARRATIVE
    val choices = ui.confirm?.queue?.firstOrNull()
    var saying by remember(pending) { mutableStateOf(false) }
    val lesson = lessonQuestion(pending)?.takeIf { onGiven != null && !signing }
    var replays by remember(pending) { mutableIntStateOf(0) }
    StagePace.forSession(ui.session.id, initialPace(profile, gestureSpeed))
    val pace = StagePace.speed.floatValue
    // A tapped word plays alone at half speed; the sentence clock waits until it is done.
    var focus by remember(pending) { mutableStateOf<WordBeat?>(null) }
    var wordTaps by remember(pending) { mutableIntStateOf(0) }
    var reacting by remember { mutableStateOf(false) }
    val answered = ui.answeredCard != null || ui.answeredYes != null
    LaunchedEffect(ui.answeredCard, ui.answeredYes) { reacting = answered }
    val paused = ui.answeredCard in PauseCards
    val context = LocalContext.current
    var showExpressBoard by rememberSaveable { mutableStateOf(false) }

    // A young child is not startled by a buzz in the hand.
    LaunchedEffect(ui.answeredYes, ui.answeredCard) {
        if (profile != Profile.YOUNG_CHILD) buzzPattern(ui.answeredYes, ui.answeredCard)?.let { buzz(context, it) }
    }

    val speech = rememberSpeechPlayer()
    val audio = speechFor(pending, content, lang)
    // Speech plays by itself only for subjects who hear and may not read; ISL users never get sound.
    LaunchedEffect(pending, lang) { if (audio != null && profile in AutoSpeech) speech.play(audio) }
    LaunchedEffect(pending) { withFrameNanos { }; ShowTiming.firstFrame() }

    val item = describeItem(pending, content, lang)
    // A show-back or say-back with pictures asks which one; the character must not act out the officer's reading.
    val checking = pending.asksChoice
    // One camera for the stage: a sign window adds analysis to it instead of restarting the preview. A nod or shake only highlights Yes or No; the subject's tap is the answer.
    val nod = remember { mutableStateOf<Boolean?>(null) }
    val point = remember { mutableStateOf<String?>(null) }
    val palm = remember { mutableStateOf(false) }
    val shrugged = remember { mutableStateOf(false) }
    LaunchedEffect(pending) { nod.value = null; point.value = null; palm.value = false; shrugged.value = false }
    val nodded = nod.value
    val camera = remember(tracker) {
        movableContentOf { frames: ((LandmarkFrame) -> Unit)? ->
            CameraMirror(tracker, Modifier.fillMaxSize(), onFrame = frames, onHead = { nod.value = it == Head.Nod }, onPoint = { point.value = it }, onStopPalm = { palm.value = true }, onShrug = { shrugged.value = true }) { MirrorPlaceholder() }
        }
    }
    val gestureId = when {
        reacting -> "okay"
        drawing || writing -> "school.write"
        choices != null -> "ask"
        // A question or typed text is still shown while they sign; otherwise the character invites them to.
        signing && pending !is Pending.Ask && pending !is Pending.FreeText && pending !is Pending.PhraseAsk -> "show_me"
        rule != null -> rule.gesture
        pending is Pending.Ask -> pending.question.gesture ?: pending.question.lexicon
        checking -> "ask"
        pending is Pending.ShowBack -> pending.entry.id
        pending is Pending.Cards -> "ask"
        pending is Pending.PhraseAsk || pending is Pending.FreeText -> null
        pending == Pending.LanguageFinder -> "wave"
        else -> "namaste"
    }
    val readLabels = readBackLabels(ui)
    val readBack = remember(readLabels, content) {
        readLabels.mapNotNull(content::signBack).takeIf { it.isNotEmpty() }?.let { chain("read_back", it + listOfNotNull(content.gestures["ask"])) }
    }
    val plan = remember(pending, content, readBack) {
        (pending as? Pending.FreeText)?.takeIf { !it.asksChoice && readBack == null }?.let { content.planText(it.text, it.modes) }
    }
    // Word highlighting follows the planned words, so it shows only when the caption is those same words.
    val highlightable = (pending as? Pending.FreeText)?.caption == null
    val focusGesture = remember(focus) {
        focus?.let { f -> (pending as? Pending.FreeText)?.let { content.planText(it.text.substring(f.range), it.modes).gesture } }
    }
    val gesture = when {
        focusGesture != null -> focusGesture
        readBack != null && (gestureId == null || gestureId == "ask") -> readBack
        gestureId != null -> content.gestures[gestureId]
        pending is Pending.PhraseAsk -> content.gestureFor(pending.phrase, pending.slot)
        pending is Pending.FreeText -> plan?.gesture ?: content.gestures["ask"]
        pending is Pending.Ask -> content.gestureFor(pending.question) // ISL hook: signed through the lexicon
        else -> null
    }
    // Follows the character through typed text, so the word being signed is lit and its picture or letters shown.
    val playClock = remember(pending) { mutableFloatStateOf(-1f) }
    LaunchedEffect(plan, replays, pace, reacting, focus) {
        playClock.floatValue = -1f
        if (plan == null || reacting || focus != null) return@LaunchedEffect
        val start = withFrameNanos { it }
        val end = (plan.beats.lastOrNull()?.end ?: 0f) + Timeline.LEAD_IN
        while (playClock.floatValue < end) {
            withFrameNanos { now -> playClock.floatValue = (now - start) / 1e9f * pace }
        }
        playClock.floatValue = -1f
    }
    val beat by remember(plan) {
        derivedStateOf {
            val t = playClock.floatValue - Timeline.LEAD_IN
            plan?.beats?.lastOrNull { t >= it.start && t < it.end }
        }
    }
    val letter by remember(plan) {
        derivedStateOf {
            val b = beat ?: return@derivedStateOf -1
            spelledIndex(b, playClock.floatValue - Timeline.LEAD_IN)
        }
    }
    val caption = when {
        reacting -> content.subjectText("okay", lang)
        drawingSent -> content.subjectText("thanks", lang)
        drawing -> content.subjectText("draw", lang)
        writing -> content.subjectText("write", lang)
        choices != null -> content.subjectText("is_this_it", lang)
        narrative -> content.subjectText("watching", lang)
        pending == null -> content.language(lang)?.hello
        // Greeting back in the language they picked tells them the tap was understood.
        pending == Pending.LanguageFinder -> ui.chosenLanguage?.let { content.language(it)?.hello }
        else -> item.caption
    }

    // The call view is the stage. Items that need more than Yes or No (pictures, numbers, drawing), writing, lessons and the
    // language finder keep their own panels below.
    // Over the whole stage, so no answer can be tapped during a break; the officer's way out stays above it.
    // A break can still become a Stop without first ending the pause.
    val pauseLayer: @Composable () -> Unit = {
        val stopInPause: (@Composable (Modifier) -> Unit)? = if (ui.answeredCard != "break") null else { m ->
            val answer = LocalAnswerColors.current
            SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false, { onCard("stop") }, m)
        }
        PauseLayer(content.subjectText("ready", lang), onPauseEnd, stop = stopInPause, modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f })
    }

    if (!drawing && !writing && pending != Pending.LanguageFinder && choices == null && lesson == null && item.choices.isEmpty() && !item.canDraw) {
        Box(Modifier.fillMaxSize()) {
            VideoCallStage(
                ui = ui,
                content = content,
                tracker = tracker,
                onCard = onCard,
                onYesNo = onYesNo,
                onSignFrame = onSignFrame,
                onStartSigning = onStartSigning,
                onOpenExpressBoard = { showExpressBoard = true },
                onCue = onCue,
                modifier = Modifier.fillMaxSize().then(if (paused) Modifier.clearAndSetSemantics {} else Modifier),
            )
            if (showExpressBoard && !paused) {
                FullChildMuteCommunicationBoard(
                    content = content,
                    lang = lang,
                    chosenCard = ui.answeredCard,
                    onCard = { id -> onCard(id); reacting = true },
                    onDismiss = { showExpressBoard = false },
                )
            }
            if (paused) pauseLayer()
            onOfficer?.let { OfficerHandle(it, Modifier.align(if (ui.rotated) Alignment.BottomStart else Alignment.TopEnd).safeDrawingPadding()) }
        }
        return
    }

    Surface(color = stage.skyBottom, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // The whole stage makes room for the keyboard, so the window never pans the character's face away.
                .imePadding()
                .graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f }
                // Covered by the pause layer: screen readers must not reach answers that cannot be tapped.
                .then(if (paused) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            // Landscape, which Android 16 forces on tablets, puts the character beside the camera instead of above it.
            val wide = maxWidth > maxHeight
            // A panel that needs width and height gets more room; otherwise the character and the camera split the screen.
            val rightsNotice = (pending as? Pending.Ask)?.question?.takeIf(content::isRights)?.let { content.subjectText("rights_interpreter", lang) }
            val topWeight = if (drawing || writing || choices != null || pending == Pending.LanguageFinder || saying || lesson != null) 0.36f else 0.5f
            val topAnimated by animateFloatAsState(topWeight, spring(stiffness = 300f), label = "split")
            val characterPane: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                Backdrop(Modifier.fillMaxSize())
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val roomy = maxHeight >= 240.dp
                    val avatarHeight = minOf(maxHeight - if (roomy) 64.dp else 48.dp, (maxWidth - SideBand * 2) / AVATAR_ASPECT).coerceAtLeast(0.dp)
                    Avatar(
                        character = characterFor(ui),
                        gesture = gesture,
                        playKey = when {
                            reacting -> "react:${ui.answeredCard}:${ui.answeredYes}"
                            focus != null -> "word:$wordTaps"
                            // Each sign being confirmed plays its own read-back.
                            else -> Triple(pending ?: choices, replays, ui.chosenLanguage)
                        },
                        speed = if (focus != null) 0.5f else pace,
                        // Breathing redraws every frame; inside a sign window the landmark tracker needs that time.
                        idle = !signing,
                        glance = pending != null && !drawing && !reacting,
                        question = if (pending is Pending.Ask || pending is Pending.PhraseAsk || pending is Pending.FreeText) questionKind(item.caption.orEmpty()) else null,
                        onFinished = { reacting = false; focus = null },
                        // Side bands keep the replay pill and held cards out of the signing space.
                        modifier = Modifier
                            .height(avatarHeight)
                            .aspectRatio(AVATAR_ASPECT)
                            .align(Alignment.BottomCenter),
                    )
                    val sensitiveGroups = remember(content) { content.groups.filter { it.sensitive }.map { it.id } }
                    val sensitive = (pending as? Pending.Ask)?.question?.group in sensitiveGroups
                    val glance = remember(ui.events, content) { glanceText(ui.events) { content.question(it)?.answer != null } }
                        ?.takeIf { !StagePace.glanceHidden.value && !sensitive && !drawing && !writing }
                    val onWordTap: ((Int) -> Unit)? = when {
                        pending is Pending.FreeText && highlightable && plan != null -> { offset ->
                            beatAt(plan.beats, offset)?.let { b ->
                                if (content.planText(pending.text.substring(b.range), pending.modes).gesture != null) { focus = b; wordTaps++ }
                            }
                        }
                        pending is Pending.Ask || pending is Pending.PhraseAsk || rule != null -> { _ -> replays++; audio?.let { speech.play(it, audioRate(pace)) } }
                        else -> null
                    }
                    val captionTop = if (glance != null) 44.dp else 12.dp
                    AnimatedContent(
                        targetState = caption,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                        // A long caption may reach the hair, never the brows; the text shrinks to fit instead.
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = captionTop, start = 52.dp, end = 52.dp)
                            .heightIn(max = (maxHeight - avatarHeight * 0.8f - captionTop).coerceAtLeast(48.dp)),
                        label = "caption",
                    ) { text ->
                        if (text != null) {
                            val own = text == item.caption
                            Bubble(
                                text, large = largeCaptions, compact = !roomy,
                                highlight = (focus?.range ?: beat?.range)?.takeIf { highlightable && own },
                                onWordTap = onWordTap?.takeIf { own },
                            )
                        }
                    }
                    val spelled = if (focus == null) beat?.letters else null
                    val shownCard = focus?.card ?: beat?.card
                    if (spelled != null) {
                        LetterTile(
                            spelled,
                            letter,
                            Modifier.align(Alignment.CenterEnd).padding(end = Spacing.lg),
                        )
                    } else if (shownCard != null) {
                        key(shownCard) {
                            HeldCard(
                                shownCard,
                                content.cards[shownCard]?.label?.get(lang),
                                Modifier.align(Alignment.CenterEnd).padding(end = Spacing.lg).popIn(rememberPopIn(0)),
                            )
                        }
                    } else item.held?.let { id ->
                        key(id) {
                            HeldCard(
                                id,
                                content.cards[id]?.label?.get(lang),
                                Modifier.align(Alignment.CenterEnd).padding(end = Spacing.lg).popIn(rememberPopIn(150)),
                            )
                        }
                    }
                    val replayable = gesture != null || (audio != null && profile != Profile.ISL_USER)
                    if (pending != null && !drawing && replayable) {
                        ReplayControls(
                            pace = pace,
                            onReplay = { focus = null; replays++; audio?.let { speech.play(it, audioRate(pace)) } },
                            onPace = {
                                val next = nextPace(pace)
                                StagePace.speed.floatValue = next
                                focus = null
                                replays++
                                audio?.let { speech.play(it, audioRate(next)) }
                            },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = Spacing.lg),
                        )
                    }
                    if (onGiven != null && !signing && !saying) SayButton(content, lang, { saying = true }, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = if (glance != null) 44.dp else 4.dp))
                    glance?.let { GlanceStrip(it, onHide = { StagePace.glanceHidden.value = true }, Modifier.align(Alignment.TopCenter)) }
                }
            }
            }
            val answerPane: @Composable (Modifier) -> Unit = { m ->
            Column(
                m.padding(horizontal = Spacing.lg).padding(top = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when {
                    choices != null -> {
                        val last = ui.confirm.queue.size == 1
                        SignChoicePanel(choices, content, lang, {
                            onSignChoice(it)
                            reacting = true
                            if (it == null && last && tracker != null) onStartSigning()
                        }, Modifier.weight(1f))
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    drawing -> {
                        // Only a picture that was asked for has nothing to go back to.
                        DrawingPanel(
                            content, lang, drawn,
                            onDone = { png ->
                                onDrawing(png)
                                drawn.clear()
                                drawing = false
                                if (pending == Pending.Drawing) drawingSent = true else reacting = true
                            },
                            onBack = { drawing = false }.takeIf { pending != Pending.Drawing },
                            modifier = Modifier.weight(1f),
                        )
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    drawingSent -> {
                        HandBackPanel(content, lang, onDrawMore = { drawn.clear(); drawingSent = false; drawing = true }, modifier = Modifier.weight(1f))
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    writing -> WritingPanel(
                        content, lang,
                        onDone = { onWritten(it); writing = false; reacting = true },
                        onBack = { writing = false },
                        pauseRow = { PauseRow(content, lang, ui.answeredCard, onCard, compact = true) },
                        modifier = Modifier.weight(1f),
                    )
                    saying && onGiven != null -> PhraseBoard(content, lang, ui.answeredCard, onCard, { onGiven(it); saying = false; reacting = true }, { saying = false }, Modifier.weight(1f))
                    lesson != null && onGiven != null -> AnswerPanel(
                        lesson, content, lang, ui.answeredCard, onCard, { onGiven(it); if (it?.confirmed == true) reacting = true },
                        onStartSigning.takeIf { tracker != null }, Modifier.weight(1f),
                    )
                    pending == Pending.LanguageFinder -> {
                        LanguageGrid(content, ui.chosenLanguage, Modifier.weight(1f)) { code ->
                            onLanguage(code)
                            content.greetingAudio(code)?.let { speech.play(it) }
                        }
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    else -> {
                        val answer = LocalAnswerColors.current
                        val chosen = ui.answeredCard
                        val asks = pending != null && rightsNotice == null && !statement
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Rail {
                                when {
                                    signing -> RailCard("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false) { onCard("stop"); onDoneSigning(); reacting = true }
                                    // A Yes under a right would read as the person accepting or waiving it; it is only shown.
                                    rightsNotice != null -> RailCard(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
                                    asks -> {
                                        if (!openEnded) RailIcon(Icons.Filled.Check, content.subjectText("yes", lang), stringResource(R.string.show_yes), answer.yes, answer.onYes, ui.answeredYes == true, proposed = nodded == true) { if (nodded == true) onCue("nod"); onYesNo(true) }
                                        RailCard("dont_know", content.subjectText("dont_know", lang), stringResource(R.string.stage_dont_know), answer.unsure, answer.onUnsure, chosen == "dont_know", proposed = shrugged.value) { if (shrugged.value) onCue("shrug"); onCard("dont_know") }
                                        RailCard(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
                                    }
                                }
                                if (!signing && item.canDraw) RailIcon(Icons.Filled.Create, content.subjectText("draw", lang), stringResource(R.string.stage_draw), stage.tool, stage.onTool, tool = true) { drawing = true }
                                if (!signing && canWrite) RailIcon(painterResource(R.drawable.ic_keyboard), content.subjectText("write", lang), stringResource(R.string.stage_write), stage.tool, stage.onTool, tool = true) { writing = true }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val window = Modifier.aspectRatio(CAMERA_ASPECT)
                                if (signing) SigningPanel(camera, ui.reading.orEmpty(), content, lang, narrative, onFrame = onSignFrame, modifier = window)
                                else MirrorWindow(camera, cameraOn, item.choices, item.numbers, chosen, content, lang, { id -> if (id == point.value) onCue("point"); onCard(id) }, window, proposedCard = point.value)
                                // One plain line says what to do now, so a frightened person is never left guessing.
                                val hint = when {
                                    signing -> if (narrative) null else content.subjectText("hint_signing", lang)
                                    rightsNotice != null || reacting || item.choices.isNotEmpty() -> null
                                    pending == null -> content.subjectText("hint_wait", lang)
                                    statement -> content.subjectText("hint_next", lang)
                                    else -> content.subjectText("hint_answer", lang)
                                }
                                hint?.let { StageHint(it, Modifier.align(if (signing) Alignment.BottomCenter else Alignment.TopCenter).padding(bottom = if (signing) 40.dp else 0.dp)) }
                                rightsNotice?.let {
                                    Text(
                                        it, style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center, maxLines = 3,
                                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(Spacing.sm)
                                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(Spacing.sm),
                                    )
                                }
                            }
                            Rail {
                                if (signing) {
                                    RailIcon(Icons.Filled.Check, content.subjectText("done", lang), stringResource(R.string.stage_done), answer.yes, answer.onYes) { onDoneSigning(); reacting = true }
                                } else {
                                    val next = if (rule != null) onNextRule else onNext
                                    next?.let { RailIcon(Icons.AutoMirrored.Filled.ArrowForward, content.subjectText("next", lang), stringResource(R.string.stage_next), answer.yes, answer.onYes, onClick = it) }
                                    if (asks && !openEnded) {
                                        RailIcon(Icons.Filled.Close, content.subjectText("no", lang), stringResource(R.string.show_no), answer.no, answer.onNo, ui.answeredYes == false, proposed = nodded == false) {
                                            if (nodded == false) onCue("shake")
                                            onYesNo(false)
                                            if (reopensInput(pending, false)) { if (canWrite) writing = true else drawing = true }
                                        }
                                        RailCard("wrong", content.subjectText("wrong", lang), stringResource(R.string.stage_wrong), answer.unsure, answer.onUnsure, chosen == "wrong") { onCard("wrong") }
                                    }
                                    // A camera, not an open palm, so nothing beside Stop looks like a stop sign.
                                    if ((canWrite || pending == null) && tracker != null) RailIcon(painterResource(R.drawable.ic_videocam), content.subjectText("sign", lang), stringResource(R.string.officer_read_signs), stage.tool, stage.onTool, tool = true, onClick = onStartSigning)
                                    RailCard("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosen == "stop", proposed = palm.value) { if (palm.value) onCue("palm"); onCard("stop") }
                                }
                            }
                        }
                    }
                }
            }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                characterPane(Modifier.weight(topAnimated).fillMaxHeight())
                // Room above the panels for the officer's handle, which would otherwise sit on a drawing or a keyboard.
                answerPane(Modifier.weight(1f - topAnimated).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)).padding(top = 48.dp))
            } else Column(Modifier.fillMaxSize()) {
                characterPane(Modifier.weight(topAnimated).fillMaxWidth())
                answerPane(Modifier.weight(1f - topAnimated).fillMaxWidth())
            }
        }
        if (paused) pauseLayer()
        if (showExpressBoard && !paused) {
            FullChildMuteCommunicationBoard(
                content = content,
                lang = lang,
                chosenCard = ui.answeredCard,
                onCard = { id ->
                    onCard(id)
                    reacting = true
                },
                onDismiss = { showExpressBoard = false },
            )
        }
        onOfficer?.let { OfficerHandle(it, Modifier.align(if (ui.rotated) Alignment.BottomStart else Alignment.TopEnd).safeDrawingPadding()) }
        }
    }
}

/** What one shown item puts on the stage. */
internal class StageItem(
    val caption: String?,
    val held: String?,
    val choices: List<String>,
    val numbers: Boolean,
    val canDraw: Boolean,
)

internal fun describeItem(pending: Pending?, content: ContentBundle, lang: String): StageItem {
    val (caption, cards) = when (pending) {
        is Pending.Ask -> pending.question.text.get(lang) to pending.question.cards
        is Pending.FreeText -> (pending.caption ?: pending.text) to pending.cardIds
        is Pending.Cards -> null to pending.cardIds
        is Pending.ShowBack -> content.subjectText("is_this_it", lang) to pending.choices.takeIf { pending.asksChoice }.orEmpty()
        is Pending.PhraseAsk -> pending.slot.text.get(lang) to listOf(pending.slot.card)
        is Pending.GroundRules -> ruleStep(pending)?.let { ruleText(content, it.key, lang) } to
            if (isPractice(pending)) listOf("draw") else listOfNotNull(ruleStep(pending)?.card)
        else -> null to emptyList()
    }
    val pictures = cards.filter { it != "draw" && it in content.cards }
    val numbers = pictures.isNotEmpty() && pictures.all { it.matches(Regex("n\\d+")) }
    val held = when {
        pending is Pending.ShowBack && !pending.asksChoice -> pending.entry.card
        pending !is Pending.Cards && pictures.size == 1 -> pictures.first()
        else -> null
    }
    val choices = if (held != null) emptyList() else pictures.take(if (numbers) 10 else 6)
    return StageItem(caption, held, choices, numbers, "draw" in cards)
}

private fun speechFor(pending: Pending?, content: ContentBundle, lang: String): String? = when (pending) {
    is Pending.Ask -> content.questionAudio(pending.question.id, lang)
    is Pending.ShowBack -> content.subjectAudio("is_this_it", lang)
    is Pending.PhraseAsk -> content.phraseAudio(pending.phrase, pending.slot, lang)
    is Pending.GroundRules -> ruleStep(pending)?.let { ruleAudio(content, it.key, lang) }
    else -> null
}

/** Recorded speech below normal speed only goes to 0.75: slower than that, words smear. */
private fun audioRate(pace: Float) = if (pace < 1f) 0.75f else 1f

/** Which unit of a spelled word the letter tile shows [t] seconds into the plan's gesture, before its lead-in:
 * one akshara at a time, left to right. -1 when the beat only holds a picture. */
internal fun spelledIndex(beat: WordBeat, t: Float): Int {
    val n = beat.letters?.size ?: return -1
    if (n == 0) return -1
    // A zero-length beat cannot be divided; show its first unit rather than crashing on the range.
    if (beat.end <= beat.start) return 0
    val progress = ((t - beat.start) / (beat.end - beat.start)).coerceIn(0f, 1f)
    return (progress * n).toInt().coerceIn(0, n - 1)
}
