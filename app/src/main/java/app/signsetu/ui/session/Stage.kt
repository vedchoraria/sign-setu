package app.signsetu.ui.session

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.LandmarkTracker
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.data.content.planText
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.chain
import app.signsetu.data.content.signBack
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.signsetu.data.session.Profile
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.data.content.questionKind
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.rememberSpeechPlayer
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.signsetu.camera.CAMERA_ASPECT
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SIGN_LIMIT_MS
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.movableContentOf
import app.signsetu.data.content.WordBeat
import app.signsetu.data.content.Given

/** FR-86: the reader's top labels a signed answer's confirm or say-back signs back before asking. */
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

fun characterFor(profile: Profile) = if (profile == Profile.ISL_USER) Character.ADULT else Character.CHILD

/**
 * The stage: what the subject sees while the phone faces them. The character on top, a live mirror of their hands
 * below, and the answers within reach of a thumb. Nothing here shows notes, the log, or unconfirmed input.
 */
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
    /** Called just before a tap that matches a gesture proposal (D-40): nod, shake, point or palm. */
    onCue: (String) -> Unit = {},
) {
    val stage = LocalStageColors.current
    val lang = ui.session.subjectLang
    val profile = ui.session.profile
    val pending = ui.pending
    val rule = ruleStep(pending)
    var drawing by remember(pending) { mutableStateOf(pending == Pending.Drawing) }
    var writing by remember(pending) { mutableStateOf(false) }
    // A young child is not asked to write; anyone else, signers above all, can answer in their own words.
    // A ground rule asks for no answer, so only the practice round opens the other ways to answer.
    val canWrite = pending != null && profile != Profile.YOUNG_CHILD && (rule == null || isPractice(pending))
    val signing = ui.reading != null
    val narrative = ui.signWindow == SignWindow.NARRATIVE
    val choices = ui.confirm?.queue?.firstOrNull()
    // Lesson-sheet hook (LessonInputs.kt): an answer input or the phrase board takes the panel under the character.
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
    // A young child is not startled by a buzz in the hand.
    LaunchedEffect(ui.answeredYes, ui.answeredCard) {
        if (profile != Profile.YOUNG_CHILD) buzzPattern(ui.answeredYes, ui.answeredCard)?.let { buzz(context, it) }
    }

    val speech = rememberSpeechPlayer()
    val audio = speechFor(pending, content, lang)
    // Speech plays by itself only for subjects who hear and may not read; ISL users never get sound (NFR-24).
    LaunchedEffect(pending, lang) { if (audio != null && profile in AutoSpeech) speech.play(audio) }
    LaunchedEffect(pending) { withFrameNanos { }; ShowTiming.firstFrame() }

    val item = describeItem(pending, content, lang)
    // A show-back or say-back with pictures asks which one; the character must not act out the officer's reading.
    val checking = pending.asksChoice
    // One camera for the stage: a sign window adds analysis to it instead of restarting the preview.
    // A nod or shake only highlights Yes or No; the subject's tap is the answer.
    // One state object: the remembered camera keeps the first one it captured.
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
            val b = beat
            val letters = b?.letters ?: return@derivedStateOf -1
            val t = playClock.floatValue - Timeline.LEAD_IN
            ((t - b.start) / (b.end - b.start) * letters.length).toInt().coerceIn(0, letters.length - 1)
        }
    }
    val caption = when {
        reacting -> content.subjectText("okay", lang)
        drawing -> content.subjectText("draw", lang)
        writing -> content.subjectText("write", lang)
        choices != null -> content.subjectText("is_this_it", lang)
        narrative -> content.subjectText("watching", lang)
        pending == null -> content.language(lang)?.hello
        // Greeting back in the language they picked tells them the tap was understood.
        pending == Pending.LanguageFinder -> ui.chosenLanguage?.let { content.language(it)?.hello }
        else -> item.caption
    }

    Surface(color = stage.skyBottom, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f }
                // Covered by the pause layer: screen readers must not reach answers that cannot be tapped.
                .then(if (paused) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            // A panel that needs width and height gets more room; otherwise the character and the camera split the screen.
            val rightsNotice = (pending as? Pending.Ask)?.question?.takeIf(content::isRights)?.let { content.subjectText("rights_interpreter", lang) }
            val topWeight = if (drawing || writing || choices != null || pending == Pending.LanguageFinder || saying || lesson != null) 0.36f else 0.5f
            val topAnimated by animateFloatAsState(topWeight, spring(stiffness = 300f), label = "split")
            Box(
                Modifier
                    .weight(topAnimated)
                    .fillMaxWidth(),
            ) {
                Backdrop(Modifier.fillMaxSize())
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val roomy = maxHeight >= 240.dp
                    val avatarHeight = minOf(maxHeight - if (roomy) 64.dp else 48.dp, (maxWidth - SideBand * 2) / AVATAR_ASPECT).coerceAtLeast(0.dp)
                    Avatar(
                        character = characterFor(profile),
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
                                content.cards[shownCard]?.label?.pick(lang),
                                Modifier.align(Alignment.CenterEnd).padding(end = Spacing.lg).popIn(rememberPopIn(0)),
                            )
                        }
                    } else item.held?.let { id ->
                        key(id) {
                            HeldCard(
                                id,
                                content.cards[id]?.label?.pick(lang),
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
                    // Lesson-sheet hook: the phrase board's button.
                    if (onGiven != null && !signing && !saying) SayButton(content, lang, { saying = true }, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = if (glance != null) 44.dp else 4.dp))
                    glance?.let { GlanceStrip(it, onHide = { StagePace.glanceHidden.value = true }, Modifier.align(Alignment.TopCenter)) }
                }
            }
            Column(
                Modifier
                    .weight(1f - topAnimated)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.md, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when {
                    choices != null -> {
                        val last = ui.confirm?.queue?.size == 1
                        SignChoicePanel(choices, content, lang, {
                            onSignChoice(it)
                            reacting = true
                            if (it == null && last && tracker != null) onStartSigning()
                        }, Modifier.weight(1f))
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    drawing -> {
                        // Only a picture that was asked for has nothing to go back to.
                        DrawingPanel(content, lang, onDone = onDrawing, onBack = { drawing = false }.takeIf { pending != Pending.Drawing }, modifier = Modifier.weight(1f))
                        PauseRow(content, lang, ui.answeredCard, onCard)
                    }
                    writing -> WritingPanel(
                        content, lang,
                        onDone = { onWritten(it); writing = false; reacting = true },
                        onBack = { writing = false },
                        pauseRow = { PauseRow(content, lang, ui.answeredCard, onCard, compact = true) },
                        modifier = Modifier.weight(1f),
                    )
                    // Lesson-sheet hook: phrase board and answer inputs.
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
                        val asks = pending != null && rightsNotice == null
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Rail {
                                when {
                                    signing -> RailCard("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false) { onCard("stop"); onDoneSigning(); reacting = true }
                                    // A Yes under a right would read as the person accepting or waiving it; it is only shown.
                                    rightsNotice != null -> RailCard(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
                                    asks -> {
                                        RailIcon(Icons.Filled.Check, content.subjectText("yes", lang), stringResource(R.string.show_yes), answer.yes, answer.onYes, ui.answeredYes == true, proposed = nodded == true) { if (nodded == true) onCue("nod"); onYesNo(true) }
                                        RailCard("dont_know", content.subjectText("dont_know", lang), stringResource(R.string.stage_dont_know), answer.unsure, answer.onUnsure, chosen == "dont_know", proposed = shrugged.value) { if (shrugged.value) onCue("shrug"); onCard("dont_know") }
                                        RailCard(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
                                    }
                                }
                                if (!signing && item.canDraw) RailIcon(Icons.Filled.Create, content.subjectText("draw", lang), stringResource(R.string.stage_draw), stage.tool, stage.onTool, tool = true) { drawing = true }
                                if (!signing && canWrite) RailIcon(Icons.Filled.Edit, content.subjectText("write", lang), stringResource(R.string.stage_write), stage.tool, stage.onTool, tool = true) { writing = true }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val window = Modifier.aspectRatio(CAMERA_ASPECT)
                                if (signing) SigningPanel(camera, ui.reading.orEmpty(), content, lang, narrative, onFrame = onSignFrame, modifier = window)
                                else MirrorWindow(camera, cameraOn, item.choices, item.numbers, chosen, content, lang, { id -> if (id == point.value) onCue("point"); onCard(id) }, window, proposedCard = point.value)
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
                                    if (asks) {
                                        RailIcon(Icons.Filled.Close, content.subjectText("no", lang), stringResource(R.string.show_no), answer.no, answer.onNo, ui.answeredYes == false, proposed = nodded == false) {
                                            if (nodded == false) onCue("shake")
                                            onYesNo(false)
                                            if (reopensInput(pending, false)) { if (canWrite) writing = true else drawing = true }
                                        }
                                        RailCard("wrong", content.subjectText("wrong", lang), stringResource(R.string.stage_wrong), answer.unsure, answer.onUnsure, chosen == "wrong") { onCard("wrong") }
                                    }
                                    // A camera, not an open palm, so nothing beside Stop looks like a stop sign.
                                    if (canWrite && tracker != null) RailIcon(painterResource(R.drawable.ic_videocam), null, stringResource(R.string.officer_read_signs), stage.tool, stage.onTool, tool = true, onClick = onStartSigning)
                                    RailCard("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosen == "stop", proposed = palm.value) { if (palm.value) onCue("palm"); onCard("stop") }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Over the whole stage, so no answer can be tapped during a break; the officer's way out stays above it.
        // A break can still become a Stop without first ending the pause.
        val stopInPause: (@Composable (Modifier) -> Unit)? = if (ui.answeredCard != "break") null else { m ->
            val answer = LocalAnswerColors.current
            SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false, { onCard("stop") }, m)
        }
        if (paused) PauseLayer(content.subjectText("ready", lang), onPauseEnd, stop = stopInPause, modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (ui.rotated) 180f else 0f })
        onOfficer?.let { OfficerHandle(it, Modifier.align(if (ui.rotated) Alignment.BottomStart else Alignment.TopEnd).safeDrawingPadding()) }
        }
    }
}

/** What one shown item puts on the stage. */
private class StageItem(
    val caption: String?,
    val held: String?,
    val choices: List<String>,
    val numbers: Boolean,
    val canDraw: Boolean,
)

private fun describeItem(pending: Pending?, content: ContentBundle, lang: String): StageItem {
    val (caption, cards) = when (pending) {
        is Pending.Ask -> pending.question.text.pick(lang) to pending.question.cards
        is Pending.FreeText -> (pending.caption ?: pending.text) to pending.cardIds
        is Pending.Cards -> null to pending.cardIds
        is Pending.ShowBack -> content.subjectText("is_this_it", lang) to pending.choices.takeIf { pending.asksChoice }.orEmpty()
        is Pending.PhraseAsk -> pending.slot.text.pick(lang) to listOf(pending.slot.card)
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

@Composable
internal fun Backdrop(modifier: Modifier) {
    val stage = LocalStageColors.current
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(stage.skyTop, stage.skyBottom)))
        // A low sun and a soft floor give the character a place to stand, without anything to look at instead.
        drawCircle(stage.glow, size.minDimension * 0.42f, Offset(size.width * 0.86f, size.height * 0.16f), alpha = 0.55f)
        drawCircle(stage.glow, size.minDimension * 0.18f, Offset(size.width * 0.08f, size.height * 0.42f), alpha = 0.35f)
        drawOval(stage.ground, Offset(-size.width * 0.2f, size.height * 0.82f), Size(size.width * 1.4f, size.height * 0.5f))
    }
}

private val BubbleTail = 12.dp

/** Each side of the character: the replay pill on the left, a held card or letter tile on the right. */
private val SideBand = 88.dp

/** A speech bubble whose tail points down at the character. */
private object BubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { BubbleTail.toPx() }
        val r = with(density) { 22.dp.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height - tail, CornerRadius(r, r)))
            moveTo(size.width / 2f - tail, size.height - tail - 1f)
            lineTo(size.width / 2f + tail * 0.2f, size.height)
            lineTo(size.width / 2f + tail, size.height - tail - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
internal fun Bubble(text: String, large: Boolean = false, compact: Boolean = false, highlight: IntRange? = null, onWordTap: ((Int) -> Unit)? = null) {
    val stage = LocalStageColors.current
    val styled = buildAnnotatedString {
        append(text)
        highlight?.takeIf { it.last < text.length }?.let {
            addStyle(SpanStyle(color = stage.onHighlight, background = stage.highlight, fontWeight = FontWeight.Bold), it.first, it.last + 1)
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val replayName = stringResource(R.string.word_replay)
    // The caller's lambda changes every frame of typed-text playback; the tap detector must not restart with it.
    val onTap by rememberUpdatedState(onWordTap)
    val tap = if (onWordTap == null) Modifier else Modifier
        .pointerInput(Unit) { detectTapGestures { p -> layout?.let { onTap?.invoke(it.getOffsetForPosition(p)) } } }
        .semantics { onClick(replayName) { onTap?.invoke(0); true } }
    // Line height in em so a caption shrunk to fit gains lines instead of clipping.
    val style = when {
        large -> MaterialTheme.typography.headlineMedium
        compact -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }.copy(lineHeight = 1.2.em)
    Surface(
        shape = BubbleShape,
        color = stage.bubble,
        contentColor = stage.onBubble,
        border = BorderStroke(1.dp, stage.onBubble.copy(alpha = 0.18f)),
        shadowElevation = 3.dp,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        // A right or a question must be read to its end: the text shrinks to fit rather than being cut off.
        Text(
            styled,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = if (large) 6 else 5,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp + BubbleTail).then(tap),
        )
    }
}

/** The picture the question is about, held up beside the character. */
@Composable
private fun HeldCard(cardId: String, label: String?, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.rotate(-4f).size(width = SideBand, height = if (label != null) 108.dp else SideBand),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardImage(cardId, Modifier.fillMaxWidth().aspectRatio(1f))
            label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF2B2118), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The word being fingerspelled, its current letter large, so a reader can follow the hands. */
@Composable
private fun LetterTile(letters: String, current: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp, modifier = modifier.rotate(-4f).size(SideBand, 108.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                letters.getOrNull(current)?.uppercase() ?: "",
                fontFamily = Baloo,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                color = Color(0xFF2B2118),
            )
            Text(
                buildAnnotatedString {
                    letters.uppercase().forEachIndexed { i, c ->
                        withStyle(SpanStyle(color = if (i == current) Color(0xFF2B2118) else Color(0xFFB8A99A), fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)) { append(c) }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OfficerHandle(onClick: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    // Small and quiet: the subject should not be drawn to it; the officer knows where it is.
    Box(
        modifier
            .padding(6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = stage.handle, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.show_officer), tint = stage.onHandle, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ReplayControls(pace: Float, onReplay: () -> Unit, onPace: () -> Unit, modifier: Modifier) {
    val stage = LocalStageColors.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = stage.tool.copy(alpha = 0.9f),
        contentColor = stage.onTool,
        border = BorderStroke(1.dp, stage.onTool.copy(alpha = 0.14f)),
        modifier = modifier.width(56.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.show_replay), modifier = Modifier.size(26.dp))
            }
            val shown = when { pace < 0.6f -> "½×"; pace < 0.9f -> "¾×"; else -> "1×" }
            val paceName = stringResource(R.string.show_pace, shown)
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onPace).semantics { contentDescription = paceName }, contentAlignment = Alignment.Center) {
                Text(
                    shown,
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** The mirror: the subject sees their own hands; choices sit at the bottom edge of it. */
@Composable
private fun MirrorWindow(
    camera: @Composable (((LandmarkFrame) -> Unit)?) -> Unit,
    cameraOn: Boolean,
    choices: List<String>,
    numbers: Boolean,
    chosenCard: String?,
    content: ContentBundle,
    lang: String,
    onCard: (String) -> Unit,
    modifier: Modifier,
    proposedCard: String? = null,
) {
    Box(modifier.mirrorFrame()) {
        if (cameraOn) {
            // Outside a sign window the mirror is preview only: no frame is analysed (D-25).
            camera(null)
        } else {
            MirrorPlaceholder()
        }
        if (choices.isNotEmpty()) key(choices) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (numbers) {
                    choices.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 40 * choices.indexOf(id)
                                NumberButton(id.removePrefix("n"), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)))
                            }
                        }
                    }
                } else {
                    choices.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { id ->
                                val delay = 70 * choices.indexOf(id)
                                ChoiceTile(id, content.cards[id]?.label?.pick(lang), chosenCard == id, { onCard(id) }, Modifier.weight(1f).popIn(rememberPopIn(delay)), name = content.cards[id]?.label?.pick("en"), proposed = proposedCard == id)
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.mirrorFrame(): Modifier {
    val stage = LocalStageColors.current
    val shape = RoundedCornerShape(20.dp)
    // The letterbox around the whole camera frame is the window colour, so the window reads as one calm pane.
    return fillMaxWidth()
        .shadow(3.dp, shape)
        .clip(shape)
        .background(stage.window)
        .border(4.dp, stage.windowFrame.copy(alpha = 0.4f), shape)
}

/**
 * A sign window: a large mirror with the hand and arm skeleton, a dot per sign seen, and Done. Only landmarks are kept,
 * in memory (D-25). A short answer shows its time bar and, after a failed read, the "sign slower" cue; a [narrative]
 * shows neither, so nothing interrupts the person while they tell what happened.
 */
@Composable
private fun SigningPanel(
    camera: @Composable (((LandmarkFrame) -> Unit)?) -> Unit,
    segments: List<SignSegment>,
    content: ContentBundle,
    lang: String,
    narrative: Boolean,
    onFrame: (LandmarkFrame) -> Unit,
    modifier: Modifier,
) {
    val answer = LocalAnswerColors.current
    val used = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!narrative) used.animateTo(1f, tween(SIGN_LIMIT_MS.toInt(), easing = LinearEasing)) }
    Box(modifier.mirrorFrame()) {
        camera(onFrame)
        if (!narrative) {
            LinearProgressIndicator(
                progress = { used.value },
                color = answer.yes,
                trackColor = Color.White.copy(alpha = 0.3f),
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).height(6.dp).clip(CircleShape),
            )
            SignSlowerCue(segments.lastOrNull()?.takeIf { it.slower }, content, lang, Modifier.align(Alignment.TopCenter).padding(top = 30.dp, start = Spacing.sm, end = Spacing.sm))
        }
        SeenSigns(segments, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.md))
    }
}

/** Entrance for things put in front of the subject: a short rise and settle, so a new picture is noticed. */
@Composable
private fun rememberPopIn(delayMillis: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    return progress
}

private fun Modifier.popIn(progress: Animatable<Float, AnimationVector1D>) = graphicsLayer {
    val p = progress.value
    alpha = p.coerceIn(0f, 1f)
    scaleX = 0.7f + 0.3f * p
    scaleY = 0.7f + 0.3f * p
    translationY = (1f - p) * 24.dp.toPx()
}

@Composable
private fun MirrorPlaceholder() {
    val stage = LocalStageColors.current
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF4A4352), stage.window))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(56.dp))
    }
}

