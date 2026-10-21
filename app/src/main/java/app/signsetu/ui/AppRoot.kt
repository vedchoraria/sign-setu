package app.signsetu.ui

import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.ExperimentalComposeUiApi
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import app.signsetu.assistant.ModelStore
import app.signsetu.assistant.Assistant
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import app.signsetu.assistant.Notice
import androidx.core.net.toUri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import android.content.ClipData
import android.content.Context
import app.signsetu.ui.components.LocalCloudAi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.signsetu.ui.theme.Spacing
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.settings.SettingsScreen
import app.signsetu.ui.start.FirstRunScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.SideEffect
import androidx.core.content.FileProvider
import app.signsetu.export.SessionReport
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import app.signsetu.ui.components.ScreenSkeleton
import app.signsetu.AppContainer
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.forSignLanguage
import app.signsetu.ui.session.signLanguage
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.HandoverEffects
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Conv
import app.signsetu.ui.session.OfficerPane
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.Stage
import app.signsetu.camera.LandmarkTracker
import app.signsetu.sign.SignReader
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.signsetu.ui.start.StartScreen
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.serialization.Serializable
import app.signsetu.ui.components.GuardedAlertDialog
import android.view.WindowManager
import androidx.compose.runtime.remember
import app.signsetu.pair.ByeReason
import app.signsetu.pair.LinkRole
import app.signsetu.pair.LinkState
import app.signsetu.pair.rememberPairedStage
import app.signsetu.ui.pair.SecondScreenSheet
import app.signsetu.ui.pair.StageDeviceScreen
import app.signsetu.ui.pair.UseAsStageButton

@Serializable data object StartKey : NavKey
@Serializable data class SessionKey(val id: String) : NavKey
@Serializable data object SettingsKey : NavKey

private const val SANDES_PACKAGE = "in.nic.gimkerala"

private fun noticeText(context: Context, n: Notice) = context.getString(when (n) {
    Notice.FALLBACK_OFFLINE -> R.string.notice_offline
    Notice.FALLBACK_ERROR -> R.string.notice_gateway_error
    Notice.REVOKED -> R.string.notice_revoked
    Notice.BACK_ONLINE -> R.string.notice_back_online
    Notice.ENROLLED -> R.string.notice_enrolled
    Notice.ENROLL_FAILED -> R.string.notice_enroll_failed
})

