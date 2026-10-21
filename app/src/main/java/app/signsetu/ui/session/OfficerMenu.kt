package app.signsetu.ui.session

import androidx.compose.material.icons.filled.Star
import app.signsetu.camera.EvidenceMode
import app.signsetu.camera.SkeletonOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.signsetu.R
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.signsetu.data.content.SignLanguage
import app.signsetu.ui.components.FilterObscuredTouches
import app.signsetu.ui.theme.SunMode

/** A menu row that toggles: TalkBack hears its state, as a Checkbox or Switch with its own handler would give. */
private fun Modifier.toggleState(on: Boolean, kind: Role) = semantics {
    role = kind
    toggleableState = ToggleableState(on)
}

/** The overflow menu: who the person is, how the stage shows, the cloud report and ending the session. */
@Composable
internal fun OfficerMenu(
    ui: SessionUi,
    vm: SessionViewModel,
    canChangeSignLanguage: Boolean,
    onChange: () -> Unit,
    onAiReport: () -> Unit,
    onEnd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val signLanguage = ui.signLanguage
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.officer_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            FilterObscuredTouches()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.officer_change)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                onClick = { menu = false; onChange() },
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
                enabled = canChangeSignLanguage,
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
            SwitchMenuItem(R.string.officer_sun, rememberVectorPainter(Icons.Filled.Star), SunMode.forced.value) { SunMode.forced.value = !SunMode.forced.value }
            SwitchMenuItem(R.string.officer_glance, rememberVectorPainter(Icons.Filled.Refresh), !StagePace.glanceHidden.value) {
                StagePace.glanceHidden.value = !StagePace.glanceHidden.value
            }
            SwitchMenuItem(R.string.officer_skeleton_show, painterResource(R.drawable.ic_videocam), SkeletonOverlay.on.value) { SkeletonOverlay.on.value = !SkeletonOverlay.on.value }
            SwitchMenuItem(R.string.officer_skeleton_keep, painterResource(R.drawable.ic_videocam), EvidenceMode.autoCapture.value) {
                EvidenceMode.autoCapture.value = !EvidenceMode.autoCapture.value
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_ai_report)) },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menu = false; onAiReport() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.officer_end), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onEnd() },
            )
        }
    }
}

/** A menu row with a switch; unlike the other rows it leaves the menu open. */
@Composable
private fun SwitchMenuItem(@StringRes label: Int, icon: Painter, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Switch(checked = checked, onCheckedChange = null) },
        onClick = onToggle,
        modifier = Modifier.toggleState(checked, Role.Switch),
    )
}
