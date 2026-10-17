package app.signsetu.ui.pair

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalStageColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.AppContainer
import app.signsetu.DeviceGuard
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.forSignLanguage
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.pair.AnswerBody
import app.signsetu.pair.ByeReason
import app.signsetu.pair.LinkState
import app.signsetu.pair.StageLink
import app.signsetu.pair.StageTransports
import app.signsetu.pair.ToOfficer
import app.signsetu.pair.b64
import app.signsetu.pair.fitDrawing
import app.signsetu.pair.resolve
import app.signsetu.ui.ImmersiveWhile
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.session.SmallAnswer
import app.signsetu.ui.session.StripAnswer
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android 10 without a fingerprint can only confirm the officer by unpinning first, so it cannot be a stage. */
fun canBeStage(context: Context, container: AppContainer): Boolean =
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure &&
        container.pairTransport != null &&
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS)

private fun bluetoothOn(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

/** On the lock screen: makes this device the subject's screen. Each tap shows the checklist again. */
@Composable
fun UseAsStageButton(container: AppContainer) {
    if (container.pairTransport == null) return
    val context = LocalContext.current
    var checklist by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (bluetoothOn(context)) container.startStage() }
    val proceed = { if (bluetoothOn(context)) container.startStage() else enable.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    // The stage never passes the start screen, where the camera is otherwise asked for.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { proceed() }
    val link = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) camera.launch(Manifest.permission.CAMERA)
    }
    TextButton(onClick = { if (canBeStage(context, container)) checklist = true else unsupported = true }) { Text(stringResource(R.string.stage_use)) }
    if (unsupported) Text(stringResource(R.string.stage_unsupported), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    if (checklist) {
        GuardedAlertDialog(
            onDismissRequest = { checklist = false },
            text = { Text(stringResource(R.string.stage_checklist)) },
            confirmButton = {
                TextButton(onClick = {
                    checklist = false
                    val perms = StageTransports.permissions(context)
                    if (perms.isEmpty()) camera.launch(Manifest.permission.CAMERA) else link.launch(perms)
                }) { Text(stringResource(R.string.stage_continue)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { checklist = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text(stringResource(R.string.stage_checklist_open)) }
                }
            },
        )
    }
}

/**
 * The stage device (D-26): it shows only what the officer phone sends, answers back, and stores nothing. Once Live it
 * stays pinned with no local exit; only a Bye from the officer, a lost link, or this device's own credential ends it.
 */
@Composable
fun StageDeviceScreen(link: StageLink, content: ContentBundle, container: AppContainer, guard: DeviceGuard) {
    val state by link.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var everLive by remember { mutableStateOf(false) }
    val title = stringResource(R.string.return_prompt_title)
    val subtitle = stringResource(R.string.return_prompt_subtitle)
    val cancel = stringResource(R.string.cancel)

    LaunchedEffect(state) {
        if (state == LinkState.Live && !everLive) {
            // Pinning after the first frame, as on a single phone (NFR-02).
            withFrameNanos { }
            guard.pin()
            everLive = true
            container.settings.setStageDevice()
        }
        if (state is LinkState.Ended) guard.pin()
    }
    ImmersiveWhile(activity, everLive)
    // Back is swallowed once pinned: the stage has no local exit (D-26); Stop stays on the item and the officer ends it.
    BackHandler(enabled = everLive) { }
    val awake = everLive && state !is LinkState.Ended
    DisposableEffect(activity, awake) {
        val window = activity?.window
        if (awake && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // A power press brings back the stage, not the keyguard; the stage holds only the current item.
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.setShowWhenLocked(false)
            activity?.setTurnScreenOn(false)
        }
    }
    // A non-owner app gets no callback when the system gesture unpins it, so the state is polled.
    LaunchedEffect(everLive) {
        if (!everLive) return@LaunchedEffect
        val am = context.getSystemService(ActivityManager::class.java)
        var seenPinned = false
        while (true) {
            delay(1_000)
            val pinned = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            if (pinned) seenPinned = true
            if (!pinned && seenPinned && link.state.value !is LinkState.Ended) link.end(ByeReason.UNPINNED)
            if (!pinned && link.state.value is LinkState.Ended) seenPinned = false
        }
    }
    val exit = {
        guard.confirmPinned(title, subtitle, cancel) {
            link.end(ByeReason.EXIT)
            guard.unpin()
            activity?.finishAndRemoveTask()
            // Process exit is the real wipe of anything left on the heap (SEC-69).
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 500)
        }
    }

    SubjectTheme {
        when (val s = state) {
            LinkState.Idle, LinkState.Connecting, is LinkState.Failed ->
                Waiting(container.pairTransport?.localName() ?: Build.MODEL) { container.endLink(ByeReason.CANCEL) }
            is LinkState.Code -> CodeScreen(s.code.orEmpty(), s.check, link::cancelCode, link::match)
            is LinkState.Ended -> Ended(exit)
            else -> LiveStage(link, content, s, exit, scope)
        }
    }
}