@Composable
fun AppRoot(container: AppContainer) {
    val repo by container.sessions.collectAsStateWithLifecycle()
    val content by produceState<ContentBundle?>(null) { value = container.content.load() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val link by container.link.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    DisposableEffect(container) {
        container.cloudAi.onNotice = { n ->
            context.mainExecutor.execute { Toast.makeText(context, noticeText(context, n), Toast.LENGTH_LONG).show() }
        }
        onDispose { container.cloudAi.onNotice = {} }
    }

    CompositionLocalProvider(LocalContentRepository provides container.content, LocalCloudAi provides container.cloudAi) { NoPersonalisedLearning {
        SignSetuTheme {
            val r = repo
            val c = content
            val s = settings
            val stage = link?.takeIf { it.role == LinkRole.STAGE }
            when {
                s == null -> ScreenSkeleton()
                !s.firstRunDone -> FirstRunScreen(c?.gestures.orEmpty(), onDone = { scope.launch { container.settings.setFirstRunDone() } })
                // A stage device never opens the session database or the navigation (D-26).
                stage != null && c != null -> StageDeviceScreen(stage, c, container)
                // No credential prompt: LockScreen opens the database itself and only shows if that fails.
                r == null -> LockScreen(
                    onUnlock = { onFailed ->
                        container.stageGuard.onStage(false)
                        container.unlockAsync(onFailed)
                    },
                    stageButton = { UseAsStageButton(container) },
                )
                c == null -> ScreenSkeleton()
                else -> Navigation(r, c, s, container)
            }
            val keyReset by container.keyReset.collectAsStateWithLifecycle()
            if (keyReset && r != null) {
                GuardedAlertDialog(
                    onDismissRequest = container::acknowledgeKeyReset,
                    title = { Text(stringResource(R.string.key_reset_title)) },
                    text = { Text(stringResource(R.string.key_reset_body)) },
                    confirmButton = { TextButton(onClick = container::acknowledgeKeyReset) { Text(stringResource(R.string.ok)) } },
                )
            }
            val downgraded by container.downgraded.collectAsStateWithLifecycle()
            if (downgraded && r != null) {
                GuardedAlertDialog(
                    onDismissRequest = container::acknowledgeDowngrade,
                    title = { Text(stringResource(R.string.downgrade_reset_title)) },
                    text = { Text(stringResource(R.string.downgrade_reset_body)) },
                    confirmButton = { TextButton(onClick = container::acknowledgeDowngrade) { Text(stringResource(R.string.ok)) } },
                )
            }
        }
    } }
}

/**
 * SEC-05: every text field in the app asks the keyboard not to learn from what is typed, since it is about a subject.
 * Compose has no keyboard option for this, so the flag is added to each input connection as it is created.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoPersonalisedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                    request.createInputConnection(outAttributes).also {
                        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
            })
        },
        content = content,
    )
}

@Composable
private fun Navigation(repo: SessionRepository, content: ContentBundle, settings: AppSettings, container: AppContainer) {
    val backStack = rememberNavBackStack(StartKey)
    val scope = rememberCoroutineScope()
    var pairing by remember { mutableStateOf(false) }
    val onSecondScreen = { pairing = true }.takeIf { container.pairTransport != null }
    // Each reads and hashes model files, so none is built on the main thread; null until ready, as with no model.
    val tracker by produceState<LandmarkTracker?>(null) { value = withContext(Dispatchers.IO) { container.landmarkTracker } }
    val reader by produceState<SignReader?>(null) { value = withContext(Dispatchers.IO) { container.signReader } }
    val modelState by produceState<ModelStore.State?>(null, backStack.size) { value = withContext(Dispatchers.IO) { container.models.state() } }
    if (pairing) SecondScreenSheet(container, onDismiss = { pairing = false })
    NavDisplay(
        backStack = backStack,
        transitionSpec = { fadeIn(tween(220, 60)) + slideInHorizontally(tween(260)) { it / 12 } togetherWith fadeOut(tween(120)) },
        popTransitionSpec = { fadeIn(tween(220, 60)) + slideInHorizontally(tween(260)) { -it / 12 } togetherWith fadeOut(tween(120)) },
        predictivePopTransitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120)) },
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<StartKey> {
                StartScreen(
                    repo = repo,
                    content = content,
                    expired = container.buildExpired,
                    rooted = container.deviceLooksRooted,
                    cameraWanted = settings.cameraEnabled,
                    onOpenSession = { id -> backStack.add(SessionKey(id)) },
                    onSettings = { backStack.add(SettingsKey) },
                    onSecondScreen = onSecondScreen,
                )
            }
            entry<SettingsKey> {
                SettingsScreen(container.settings, container.models, onBack = { backStack.removeLastOrNull() }, onClearAll = container::clearAllData)
            }
            entry<SessionKey> { key ->
                SessionRoute(
                    key.id, repo, content, settings, container.report, tracker, reader,
                    onFavourite = { id -> scope.launch { container.settings.toggleFavourite(id) } },
                    assistant = container.assistant,
                    onClosed = { backStack.removeLastOrNull() },
                    container = container,
                    onSecondScreen = onSecondScreen,
                )
            }
        },
    )
}

@Composable
internal fun SessionRoute(
    sessionId: String,
    repo: SessionRepository,
    content: ContentBundle,
    settings: AppSettings,
    report: SessionReport,
    tracker: LandmarkTracker?,
    reader: SignReader?,
    assistant: Assistant?,
    onFavourite: (String) -> Unit,
    onClosed: () -> Unit,
    container: AppContainer,
    onSecondScreen: (() -> Unit)?,
) {
    val officerLang = if (LocalConfiguration.current.locales[0].language == "hi") "hi" else "en"
    val vm: SessionViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory { initializer { SessionViewModel(sessionId, repo, content, officerLang, report, reader) } },
    )
    LaunchedEffect(tracker) { tracker?.let { withContext(Dispatchers.Default) { it.warm() } } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val signLanguage = ui?.signLanguage ?: SignLanguage.ISL
    val langReader by produceState(reader.takeIf { signLanguage == SignLanguage.ISL }, signLanguage, reader) {
        value = if (signLanguage == SignLanguage.ISL) reader else withContext(Dispatchers.IO) { container.signReader(signLanguage) }
    }
    SideEffect { vm.reader = langReader }
    val signContent = remember(content, signLanguage) { content.forSignLanguage(signLanguage) }
    val conv by vm.auto.conv.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val cameraOn = rememberSaveable { settings.cameraEnabled }
    var sandesHandoffContact by rememberSaveable { mutableStateOf<String?>(null) }
    // D-26: with a second screen the subject's stage is there, and this phone stays the officer's.
    val link by container.link.collectAsStateWithLifecycle()
    val officerLink = link?.takeIf { it.role == LinkRole.OFFICER }
    val linkState = officerLink?.state?.collectAsStateWithLifecycle()?.value
    val paired = linkState == LinkState.Live || linkState == LinkState.Lost || linkState == LinkState.Hold || linkState is LinkState.Ended
    // A recreation for dark mode, font scale or locale keeps the session; only really leaving it ends the link.
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true && container.link.value?.role == LinkRole.OFFICER) container.endLink() } }
    KeepScreenOnWhile(activity, linkState == LinkState.Live)
    val state = ui ?: return
    val pane = officerLink?.takeIf { paired }?.let {
        rememberPairedStage(vm, it, settings, cameraOn, officerLang, onFallback = {
            vm.fallbackToLocal()
            container.endLink(ByeReason.FALLBACK)
        })
    }
    val backToOfficer: () -> Unit = vm::returnToOfficer

    val localStage = state.mode == Mode.SHOW && !paired
    ImmersiveWhile(activity, localStage)
    // The assistant's buffers would sit beside the camera and the landmark graph on a small phone.
    LaunchedEffect(localStage || state.signWindow != null) { if (localStage || state.signWindow != null) container.models.release() }
    // With a second screen the officer keeps this phone, so tilt and the volume keys do not hand it over.
    if (!paired) HandoverEffects(state, vm, settings.tiltToShow, settings.volumeKeys, container.hardKeys, container.stageGuard, backToOfficer)
    // The app locking takes this screen away: landmarks go with it, though not on a mere configuration change.
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) vm.wipeSigns() } }

    when (if (paired) Mode.OFFICER else state.mode) {
        Mode.OFFICER -> OfficerPane(
            ui = state,
            vm = vm,
            officerLang = officerLang,
            favourites = settings.favourites,
            onFavourite = onFavourite,
            assistant = assistant,
            canReadSigns = tracker != null && !paired,
            paired = pane,
            onSecondScreen = onSecondScreen,
            allowAuto = settings.allowAuto,
            islReorder = settings.islReorder,
            onCallInterpreter = if (settings.interpreterConfigured) {
                {
                    if (settings.interpreterApp == InterpreterApp.SANDES) {
                        sandesHandoffContact = settings.interpreterContact
                    } else {
                        activity?.let { callInterpreter(it, settings) }
                    }
                }
            } else {
                null
            },
            onShare = { file ->
                activity?.let { shareReport(it, file) }
            },
            onEndSession = {
                vm.endSession {
                    officerLink?.let { container.endLink() }
                    Toast.makeText(activity ?: return@endSession onClosed(), R.string.session_finished, Toast.LENGTH_LONG).show()
                    onClosed()
                }
            },
        )
        Mode.SHOW -> {
            BackHandler(onBack = backToOfficer)
            SubjectTheme {
                Stage(
                    ui = state,
                    content = signContent,
                    tracker = tracker,
                    cameraOn = cameraOn,
                    largeCaptions = settings.largeCaptions,
                    gestureSpeed = if (settings.slowGestures) 0.75f else 1f,
                    onCard = vm::answerCard,
                    onYesNo = vm::answerYesNo,
                    onCue = vm::noteCue,
                    onLanguage = vm::chooseLanguage,
                    onDrawing = vm::submitDrawing,
                    onWritten = vm::answerText,
                    onSignFrame = vm::onSignFrame,
                    onStartSigning = vm::startReading,
                    onDoneSigning = vm::finishReading,
                    onOfficer = backToOfficer,
                    onNext = (vm.auto::advance).takeIf { (conv as? Conv.Proposed)?.advance == true },
                    onSignChoice = vm::chooseSign,
                    onNextRule = vm::nextRule,
                    onPauseEnd = vm::endPause,
                    onGiven = vm::give,
                )
            }
        }
    }

    sandesHandoffContact?.let { contact ->
        SandesHandoffDialog(
            contact = contact,
            onLaunch = {
                sandesHandoffContact = null
                activity?.let(::launchSandes)
            },
            onDismiss = { sandesHandoffContact = null },
        )
    }
}

/** FR-64: the only text the Sandes handoff puts on the clipboard is the interpreter's contact. */
fun sanitizeSandesHandoffClipboard(contact: String): String = contact.trim()

@Composable
internal fun SandesHandoffDialog(
    contact: String,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.sandes_contact_copied)

    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Call, contentDescription = null) },
        title = { Text(stringResource(R.string.sandes_handoff_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.sandes_handoff_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            stringResource(R.string.sandes_contact_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            contact,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = {
                        // Only the configured contact reaches the clipboard, never session or transcript data.
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText(null, sanitizeSandesHandoffClipboard(contact)).toClipEntry())
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.sandes_copy_contact))
                }
                Button(onClick = onLaunch) {
                    Text(stringResource(R.string.sandes_launch))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun launchSandes(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(SANDES_PACKAGE)
    val opened = intent != null && runCatching { activity.startActivity(intent) }.isSuccess
    if (!opened) {
        val name = activity.getString(R.string.settings_sandes)
        Toast.makeText(activity, activity.getString(R.string.call_missing, name), Toast.LENGTH_LONG).show()
    }
}

/** Opens WhatsApp at the interpreter's number; no session data goes with it (FR-64). Sandes goes through its dialog. */
private fun callInterpreter(activity: Activity, settings: AppSettings) {
    val name = activity.getString(R.string.settings_whatsapp)
    val intent = whatsappNumber(settings.interpreterContact)?.let { Intent(Intent.ACTION_VIEW, "https://wa.me/$it".toUri()) }
    val opened = intent != null && runCatching { activity.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(activity, activity.getString(R.string.call_missing, name), Toast.LENGTH_LONG).show()
    }
}

private fun shareReport(activity: Activity, file: File) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    activity.startActivity(Intent.createChooser(send, null))
}

/** The officer phone must not sleep into the background mid-answer while a second screen is live. */
@Composable
private fun KeepScreenOnWhile(activity: Activity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
internal fun ImmersiveWhile(activity: Activity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
