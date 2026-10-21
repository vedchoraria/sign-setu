package app.signsetu.ui.session

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.countOpenFingers
import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.planText
import app.signsetu.data.content.questionKind
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The stage as a one-to-one video call: the character is the caller, the subject's camera a small window. Head
 * gestures, a palm, a shrug, pointing and held-up fingers only propose an answer; the subject's tap answers.
 */
@Composable
fun VideoCallStage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onSignFrame: (LandmarkFrame) -> Unit,
    onStartSigning: () -> Unit,
    onOpenExpressBoard: () -> Unit,
    onCue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = ui.session.subjectLang
    val pending = ui.pending
    val signing = ui.reading != null
    val haptic = LocalHapticFeedback.current

    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var pipExpanded by remember { mutableStateOf(false) }

    // A string resource and its optional argument, resolved where it is drawn so a language change is followed.
    var recentReaction by remember { mutableStateOf<Pair<Int, Any?>?>(null) }
    var showBreathingGuide by remember { mutableStateOf(false) }
    LaunchedEffect(recentReaction) {
        if (recentReaction != null) {
            delay(4000L)
            recentReaction = null
        }
    }
    fun react(@StringRes reaction: Int, arg: Any? = null, feel: HapticFeedbackType = HapticFeedbackType.LongPress) {
        haptic.performHapticFeedback(feel)
        recentReaction = reaction to arg
    }

    var showVictimSkeleton by rememberSaveable { mutableStateOf(false) }

    // Left alone, the stage signs its own buttons one by one; a touch pauses it and the next quiet spell resumes there.
    var guideStep by rememberSaveable { mutableIntStateOf(0) }
    var guiding by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(touches, signing) {
        guiding = false
        if (signing) return@LaunchedEffect
        delay(GUIDE_IDLE_MS)
        guiding = true
        while (guideStep < GuideSteps.size) {
            delay(GUIDE_STEP_MS)
            guideStep++
        }
        guideStep = 0
        guiding = false
    }
    val guided = GuideSteps.getOrNull(guideStep)?.takeIf { guiding }

    // The card a gesture proposed and the cue it came from; the cue is logged only if the subject taps it.
    var proposal by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(pending) { proposal = null }
    fun propose(card: String, cue: String?) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        proposal = card to cue
    }
    fun answer(card: String, cue: String? = null) {
        cue?.let(onCue)
        when {
            card == "yes" || card == "no" -> onYesNo(card == "yes")
            else -> onCard(card)
        }
        when {
            card == "yes" -> react(R.string.call_r_yes)
            card == "no" -> react(R.string.call_r_no)
            card == "stop" -> react(R.string.call_r_stop)
            card == "dont_know" -> react(R.string.call_r_unsure, feel = HapticFeedbackType.TextHandleMove)
            card == "water" -> react(R.string.call_r_water)
            card == "food" -> react(R.string.call_r_food)
            card.matches(FINGERS) -> react(R.string.call_r_fingers, card.drop(1).toInt())
            else -> react(R.string.call_r_point, cardLabel(content, card, lang), HapticFeedbackType.TextHandleMove)
        }
        proposal = null
    }

    val item = remember(pending, lang) { describeItem(pending, content, lang) }
    val plan = remember(pending, lang) {
        (pending as? Pending.FreeText)?.let { content.planText(it.text, it.modes) }
    }
    val gesture = remember(pending, plan) {
        when (pending) {
            is Pending.Ask -> content.gestureFor(pending.question)
            is Pending.PhraseAsk -> content.gestureFor(pending.phrase, pending.slot)
            is Pending.FreeText -> plan?.gesture ?: content.gestures["ask"] ?: content.gestures["namaste"]
            else -> content.gestures["namaste"]
        }
    }
    val shownGesture = guided?.let { content.gestures[it.second] } ?: gesture
    val promptText = (pending as? Pending.FreeText)?.let { it.caption ?: it.text } ?: item.caption ?: content.subjectText("hint_wait", lang) ?: "We are here with you."
    val liveTranscript = remember(ui.reading) {
        ui.reading?.mapNotNull { it.word }?.filter { it != "?" }?.joinToString(" ")
    }

    // Held-up fingers answer an age or count question without opening a keypad.
    val isNumberOrAgeQuestion = remember(pending) {
        val q = (pending as? Pending.Ask)?.question
        q?.answer == AnswerType.AGE || q?.answer == AnswerType.COUNT || q?.id in setOf("w_age", "p_siblings", "a_age")
    }
    var heldFingerCount by remember { mutableIntStateOf(-1) }
    var heldFingerSince by remember { mutableLongStateOf(0L) }

    val onFrameAnalyzed: (LandmarkFrame) -> Unit = { frame ->
        onSignFrame(frame)
        val now = frame.timestampMs
        val handsVisible = frame.leftHand != null || frame.rightHand != null
        if (handsVisible && !signing) onStartSigning()
        if (isNumberOrAgeQuestion && handsVisible) {
            val fingers = countOpenFingers(frame)
            if (fingers != null) {
                if (fingers == heldFingerCount) {
                    if (now - heldFingerSince > 450L && heldFingerSince > 0L) {
                        propose("n$fingers", null)
                        // Far in the future, so the same held count is sent once.
                        heldFingerSince = Long.MAX_VALUE / 2
                    }
                } else {
                    heldFingerCount = fingers
                    heldFingerSince = now
                }
            } else {
                heldFingerCount = -1
                heldFingerSince = 0L
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) touches++
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp, top = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    character = characterFor(ui),
                    gesture = shownGesture,
                    playKey = guided?.let { "guide:$guideStep" } ?: pending,
                    speed = 0.9f,
                    idle = !signing,
                    glance = true,
                    question = if (pending is Pending.Ask || pending is Pending.PhraseAsk) questionKind(promptText) else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(AVATAR_ASPECT),
                )
            }
        }

        CallHeader(
            onBreathe = { showBreathingGuide = !showBreathingGuide },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        val pipWidth = if (pipExpanded) 180.dp else 130.dp
        val pipShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 100.dp, end = 14.dp)
                .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                .size(pipWidth, pipWidth * (16f / 9f))
                .clip(pipShape)
                .border(
                    BorderStroke(
                        if (signing) 3.dp else 2.dp,
                        if (signing) Color(0xFF10B981) else Color(0x88FFFFFF),
                    ),
                    pipShape,
                )
                .shadow(8.dp, pipShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pipOffsetX += dragAmount.x
                        pipOffsetY += dragAmount.y
                    }
                }
                .clickable { pipExpanded = !pipExpanded },
        ) {
            CameraMirror(
                tracker = tracker,
                modifier = Modifier.fillMaxSize(),
                showSkeleton = showVictimSkeleton,
                onFrame = onFrameAnalyzed,
                onHead = { head ->
                    when (head) {
                        Head.Nod -> propose("yes", "nod")
                        Head.Shake -> propose("no", "shake")
                        else -> Unit
                    }
                },
                onPoint = { pointId -> propose(pointId, "point") },
                onStopPalm = { propose("stop", "palm") },
                onShrug = { propose("dont_know", "shrug") },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (showVictimSkeleton) Color(0xDD0F172A) else Color(0x99000000),
                border = BorderStroke(1.dp, if (showVictimSkeleton) Color(0xFFFBBF24) else Color(0x44FFFFFF)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clickable { showVictimSkeleton = !showVictimSkeleton },
            ) {
                Text(
                    text = stringResource(if (showVictimSkeleton) R.string.call_skeleton_on else R.string.call_skeleton_off),
                    fontSize = 9.sp,
                    color = if (showVictimSkeleton) Color(0xFFFDE68A) else Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (signing) Color(0xFF10B981) else Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                Text(
                    text = stringResource(if (signing) R.string.call_signing else R.string.call_you),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 124.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!liveTranscript.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xEE064E3B),
                    border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("👋", fontSize = 16.sp)
                        Text(
                            text = stringResource(R.string.call_live, liveTranscript),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF6EE7B7),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = recentReaction != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xEE1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                ) {
                    Text(
                        text = recentReaction?.let { (id, arg) -> if (arg == null) stringResource(id) else stringResource(id, arg) }.orEmpty(),
                        fontFamily = Baloo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xDD0F172A),
                border = BorderStroke(1.5.dp, Color(0x44FFFFFF)),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = promptText,
                        fontFamily = Baloo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    // A young child is not made to read instructions; the character and the guide show them.
                    if (!ui.child) Text(
                        text = stringResource(R.string.call_sign_hint),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        proposal?.let { (card, cue) ->
            val label = cardLabel(content, card, lang)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0E7490),
                border = BorderStroke(2.dp, Color(0xFF67E8F9)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp)
                    .clickable { answer(card, cue) }
                    .semantics { role = Role.Button; contentDescription = label },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (content.hasCardImage(card)) CardImage(card, Modifier.size(36.dp))
                    Text(label, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    content.subjectText("is_this_it", lang)?.let { Text(it, fontSize = 14.sp, color = Color(0xFFCFFAFE)) }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xEE1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallActionButton("water", cardLabel(content, "water", lang), Color(0xFF0369A1), highlight = guided?.first == "water") {
                    answer("water")
                }
                CallActionButton("food", cardLabel(content, "food", lang), Color(0xFFD97706), highlight = guided?.first == "food") {
                    answer("food")
                }
                CallActionButton("yes", cardLabel(content, "yes", lang), Color(0xFF15803D), highlight = guided?.first == "yes") {
                    answer("yes")
                }
                CallActionButton("no", cardLabel(content, "no", lang), Color(0xFFB91C1C), highlight = guided?.first == "no") {
                    answer("no")
                }
                CallActionButton("express", cardLabel(content, "help", lang), Color(0xFF7C3AED), highlight = guided?.first == "express") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenExpressBoard()
                }
                CallActionButton("stop", cardLabel(content, "stop", lang), Color(0xFFDC2626), highlight = guided?.first == "stop") {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    answer("stop")
                }
            }
        }

        AnimatedVisibility(
            visible = showBreathingGuide,
            enter = fadeIn(tween(300)) + scaleIn(tween(300)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CalmingBreathingGuide(onDismiss = { showBreathingGuide = false })
        }
    }
}

/** Each stage button with the sign the character makes while it is pointed out. */
private val GuideSteps = listOf("yes" to "yes.nod", "no" to "no.shake", "water" to "water.cup", "food" to "eat.fingers", "express" to "show_me", "stop" to "show_me")
private const val GUIDE_IDLE_MS = 20_000L
private const val GUIDE_STEP_MS = 3_000L

private val FINGERS = Regex("n\\d+")

private fun cardLabel(content: ContentBundle, card: String, lang: String): String =
    content.cards[card]?.label?.let { it[lang] ?: it["en"] } ?: card.removePrefix("n").takeIf { card.matches(FINGERS) } ?: card
