package app.signsetu.ui.start

import app.signsetu.ui.components.BrandButton
import app.signsetu.ui.components.ChoiceTile
import app.signsetu.ui.components.OfficerCard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.selection.toggleable
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.ui.session.SYSTEM_JJ_NOTICE
import app.signsetu.ui.session.SYSTEM_JUVENILE
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.camera.hasCameraPermission
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.MiniFace
import app.signsetu.ui.session.characterFor
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import app.signsetu.ui.components.FilterObscuredTouches

/**
 * Home: the character greets on top; below, one quick choice of who is being helped and in which language.
 * The session itself opens on the stage, with the camera mirror.
 */
@Composable
fun StartScreen(
    repo: SessionRepository,
    content: ContentBundle,
    expired: Boolean,
    rooted: Boolean,
    cameraWanted: Boolean,
    onOpenSession: (String) -> Unit,
    onSettings: () -> Unit,
    onSecondScreen: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ink = LocalInk.current
    var refresh by remember { mutableIntStateOf(0) }
    val open by produceState<Session?>(null, refresh) { value = repo.openSession() }
    var profile by rememberSaveable { mutableStateOf<Profile?>(null) }
    var lang by rememberSaveable { mutableStateOf("hi") }
    var role by rememberSaveable { mutableStateOf(SessionRole.NOT_KNOWN) }
    var underAge by rememberSaveable { mutableStateOf(false) }

    val begin = { p: Profile ->
        scope.launch {
            val id = repo.start(p, lang, role)
            // The record shows the officer saw the Juvenile Justice notice, and whether they marked the person a child.
            if (role == SessionRole.ACCUSED) {
                repo.log(id, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_JJ_NOTICE)
                if (underAge && p != Profile.YOUNG_CHILD) repo.log(id, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_JUVENILE)
            }
            onOpenSession(id)
        }
        Unit
    }
    // The camera permission is asked of the officer here, never of the subject on the stage.
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { profile?.let(begin) }
    val onStart = {
        profile?.let { p ->
            if (cameraWanted && !hasCameraPermission(context)) askCamera.launch(Manifest.permission.CAMERA) else begin(p)
        }
        Unit
    }

    val header = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = Color.Transparent, contentColor = ink.onBand, shape = header, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.background(ink.brush).statusBarsPadding().height(128.dp).fillMaxWidth().clip(RectangleShape)) {
                // Shown head and shoulders only: the officer's screen, where the character just says hello.
                Avatar(
                    character = characterFor(profile ?: Profile.YOUNG_CHILD),
                    gesture = content.gestures["namaste"],
                    playKey = profile,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.lg).size(96.dp, 110.dp).offset(y = 34.dp),
                )
                Column {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f).semantics { heading() })
                        onSecondScreen?.let { IconButton(onClick = it) { Icon(Icons.Filled.Phone, contentDescription = stringResource(R.string.second_screen)) } }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title)) }
                    }
                    Text(
                        listOfNotNull(content.language(lang)?.hello, stringResource(R.string.home_tagline)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.onBandMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.lg, end = 128.dp),
                    )
                }
            }
        }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(top = Spacing.lg, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (expired) Notice(stringResource(R.string.start_expired_title), stringResource(R.string.start_expired_body), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    if (content.damaged) {
                        Notice(stringResource(R.string.start_damaged_title), stringResource(R.string.start_damaged_body), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    } else if (content.rejected.isNotEmpty()) {
                        Notice(null, stringResource(R.string.start_media_rejected), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    if (content.aslDisabled) Notice(null, stringResource(R.string.start_asl_off), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    if (rooted) Notice(null, stringResource(R.string.start_rooted), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    open?.let { session ->
                        ResumeCard(
                            session = session,
                            languageName = content.language(session.subjectLang)?.name ?: session.subjectLang,
                            onResume = { onOpenSession(session.id) },
                            onEnd = { scope.launch { repo.purge(session.id); refresh++ } },
                        )
                    }
                    // Profile, language and role are one decision, so they share one card.
                    OfficerCard {
                        Text(stringResource(R.string.start_who), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Profile.entries.chunked(2).forEach { pair ->
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    pair.forEach { p -> ProfileTile(p, content, selected = profile == p, onSelect = { profile = p }, Modifier.weight(1f).fillMaxHeight()) }
                                }
                            }
                        }
                        Text(
                            profile?.let { stringResource(profileDescription(it)) } ?: stringResource(R.string.home_greeting_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (profile == Profile.OTHER_LANGUAGE) {
                            Text(stringResource(R.string.start_language_finder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            LanguagePicker(content, lang, onPick = { lang = it }, label = stringResource(R.string.start_language))
                        }
                        content.language(lang)?.takeIf { profile != Profile.OTHER_LANGUAGE && content.subjectText("yes", lang) == null }?.let {
                            Notice(null, stringResource(R.string.start_pictures_only, it.name), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        RoleChoice(role, onRole = { role = it })
                    }
                    if (role == SessionRole.ACCUSED) {
                        JuvenileNotice(underAge || profile == Profile.YOUNG_CHILD, enabled = profile != Profile.YOUNG_CHILD, onChecked = { underAge = it })
                    }
                }
                BrandButton(
                    onClick = onStart,
                    enabled = profile != null && !expired && !content.damaged,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).height(56.dp),
                ) {
                    Text(stringResource(R.string.start_button), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = Spacing.sm))
                }
    }
}

/** One profile: the face the person will meet on the stage, and a short name. */
@Composable
private fun ProfileTile(profile: Profile, content: ContentBundle, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    ChoiceTile(selected, onSelect, modifier.heightIn(min = 64.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                MiniFace(
                    characterFor(profile),
                    36.dp,
                    LocalStageColors.current.skyTop,
                    gesture = if (profile == Profile.NOT_SURE) content.gestures["ask"] else null,
                    atSeconds = if (profile == Profile.NOT_SURE) 0.9f else null,
                )
                ProfileBadge(profile, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
            }
            Text(
                stringResource(profileShort(profile)),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ProfileBadge(profile: Profile, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val icon = when (profile) {
        Profile.ISL_USER -> R.drawable.ic_hand
        Profile.OTHER_LANGUAGE -> R.drawable.ic_translate
        else -> null
    }
    if (icon == null && profile != Profile.NOT_SURE) return
    Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, border = BorderStroke(1.dp, colors.surfaceContainerLowest), modifier = modifier.size(22.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp))
            else Text("?", fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Notice(title: String?, body: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResumeCard(session: Session, languageName: String, onResume: () -> Unit, onEnd: () -> Unit) {
    OfficerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniFace(characterFor(session.profile), 40.dp, LocalStageColors.current.skyTop)
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(stringResource(R.string.start_resume_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.start_resume_body,
                        stringResource(profileTitle(session.profile)),
                        languageName,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.startedAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            TextButton(onClick = onEnd, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.start_end), color = MaterialTheme.colorScheme.error) }
            FilledTonalButton(onClick = onResume, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.start_resume))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(content: ContentBundle, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    val current = content.language(selected)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = current?.let { "${it.name}  ·  ${it.english}" } ?: selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = label?.let { { Text(it) } },
            leadingIcon = { Icon(painterResource(R.drawable.ic_translate), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FilterObscuredTouches()
            content.languages.forEach { l ->
                DropdownMenuItem(
                    text = { Text("${l.name}  ·  ${l.english}") },
                    onClick = { onPick(l.code); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Shown for every accused person: the Juvenile Justice rules if they may be under 18, and the officer's mark that
 * they may be, which leaves out adult arrest rights. A young child profile is always treated as under 18.
 */
@Composable
fun JuvenileNotice(checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.start_juvenile_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.start_juvenile_body), style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Text(stringResource(R.string.start_juvenile_mark), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

@Composable
fun RoleChoice(role: SessionRole, onRole: (SessionRole) -> Unit, modifier: Modifier = Modifier) {
    Text(stringResource(R.string.report_role), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    // Equal-height tiles: labels wrap in Hindi and at 360 dp, and a segmented row clips them.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SessionRole.entries.forEach { r ->
            ChoiceTile(r == role, onClick = { onRole(r) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(roleTitle(r)), style = MaterialTheme.typography.labelLarge, maxLines = 3, textAlign = TextAlign.Center)
            }
        }
    }
}

fun roleTitle(r: SessionRole) = when (r) {
    SessionRole.NOT_KNOWN -> R.string.role_not_known
    SessionRole.VICTIM -> R.string.role_victim
    SessionRole.ACCUSED -> R.string.role_accused
}

fun profileTitle(p: Profile) = when (p) {
    Profile.YOUNG_CHILD -> R.string.profile_child
    Profile.ISL_USER -> R.string.profile_isl
    Profile.OTHER_LANGUAGE -> R.string.profile_other
    Profile.NOT_SURE -> R.string.profile_unsure
}

private fun profileShort(p: Profile) = when (p) {
    Profile.YOUNG_CHILD -> R.string.profile_child
    Profile.ISL_USER -> R.string.profile_isl_short
    Profile.OTHER_LANGUAGE -> R.string.profile_other_short
    Profile.NOT_SURE -> R.string.profile_unsure
}

private fun profileDescription(p: Profile) = when (p) {
    Profile.YOUNG_CHILD -> R.string.profile_child_desc
    Profile.ISL_USER -> R.string.profile_isl_desc
    Profile.OTHER_LANGUAGE -> R.string.profile_other_desc
    Profile.NOT_SURE -> R.string.profile_unsure_desc
}