@Composable
internal fun ChoiceTile(cardId: String, label: String?, chosen: Boolean, onClick: () -> Unit, modifier: Modifier, name: String? = label, proposed: Boolean = false) {
    val answer = LocalAnswerColors.current
    val pulse = if (proposed && !chosen) rememberInfiniteTransition(label = "proposed").animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ring").value else 0f
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = if (chosen) BorderStroke(4.dp, answer.selection) else if (pulse > 0f) BorderStroke(4.dp, answer.selection.copy(alpha = pulse)) else TileEdge,
        modifier = modifier.heightIn(min = 96.dp).semantics { (label ?: name)?.let { contentDescription = it }; selected = chosen },
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Without a word in their language the picture carries the meaning alone, so it gets the room.
            CardImage(cardId, Modifier.size(if (label != null) 58.dp else 76.dp))
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFF2B2118), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun NumberButton(digit: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val answer = LocalAnswerColors.current
    PressableSurface(
        onClick = onClick,
        shape = CircleShape,
        color = if (chosen) answer.selection else Color.White,
        border = if (chosen) null else TileEdge,
        modifier = modifier.aspectRatio(1f).semantics { selected = chosen },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = if (chosen) answer.onSelection else Color(0xFF2B2118))
        }
    }
}


@Composable
private fun PauseRow(content: ContentBundle, lang: String, chosenCard: String?, onCard: (String) -> Unit, compact: Boolean = false) {
    val answer = LocalAnswerColors.current
    SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosenCard == "stop", { onCard("stop") }, Modifier.fillMaxWidth(), height = if (compact) 48.dp else 52.dp)
}

