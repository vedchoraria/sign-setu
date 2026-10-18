package app.signsetu.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import androidx.compose.runtime.saveable.rememberSaveable
import app.signsetu.camera.CAMERA_ASPECT
import app.signsetu.camera.CameraMirror
import app.signsetu.camera.Head
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.countOpenFingers
import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick
import app.signsetu.data.content.planText
import app.signsetu.data.content.questionKind
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignSegment
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * High-Empathy, Trauma-Informed Video Call Stage for ISL users.
 * Replaces technical, interrogative UI with a comforting, familiar 1-on-1 video call experience.
 *
 * Supports Zero-Tap Hands-Free Signing:
 * - Natural head gestures (Nod/Shake) auto-register answers.
 * - Palm stop automatically pauses.
 * - Pointing to pain locations registers location without touching the screen.
 * - Live video subtitles show avatar's questions and subject's recognized ISL signs.
 */
@Composable
fun VideoCallStage(
    ui: SessionUi,
    content: ContentBundle,
    tracker: LandmarkTracker?,
    cameraOn: Boolean,
    onCard: (String) -> Unit,
    onYesNo: (Boolean) -> Unit,
    onSignFrame: (LandmarkFrame) -> Unit,
    onStartSigning: () -> Unit,
    onDoneSigning: () -> Unit,
    onOpenExpressBoard: () -> Unit,
    onCue: (String) -> Unit,
    onNext: (() -> Unit)?,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = ui.session.subjectLang
    val pending = ui.pending
    val signing = ui.reading != null
    val haptic = LocalHapticFeedback.current

    // Video call timer
    var callSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            callSeconds++
        }
    }

    // PiP position
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }

    // Audio & video call states
    var micMuted by remember { mutableStateOf(false) }
    var videoMuted by remember { mutableStateOf(!cameraOn) }
    var pipExpanded by remember { mutableStateOf(false) }

    // Gesture proposals & auto-response
    var lastHandActiveMs by remember { mutableLongStateOf(0L) }
    var autoSignArmed by remember { mutableStateOf(true) }

    // Calming reassurance banner
    var recentReaction by remember { mutableStateOf<String?>(null) }
    var showBreathingGuide by remember { mutableStateOf(false) }
    LaunchedEffect(recentReaction) {
        if (recentReaction != null) {
            delay(4000L)
            recentReaction = null
        }
    }

    var showVictimSkeleton by rememberSaveable { mutableStateOf(false) }

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

    // Avatar voice / prompt text
    val promptText = (pending as? Pending.FreeText)?.let { it.caption ?: it.text } ?: item.caption ?: content.subjectText("hint_wait", lang) ?: "We are here with you."

    // Recognized signs stream
    val liveTranscript = remember(ui.reading) {
        ui.reading?.mapNotNull { it.word }?.filter { it != "?" }?.joinToString(" ")
    }

    // Hands-free finger counter for Age/Number questions
    val isNumberOrAgeQuestion = remember(pending) {
        val q = (pending as? Pending.Ask)?.question
        q?.answer == AnswerType.AGE || q?.answer == AnswerType.COUNT || q?.id in setOf("w_age", "p_siblings", "a_age")
    }
    var heldFingerCount by remember { mutableIntStateOf(-1) }
    var heldFingerSince by remember { mutableLongStateOf(0L) }

    // Frame analyzer wrapper with zero-tap auto-sign and finger detection
    val onFrameAnalyzed: (LandmarkFrame) -> Unit = { frame ->
        onSignFrame(frame)
        val now = frame.timestampMs
        val handsVisible = frame.leftHand != null || frame.rightHand != null
        if (handsVisible) {
            lastHandActiveMs = now
            if (!signing && autoSignArmed) {
                onStartSigning()
            }
        }

        // Automatic finger count ingestion for age/counts without opening keypads
        if (isNumberOrAgeQuestion && handsVisible) {
            val fingers = countOpenFingers(frame)
            if (fingers != null) {
                if (fingers == heldFingerCount) {
                    if (now - heldFingerSince > 450L && heldFingerSince > 0L) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val numCard = "n$fingers"
                        onCard(numCard)
                        recentReaction = "🖐 $fingers fingers held: recorded"
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

    val pulseAlpha = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    ).value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Deep calming midnight slate
    ) {
        // ==========================================
        // 1. MAIN PARTICIPANT: ISL Police Interpreter Avatar
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp, top = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Warm studio call backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                        ),
                    ),
            )

            // Animated Avatar Video Participant
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    character = Character.ADULT,
                    gesture = gesture,
                    playKey = pending,
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

        // ==========================================
        // 2. VIDEO CALL TOP HEADER BAR
        // ==========================================
        Surface(
            color = Color(0xCC0F172A),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Officer / Interpreter Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp),
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = pulseAlpha),
                            modifier = Modifier.size(20.dp),
                        ) {}
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ISL Police Support Call",
                                fontFamily = Baloo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                            )
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "%02d:%02d • Confidential & Safe".format(callSeconds / 60, callSeconds % 60),
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }

                // Safety & Encryption Pill & Calm Guide
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E3A8A).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                        modifier = Modifier.clickable { showBreathingGuide = !showBreathingGuide },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "🫁",
                                fontSize = 12.sp,
                            )
                            Text(
                                text = if (lang == "hi") "साँस लें" else "Breathe",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF93C5FD),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                            Text(
                                text = if (lang == "hi") "सुरक्षित लाइन" else "Secure Call",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0),
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. PICTURE-IN-PICTURE (PiP) SUBJECT CAMERA
        // ==========================================
        val pipWidth = if (pipExpanded) 180.dp else 130.dp
        val pipHeight = pipWidth * (16f / 9f)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 14.dp)
                .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                .size(pipWidth, pipHeight)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(
                        if (signing) 3.dp else 2.dp,
                        if (signing) Color(0xFF10B981) else Color(0x88FFFFFF),
                    ),
                    RoundedCornerShape(20.dp),
                )
                .shadow(8.dp, RoundedCornerShape(20.dp))
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
                    if (head == Head.Nod) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCue("nod")
                        onYesNo(true)
                        recentReaction = "✓ Yes (हाँ) acknowledged"
                    } else if (head == Head.Shake) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCue("shake")
                        onYesNo(false)
                        recentReaction = "✕ No (नहीं) acknowledged"
                    }
                },
                onPoint = { pointId ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCue("point")
                    onCard(pointId)
                    recentReaction = "Pointed: $pointId"
                },
                onStopPalm = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCue("palm")
                    onCard("stop")
                    recentReaction = "✋ Hold / Stop requested"
                },
                onShrug = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCue("shrug")
                    onCard("dont_know")
                    recentReaction = "🤷 Don't know recorded"
                },
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

            // Victim Immersion: Skeleton vs Natural Camera Mirror Toggle
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
                    text = if (showVictimSkeleton) "🦴 Skel: ON" else "👤 Natural",
                    fontSize = 9.sp,
                    color = if (showVictimSkeleton) Color(0xFFFDE68A) else Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            // "You (Signing)" badge on PiP
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (signing) Color(0xFF10B981) else Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            ) {
                Text(
                    text = if (signing) "🔴 Signing..." else "You (ISL)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // ==========================================
        // 4. LIVE SUBTITLES & REASSURANCE BANNER
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 98.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Live recognized signs caption
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
                            text = "Live ISL: $liveTranscript",
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF6EE7B7),
                        )
                    }
                }
            }

            // Gesture / action feedback pill
            AnimatedVisibility(visible = recentReaction != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xEE1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                ) {
                    Text(
                        text = recentReaction.orEmpty(),
                        fontFamily = Baloo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Interpreter question speech bubble / CC
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
                    Text(
                        text = if (lang == "hi") "इशारों में बताएं (हाथ हिलाएं या सिर हिलाएं)" else "Sign naturally (nod, shake or sign)",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // ==========================================
        // 5. CALL CONTROLS FLOATING DOCK (Bottom)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xEE1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Water Need Button
                CallActionButton(
                    icon = "water",
                    label = if (lang == "hi") "पानी" else "Water",
                    backgroundColor = Color(0xFF0369A1),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCard("water")
                        recentReaction = "Water requested 💧"
                    },
                )

                // 2. Food Need Button (Vital for distressed children)
                CallActionButton(
                    icon = "food",
                    label = if (lang == "hi") "खाना" else "Food",
                    backgroundColor = Color(0xFFD97706),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCard("food")
                        recentReaction = "Food / Hungry requested 🥪"
                    },
                )

                // 3. Direct YES Gesture Quick Touch
                CallActionButton(
                    icon = "yes",
                    label = if (lang == "hi") "हाँ" else "YES",
                    backgroundColor = Color(0xFF15803D),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onYesNo(true)
                        recentReaction = "✓ YES confirmed"
                    },
                )

                // 4. Direct NO Gesture Quick Touch
                CallActionButton(
                    icon = "no",
                    label = if (lang == "hi") "नहीं" else "NO",
                    backgroundColor = Color(0xFFB91C1C),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onYesNo(false)
                        recentReaction = "✕ NO confirmed"
                    },
                )

                // 5. Express Pictogram Board Opener
                CallActionButton(
                    icon = "express",
                    label = if (lang == "hi") "मदद" else "Help",
                    backgroundColor = Color(0xFF7C3AED),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenExpressBoard()
                    },
                )

                // 6. Emergency Hold / Pause Call Button
                CallActionButton(
                    icon = "stop",
                    label = if (lang == "hi") "रुकें" else "Hold",
                    backgroundColor = Color(0xFFDC2626),
                    iconTint = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCard("stop")
                        onPause()
                    },
                )
            }
        }

        // ==========================================
        // 6. TRAUMA GROUNDING & BREATHING GUIDE OVERLAY
        // ==========================================
        AnimatedVisibility(
            visible = showBreathingGuide,
            enter = fadeIn(tween(300)) + scaleIn(tween(300)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CalmingBreathingGuide(
                lang = lang,
                onDismiss = { showBreathingGuide = false },
            )
        }
    }
}

