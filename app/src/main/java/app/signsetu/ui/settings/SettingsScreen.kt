package app.signsetu.ui.settings

import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import app.signsetu.assistant.ModelStore
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ListItemDefaults
import app.signsetu.ui.components.OfficerCard
import app.signsetu.ui.session.SectionLabel
import androidx.compose.foundation.layout.ColumnScope
import android.app.Activity
import app.signsetu.data.settings.OfficerLocale
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.BuildConfig
import app.signsetu.R
import app.signsetu.data.settings.AppSettings
import app.signsetu.data.settings.InterpreterApp
import app.signsetu.data.settings.SettingsStore
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.signsetu.ui.components.GuardedAlertDialog

private enum class Dialog { APP, CONTACT, RETENTION, CLEAR, LICENCES, LANGUAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, models: ModelStore, onBack: () -> Unit, onClearAll: () -> Unit) {
    val loaded by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = loaded ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val notSet = stringResource(R.string.settings_not_set)
    var modelState by remember { mutableStateOf(models.state()) }
    var importing by remember { mutableStateOf(false) }
    var badModel by remember { mutableStateOf(false) }
    // The department hands out the model as a file; it is copied in and checked, never downloaded (D-24).
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            badModel = false
            scope.launch {
                badModel = !models.import(uri)
                modelState = models.state()
                importing = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Group(R.string.settings_interpreter) {
                Item(stringResource(R.string.settings_interpreter_app), stringResource(appName(s.interpreterApp))) { dialog = Dialog.APP }
                if (s.interpreterApp != InterpreterApp.NONE) {
                    Item(stringResource(R.string.settings_interpreter_contact), s.interpreterContact.ifBlank { notSet }) { dialog = Dialog.CONTACT }
                }
            }
            Group(R.string.settings_stage) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_camera)) },
                    supportingContent = { Text(stringResource(R.string.settings_camera_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.cameraEnabled, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.cameraEnabled, role = Role.Switch) { on -> scope.launch { store.setCameraEnabled(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_large_captions)) },
                    supportingContent = { Text(stringResource(R.string.settings_large_captions_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.largeCaptions, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.largeCaptions, role = Role.Switch) { on -> scope.launch { store.setLargeCaptions(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_slow_gestures)) },
                    supportingContent = { Text(stringResource(R.string.settings_slow_gestures_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.slowGestures, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.slowGestures, role = Role.Switch) { on -> scope.launch { store.setSlowGestures(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_isl_reorder)) },
                    supportingContent = { Text(stringResource(R.string.settings_isl_reorder_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.islReorder, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.islReorder, role = Role.Switch) { on -> scope.launch { store.setIslReorder(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_pin_to_leave)) },
                    supportingContent = { Text(stringResource(R.string.settings_pin_to_leave_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.pinToLeaveStage, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.pinToLeaveStage, role = Role.Switch) { on -> scope.launch { store.setPinToLeaveStage(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_allow_auto)) },
                    supportingContent = { Text(stringResource(R.string.settings_allow_auto_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.allowAuto, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.allowAuto, role = Role.Switch) { on -> scope.launch { store.setAllowAuto(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_tilt)) },
                    supportingContent = { Text(stringResource(R.string.settings_tilt_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.tiltToShow, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.tiltToShow, role = Role.Switch) { on -> scope.launch { store.setTiltToShow(on) } },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_volume_keys)) },
                    supportingContent = { Text(stringResource(R.string.settings_volume_keys_summary)) },
                    colors = ItemColors,
                    trailingContent = { Switch(checked = s.volumeKeys, onCheckedChange = null) },
                    modifier = Modifier.toggleable(s.volumeKeys, role = Role.Switch) { on -> scope.launch { store.setVolumeKeys(on) } },
                )
            }
            Group(R.string.settings_assistant) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                when {
                                    importing -> R.string.settings_assistant_importing
                                    badModel -> R.string.settings_assistant_bad
                                    else -> when (modelState) {
                                        ModelStore.State.READY -> R.string.settings_assistant_ready
                                        ModelStore.State.NOT_INSTALLED -> R.string.settings_assistant_missing
                                        ModelStore.State.TOO_LITTLE_MEMORY -> R.string.settings_assistant_memory
                                        ModelStore.State.UNSUPPORTED -> R.string.settings_assistant_unsupported
                                    }
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (badModel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = ItemColors,
                )
                if (!importing && modelState == ModelStore.State.NOT_INSTALLED) {
                    Item(stringResource(R.string.settings_assistant_import), "") { pickModel.launch(arrayOf("*/*")) }
                }
                if (!importing && modelState == ModelStore.State.READY) {
                    Item(stringResource(R.string.settings_assistant_remove), "", destructive = true) {
                        scope.launch { models.remove(); modelState = models.state() }
                    }
                }
            }
            Group(R.string.settings_records) {
                Item(
                    stringResource(R.string.settings_retention),
                    pluralStringResource(R.plurals.settings_hours, s.retentionHours, s.retentionHours),
                    note = stringResource(R.string.settings_retention_note),
                ) { dialog = Dialog.RETENTION }
                Item(stringResource(R.string.settings_clear), stringResource(R.string.settings_clear_summary), destructive = true) {
                    dialog = Dialog.CLEAR
                }
            }
            Group(R.string.settings_about) {
                Item(stringResource(R.string.settings_app_language), stringResource(languageName(OfficerLocale.get(context)))) { dialog = Dialog.LANGUAGE }
                Item(stringResource(R.string.settings_licences), stringResource(R.string.settings_licences_summary)) { dialog = Dialog.LICENCES }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    colors = ItemColors,
                    supportingContent = {
                        val built = LocalDate.ofEpochDay(BuildConfig.BUILD_EPOCH_DAY).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, built))
                            // FR-52: the notice appears in About as well as on first run.
                            Text(stringResource(R.string.notice_aid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    when (dialog) {
        Dialog.APP -> ChoiceDialog(
            title = stringResource(R.string.settings_interpreter_app),
            options = InterpreterApp.entries,
            selected = s.interpreterApp,
            label = { stringResource(appName(it)) },
            onPick = { app ->
                // A WhatsApp number and a Sandes contact name are not interchangeable.
                val contact = if (app == s.interpreterApp) s.interpreterContact else ""
                scope.launch { store.setInterpreter(app, contact) }
                dialog = if (app == InterpreterApp.NONE || contact.isNotBlank()) null else Dialog.CONTACT
            },
            onDismiss = { dialog = null },
        )
        Dialog.CONTACT -> ContactDialog(
            app = s.interpreterApp,
            initial = s.interpreterContact,
            onSave = { scope.launch { store.setInterpreter(s.interpreterApp, it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.RETENTION -> ChoiceDialog(
            title = stringResource(R.string.settings_retention),
            options = AppSettings.RETENTION_CHOICES,
            selected = s.retentionHours,
            label = { pluralStringResource(R.plurals.settings_hours, it, it) },
            onPick = { scope.launch { store.setRetentionHours(it) }; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.CLEAR -> GuardedAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = null; onClearAll() }) {
                    Text(stringResource(R.string.settings_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        Dialog.LICENCES -> LicencesDialog(onDismiss = { dialog = null })
        Dialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = OfficerLocale.CHOICES,
            selected = OfficerLocale.get(context),
            label = { stringResource(languageName(it)) },
            onPick = { tag ->
                dialog = null
                OfficerLocale.set(context, tag)
                (context as? Activity)?.recreate()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@StringRes
fun languageName(tag: String) = when (tag) {
    "en" -> R.string.lang_english
    "hi" -> R.string.lang_hindi
    else -> R.string.settings_lang_system
}

@StringRes
private fun appName(app: InterpreterApp) = when (app) {
    InterpreterApp.NONE -> R.string.settings_not_set
    InterpreterApp.WHATSAPP -> R.string.settings_whatsapp
    InterpreterApp.SANDES -> R.string.settings_sandes
}

/** One titled card of settings on the page background. */
@Composable
private fun Group(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(stringResource(title), Modifier.padding(start = Spacing.xxl, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm))
    OfficerCard(Modifier.padding(horizontal = Spacing.lg), padding = 0.dp) {
        val divider = MaterialTheme.colorScheme.outlineVariant
        // Opaque items 1 dp apart over a line drawn from 16 dp: inset dividers without one per call site.
        Column(
            Modifier.drawBehind { drawRect(divider, topLeft = Offset(Spacing.lg.toPx(), 0f)) },
            verticalArrangement = Arrangement.spacedBy(1.dp),
            content = content,
        )
    }
}

private val ItemColors @Composable get() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

@Composable
private fun Item(title: String, summary: String, note: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(title, color = if (destructive) colors.error else colors.onSurface) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (summary.isNotEmpty()) Text(summary)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        },
        colors = ItemColors,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = option == selected, onClick = { onPick(option) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ContactDialog(app: InterpreterApp, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val phone = app == InterpreterApp.WHATSAPP
    val valid = if (phone) whatsappNumber(value) != null else value.isNotBlank()
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_interpreter_contact)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = {
                    Text(stringResource(if (phone) R.string.settings_contact_whatsapp_hint else R.string.settings_contact_sandes_hint))
                },
                isError = phone && value.isNotBlank() && !valid,
                supportingText = if (phone && value.isNotBlank() && !valid) {
                    { Text(stringResource(R.string.settings_contact_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Names and licences stay in English: they are legal identifiers, not interface text. */
private val LICENCES = listOf(
    "OpenMoji 17.0.0" to "CC BY-SA 4.0. HfG Schwäbisch Gmünd, openmoji.org",
    "Mulberry Symbols" to "CC BY-SA 4.0. Steve Lee, mulberrysymbols.org",
    "Indic Parler-TTS (recorded prompts)" to "Apache 2.0. AI4Bharat, IIT Madras",
    "INCLUDE (sign reader training data)" to "CC BY 4.0. \"INCLUDE: A Large Scale Dataset for Indian Sign Language Recognition\", " +
        "A. Sridhar, R. G. Ganesan, P. Kumar, M. Khapra (IIT Madras, AI4Bharat), doi:10.5281/zenodo.4010759. " +
        "Changed: a sign classifier was trained on its landmarks.",
    "OpenHands pose release" to "CC BY 4.0. AI4Bharat, \"OpenHands v1: Labelled ISLR datasets, Poses\", zenodo.org/records/6674324. " +
        "Changed: poses resampled and normalised.",
    "PopSign ASL v1.0 (ASL reader training data, ASL signs)" to "CC BY 4.0. Starner, Forbes, So, Martin, Sridhar, Deshpande et al. (Georgia Tech, NTID), " +
        "NeurIPS 2023 Datasets and Benchmarks, signdata.cc.gatech.edu; frames from huggingface.co/datasets/sign/popsign-images. " +
        "Changed: landmarks extracted, a sign classifier trained, signers retargeted as keyframes on the app's characters, mirrored to right-hand dominant where the left hand led.",
    "Multimodal ISL alphabet dataset (ISL letter reader training data)" to "CC BY 4.0. Hambir, Khandagale, Cherian, Gaikwad " +
        "(Vishwakarma Institute of Information Technology, Pune), Mendeley Data, doi:10.17632/2tj6c7pzfk.1. " +
        "Changed: hand landmarks extracted from the English and number photographs, a letter classifier trained.",
    "ISLAN (ISL letter reader test data)" to "CC BY 4.0. Mendeley Data, doi:10.17632/rc349j45m5.1. " +
        "Changed: photographs turned upright and hand landmarks extracted to measure the letter reader; not shipped.",
    "ASLNow! fingerspelling landmarks" to "MIT. sid220, huggingface.co/datasets/sid220/asl-now-fingerspelling. " +
        "Changed: letters chained into synthetic spelling to train a reader, and posed as the ASL alphabet.",
    "MediaPipe and the Holistic landmarker model" to "Apache 2.0. Google LLC",
    "Sign Setu symbols (original drawings)" to "CC BY-SA 4.0. Drawn in OpenMoji style for this app",
    "SQLCipher for Android" to "BSD 3-Clause. Zetetic LLC",
    "PdfBox-Android" to "Apache 2.0. Tom Roush; Apache PDFBox",
    "AndroidX, Jetpack Compose, Media3" to "Apache 2.0. The Android Open Source Project",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0. JetBrains",
    "CameraX" to "Apache 2.0. The Android Open Source Project",
    "Protocol Buffers for Java (protobuf-java)" to "BSD 3-Clause. Google LLC",
    "BouncyCastle" to "MIT-style Bouncy Castle licence. The Legion of the Bouncy Castle Inc.",
    "llama.cpp" to "MIT. Georgi Gerganov and contributors, ggml-org/llama.cpp",
    "Baloo 2 font" to "SIL Open Font Licence 1.1. Ek Type",
    "INCLUDE sign retargeting" to "CC BY 4.0. Motions retargeted from the INCLUDE landmark samples; see INCLUDE above.",
    "ISLRTC Indian Sign Language Dictionary" to "Government Open Data License - India. Indian Sign Language Research and Training Centre, " +
        "Government of India, data.gov.in. Changed: motions of words missing from INCLUDE retargeted onto the app's characters; no video is included.",
)

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licences)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LICENCES.forEach { (name, licence) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(licence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Symbols are converted to bitmaps for this app; the converted symbol set is shared under CC BY-SA 4.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