/**
 * Yes, No and the answers that keep a conversation honest sit in narrow rails either side of the camera, always in the
 * same place with picture, word and colour (FR-20, NFR-21, ABE 2.104 to 2.108). Every tap is logged after the prompt shown.
 */
@Composable
private fun Rail(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.width(60.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically), content = content)

@Composable
private fun ColumnScope.RailCard(cardId: String, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, proposed: Boolean = false, onClick: () -> Unit) =
    RailButton(label, name, color, onColor, chosen, false, onClick, proposed) { CardImage(cardId, Modifier.size(if (label != null) 24.dp else 32.dp)) }

@Composable
private fun ColumnScope.RailIcon(icon: ImageVector, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean = false, tool: Boolean = false, proposed: Boolean = false, onClick: () -> Unit) =
    RailIcon(rememberVectorPainter(icon), label, name, color, onColor, chosen, tool, proposed, onClick)

@Composable
private fun ColumnScope.RailIcon(icon: Painter, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean = false, tool: Boolean = false, proposed: Boolean = false, onClick: () -> Unit) =
    RailButton(label, name, color, onColor, chosen, tool, onClick, proposed) { Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(24.dp)) }

@Composable
private fun ColumnScope.RailButton(label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, tool: Boolean, onClick: () -> Unit, proposed: Boolean = false, icon: @Composable () -> Unit) {
    val pulse = if (proposed && !chosen) rememberInfiniteTransition(label = "proposed").animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ring").value else 0f
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = when {
            chosen -> BorderStroke(3.dp, onColor)
            pulse > 0f -> BorderStroke(4.dp, onColor.copy(alpha = pulse))
            tool -> BorderStroke(1.dp, onColor.copy(alpha = 0.16f))
            else -> null
        },
        // 64 dp fits an icon over two lines of Hindi; five in one rail on a 640 dp phone share the height, still above 48 dp.
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().height(64.dp)
            .semantics { contentDescription = label ?: name; selected = chosen },
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
        modifier = modifier.height(76.dp).semantics { contentDescription = label ?: name },
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
        modifier = modifier.height(height).semantics { contentDescription = label ?: name },
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
    shape: androidx.compose.ui.graphics.Shape,
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
        modifier = modifier.scale(scale),
        content = content,
    )
}