@Composable
internal fun Waiting(name: String, onCancel: () -> Unit) = Centered {
    Text(stringResource(R.string.stage_waiting, name), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    CircularProgressIndicator(color = LocalContentColor.current)
    StageOutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
internal fun CodeScreen(code: String, check: String?, onCancel: () -> Unit, onMatch: () -> Unit) = Centered {
    CodeBoxes(code)
    Text(stringResource(R.string.stage_code), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    check?.let { CheckValue(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StageOutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onMatch, enabled = check != null, shape = MaterialTheme.shapes.medium, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.stage_match)) }
    }
}

@Composable
internal fun Ended(onExit: () -> Unit) = Centered {
    Text(stringResource(R.string.stage_handback), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    TextButton(onClick = onExit, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) { Text(stringResource(R.string.stage_officer_unlock)) }
}

@Composable
internal fun Break(text: String) = Centered {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
}

/** The value both screens show after the officer's phone accepts the code; Match only when they are the same. */
@Composable
fun CheckValue(check: String) {
    Text(stringResource(R.string.pair_check_label), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(
        check,
        fontFamily = FontFamily.Monospace,
        fontSize = 40.sp,
        modifier = Modifier.semantics { contentDescription = check.toCharArray().joinToString(" ") },
    )
}

@Composable
private fun LiveStage(link: StageLink, baseContent: ContentBundle, state: LinkState, exit: () -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    val show by link.view.collectAsStateWithLifecycle()
    val unacked by link.unacked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = show?.view
    val lang = view?.signLanguage?.let { n -> SignLanguage.entries.firstOrNull { it.name == n } } ?: SignLanguage.ISL
    val content = remember(baseContent, lang) { baseContent.forSignLanguage(lang) }
    val itemId = show?.itemId
    // The item answered last; Stop on an idle screen still belongs somewhere in the record.
    val lastItem = remember { LongArray(1) }
    if (itemId != null) lastItem[0] = itemId
    var breakFor by remember { mutableStateOf<Long?>(null) }
    // The break ends only when the officer shows something new.
    LaunchedEffect(itemId) { if (breakFor != null && itemId != null && itemId != breakFor) breakFor = null }
    val pending = remember(view?.item) { view?.item?.resolve(content) }
    var sentFor by remember { mutableStateOf(emptySet<Long>()) }
    val send = { body: AnswerBody ->
        val id = itemId ?: lastItem[0]
        link.answer(id, body)
        sentFor = sentFor + id
    }

    LaunchedEffect(itemId, pending) {
        if (view?.item != null && pending == null) link.status(ToOfficer.Status(0, false, true, view.cameraOn, error = "unknown"))
        if (itemId != null && pending != null) {
            withFrameNanos { }
            link.displayed(itemId)
        }
    }
    BatteryStatus(link, view?.cameraOn == true)

    if (view == null) {
        Centered { Text(stringResource(R.string.stage_hold), style = MaterialTheme.typography.headlineSmall) }
        return
    }
    if (breakFor != null) {
        Break(content.subjectText("break", view.lang) ?: stringResource(R.string.stage_break))
        return
    }
    val covered = state != LinkState.Live
    var answeredCard by remember(itemId) { mutableStateOf<String?>(null) }
    var answeredYes by remember(itemId) { mutableStateOf<Boolean?>(null) }
    var chosenLanguage by remember(itemId) { mutableStateOf<String?>(null) }
    val profile = runCatching { Profile.valueOf(view.profile) }.getOrDefault(Profile.NOT_SURE)
    val ui = SessionUi(
        Session("stage", 0L, profile, view.lang), "safety", false, pending, Mode.SHOW, view.rotated,
        answeredCard, answeredYes, chosenLanguage, emptyList(), null, emptyMap(),
    )
    val onCard = { id: String ->
        answeredCard = id
        answeredYes = null
        send(AnswerBody.Card(id))
        if (id == "stop") breakFor = itemId ?: -1L
    }
    Box(Modifier.fillMaxSize()) {
        // Stays composed under the cover, so a drawing or written answer survives a reconnect.
        key(itemId) {
            Box(if (covered) Modifier.clearAndSetSemantics { } else Modifier) {
                Stage(
                    ui = ui,
                    content = content,
                    // No landmark analysis here: guesses are not sent in this version and the mirror stays plain.
                    tracker = null,
                    cameraOn = view.cameraOn && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    largeCaptions = view.largeCaptions,
                    gestureSpeed = view.gestureSpeed,
                    onCard = onCard,
                    onYesNo = { answeredYes = it; answeredCard = null; send(AnswerBody.YesNo(it)) },
                    onLanguage = { chosenLanguage = it; send(AnswerBody.Language(it)) },
                    onDrawing = { png -> scope.launch { withContext(Dispatchers.Default) { fitDrawing(png) }?.let { send(AnswerBody.Drawing(it.b64())) } } },
                    onWritten = { send(AnswerBody.Text(it.trim())) },
                    onSignFrame = {},
                    onStartSigning = {},
                    onDoneSigning = {},
                    onOfficer = null,
                    onNextRule = { send(AnswerBody.Next) },
                    onGiven = { g -> if (g != null) send(AnswerBody.Given(g.question, g.method.name, g.confirmed, g.value, g.picks)) },
                )
            }
        }
        val id = itemId ?: lastItem[0]
        if (!covered && id in sentFor) {
            Text(
                stringResource(if (unacked.any { it.itemId == id }) R.string.stage_sending else R.string.stage_sent),
                style = MaterialTheme.typography.labelMedium,
                color = LocalStageColors.current.title,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(Spacing.md),
            )
        }
        if (covered) Cover(state, content, view.lang, onStop = { onCard("stop") }, onUnsure = { onCard("dont_know") }, onExit = exit)
    }
}

/** Lost or Hold: the item is hidden; Stop and "I don't know" still work and are sent once the link is back. */
@Composable
internal fun Cover(state: LinkState, content: ContentBundle, lang: String, onStop: () -> Unit, onUnsure: () -> Unit, onExit: () -> Unit) {
    val stage = LocalStageColors.current
    Surface(color = stage.skyBottom, contentColor = stage.title, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (state == LinkState.Hold) R.string.stage_hold else R.string.stage_reconnecting),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (state == LinkState.Lost) CircularProgressIndicator(color = stage.title)
            // The same pictures the subject has used on the stage, not bare words or symbols.
            val answer = LocalAnswerColors.current
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                StripAnswer("dont_know", content.subjectText("dont_know", lang), stringResource(R.string.stage_dont_know), false, onUnsure, Modifier.weight(1f))
                SmallAnswer("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, false, onStop, Modifier.weight(1f), height = 76.dp)
            }
            TextButton(onClick = onExit) { Text(stringResource(R.string.stage_officer_unlock), color = stage.title.copy(alpha = 0.8f)) }
        }
    }
}

/** Battery and charging go to the officer on a change of at least 1% (the broadcast is sticky and frequent). */
@Composable
private fun BatteryStatus(link: StageLink, cameraOn: Boolean) {
    val context = LocalContext.current
    DisposableEffect(link, cameraOn) {
        var last: Pair<Int, Boolean>? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val now = level * 100 / scale to (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
                if (level < 0 || now == last) return
                last = now
                val pinned = c.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
                link.status(ToOfficer.Status(now.first, now.second, pinned, cameraOn))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/** The pairing code, one digit per box in two groups of three, read out digit by digit. */
@Composable
private fun CodeBoxes(code: String) {
    val stage = LocalStageColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.clearAndSetSemantics { contentDescription = code.toCharArray().joinToString(" ") },
    ) {
        code.forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) Spacer(Modifier.width(Spacing.sm))
            Surface(shape = MaterialTheme.shapes.medium, color = stage.bubble, contentColor = stage.onBubble, border = BorderStroke(1.dp, stage.onBubble.copy(alpha = 0.2f)), modifier = Modifier.size(48.dp, 56.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(c.toString(), fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 28.sp) }
            }
        }
    }
}

@Composable
private fun StageOutlinedButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val ink = LocalContentColor.current
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
        border = BorderStroke(1.dp, ink.copy(alpha = 0.5f)),
        modifier = Modifier.height(48.dp),
        content = content,
    )
}

@Composable
private fun Centered(body: @Composable () -> Unit) {
    val stage = LocalStageColors.current
    Surface(color = stage.skyBottom, contentColor = stage.title, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(horizontal = Spacing.lg, vertical = Spacing.xl).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { body() }
    }
}
