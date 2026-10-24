package app.signsetu.ui.pair

import app.signsetu.ui.session.clockTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalResources
import app.signsetu.data.session.EventType
import app.signsetu.ui.session.signedDisplay
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.pair.LinkState
import app.signsetu.ui.session.AnswerEvents
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.describe
import app.signsetu.ui.theme.Spacing

/** Above the officer's page while paired: the device, what is on it, the last answer, and any alert. */
@Composable
fun PairedBar(pane: PairedPane, ui: SessionUi, content: ContentBundle, officerLang: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val s = pane.status
        val low = s != null && s.battery < 15 && !s.charging
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (low) Color(0xFFFFC857) else colors.secondaryContainer,
            contentColor = if (low) Color(0xFF3A2A00) else colors.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    if (s != null) stringResource(R.string.second_screen_chip, pane.label, s.battery) else pane.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        when {
            pane.unpinned -> Alert(stringResource(R.string.second_screen_unpinned), stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone)
            pane.state is LinkState.Ended -> Alert(stringResource(R.string.second_screen_ended), stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone)
            pane.state == LinkState.Lost -> Alert(
                stringResource(R.string.second_screen_lost),
                stringResource(R.string.second_screen_reconnect) to pane.onReconnect,
                stringResource(R.string.second_screen_use_this) to pane.onUseThisPhone,
            )
        }
        if (pane.breakPending) Alert(stringResource(R.string.second_screen_break), stringResource(R.string.second_screen_break_given) to pane.onBreakGiven)
        pane.askedAt?.let {
            Text(stringResource(R.string.second_screen_asked_before, clockTime(it)), style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
        }
        if (ui.mode == Mode.SHOW) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                        Text(
                            stringResource(R.string.second_screen_on, ui.onStage?.let { describe(it, content, officerLang) } ?: stringResource(R.string.second_screen_idle)),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val res = LocalResources.current
                        ui.events.lastOrNull { it.via != null && it.type in AnswerEvents }?.let { e -> if (e.type == EventType.ANSWER_SIGNED) signedDisplay(res, e.text) else e.text }?.let {
                            Text(stringResource(R.string.second_screen_last_answer, it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = pane.onDone) { Text(stringResource(R.string.second_screen_done)) }
                }
            }
        }
    }
}

@Composable
private fun Alert(text: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer, contentColor = colors.onErrorContainer) {
        Column(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm)) {
            Text(text, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.align(Alignment.End)) {
                actions.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(label, color = colors.onErrorContainer) } }
            }
        }
    }
}