@Composable
private fun LanguageGrid(content: ContentBundle, chosen: String?, modifier: Modifier, onLanguage: (String) -> Unit) {
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
                            Text(l.name, fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Color(0xFF2B2118), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.english, fontSize = 11.sp, color = Color(0xFF6B5E50), maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class DrawStroke(val points: List<Offset>)

/** The subject's own words, typed large on the stage; the keyboard opens at once and Done sends them. */
@Composable
private fun WritingPanel(
    content: ContentBundle,
    lang: String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    pauseRow: @Composable () -> Unit,
    modifier: Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val answer = LocalAnswerColors.current
    val send = { if (text.isNotBlank()) onDone(text) }
    Column(modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = TileEdge, shadowElevation = 2.dp, modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value = text,
                // Input past the cap is ignored, never cut later: the receiver logs what was sent (D-26).
                onValueChange = { if (it.length <= app.signsetu.pair.TEXT_CAP) text = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color(0xFF2B2118)),
                // SEC-05: nothing the subject writes is learned or suggested by the keyboard.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
                cursorBrush = SolidColor(Color(0xFF2B2118)),
                modifier = Modifier.fillMaxSize().padding(20.dp).focusRequester(focus),
            )
        }
        if (text.length > app.signsetu.pair.TEXT_CAP - 200) {
            Text(stringResource(R.string.write_count, text.length, app.signsetu.pair.TEXT_CAP), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.End))
        }
        pauseRow()
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BackButton(content, lang, onBack)
            PressableSurface(
                onClick = send,
                shape = RoundedCornerShape(50),
                color = if (text.isNotBlank()) answer.yes else Color(0xFFBFD8CB),
                modifier = Modifier.weight(1f).height(56.dp).named(R.string.stage_done, content.subjectText("done", lang) == null),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYes)
                    content.subjectText("done", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = answer.onYes, modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
        }
    }
}