@Composable
private fun CalmingBreathingGuide(
    lang: String,
    onDismiss: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathCycle")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFA0F172A),
        border = BorderStroke(2.dp, Color(0xFF38BDF8)),
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(0.92f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (lang == "hi") "शांति और साँस (Grounding)" else "Safe Space Breathing",
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(vertical = 8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                    modifier = Modifier.size(150.dp * breathScale),
                ) {}
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                    modifier = Modifier.size(110.dp * breathScale),
                ) {}
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.size(72.dp),
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (breathScale > 1.05f) (if (lang == "hi") "छोड़ें" else "Exhale") else (if (lang == "hi") "साँस लें" else "Inhale"),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Text(
                text = if (lang == "hi")
                    "आप यहाँ पूरी तरह सुरक्षित हैं। धीरे-धीरे साँस लें और शांत महसूस करें।"
                else
                    "You are in a safe space. Breathe with the circle at your own pace.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 14.sp,
                color = Color(0xFFE2E8F0),
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0369A1),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = if (lang == "hi") "मैं ठीक हूँ, आगे बढ़ें" else "I'm Ready to Continue",
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: String,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            modifier = Modifier.size(50.dp),
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    "yes" -> Icon(Icons.Filled.Check, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
                    "no" -> Icon(Icons.Filled.Close, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
                    "express" -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    "stop" -> CardImage("stop", Modifier.size(28.dp))
                    else -> CardImage(icon, Modifier.size(28.dp))
                }
            }
        }
        Text(
            text = label,
            fontFamily = Baloo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E8F0),
        )
    }
}