/** Back to the question's answers, for a subject who opened writing or drawing by mistake. */
@Composable
private fun BackButton(content: ContentBundle, lang: String, onBack: () -> Unit) {
    val name = stringResource(R.string.stage_back)
    val stage = LocalStageColors.current
    PressableSurface(
        onClick = onBack,
        shape = CircleShape,
        color = stage.tool,
        border = BorderStroke(1.dp, stage.onTool.copy(alpha = 0.16f)),
        modifier = Modifier.size(56.dp).semantics { contentDescription = content.subjectText("back", lang) ?: name },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = stage.onTool) }
    }
}

/** A name for a control whose word is missing in the subject's language, so a screen reader still has one. */
@Composable
private fun Modifier.named(@StringRes id: Int, needed: Boolean = true): Modifier {
    if (!needed) return this
    val name = stringResource(id)
    return semantics { contentDescription = name }
}

@Composable
private fun DrawingPanel(content: ContentBundle, lang: String, onDone: (ByteArray) -> Unit, onBack: (() -> Unit)?, modifier: Modifier) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val ink = Color(0xFF2B2118)
    val paper = Color.White
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = paper,
            border = TileEdge,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = { strokes.add(DrawStroke(current)); current = emptyList() },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                (strokes + DrawStroke(current)).forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, ink, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            onBack?.let { BackButton(content, lang, it) }
            val stage = LocalStageColors.current
            PressableSurface(
                onClick = { strokes.clear() }, shape = RoundedCornerShape(50), color = stage.tool, border = BorderStroke(1.dp, stage.onTool.copy(alpha = 0.16f)),
                modifier = Modifier.weight(1f).height(56.dp).named(R.string.stage_clear, content.subjectText("clear", lang) == null),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = stage.onTool)
                    content.subjectText("clear", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
            val answer = LocalAnswerColors.current
            PressableSurface(
                onClick = { if (strokes.isNotEmpty()) onDone(renderPng(strokes, canvasSize, ink.toArgb(), paper.toArgb())) },
                shape = RoundedCornerShape(50),
                color = if (strokes.isNotEmpty()) answer.yes else Color(0xFFBFD8CB),
                modifier = Modifier.weight(1f).height(56.dp).named(R.string.stage_done, content.subjectText("done", lang) == null),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYes)
                    content.subjectText("done", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = answer.onYes, modifier = Modifier.padding(start = Spacing.sm)) }
                }
            }
        }
    }
}

private fun renderPng(strokes: List<DrawStroke>, size: IntSize, ink: Int, paper: Int): ByteArray {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(paper)
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 8 * density
        color = ink
    }
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        val path = android.graphics.Path().apply {
            moveTo(s.points[0].x, s.points[0].y)
            s.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
