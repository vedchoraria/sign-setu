package app.signsetu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay

/** The officer's view of the AI counsellor: the next line before it is shown, and Take over at every step. */
@Composable
internal fun CounsellorPanel(state: Counsel, counsellor: Counsellor, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, shape = MaterialTheme.shapes.large, modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs).fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.ai_tag), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            when (state) {
                Counsel.Off -> Unit
                Counsel.Thinking -> Status(stringResource(R.string.counsel_thinking), counsellor)
                Counsel.Waiting -> Status(stringResource(R.string.counsel_waiting), counsellor)
                is Counsel.Holding -> {
                    var now by remember(state) { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(state) { while (now < state.untilMs) { delay(200); now = System.currentTimeMillis() } }
                    val left = ((state.untilMs - now) / 1000L + 1).coerceAtLeast(0)
                    Text(state.line.say, style = MaterialTheme.typography.titleMedium)
                    state.line.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    LinearProgressIndicator(progress = { (left / 6f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.counsel_next, left), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = counsellor::sendNow) { Text(stringResource(R.string.counsel_send_now)) }
                        OutlinedButton(onClick = counsellor::skip) { Text(stringResource(R.string.counsel_skip)) }
                        TextButton(onClick = counsellor::takeOver) { Text(stringResource(R.string.counsel_take_over)) }
                    }
                }
                is Counsel.Paused -> {
                    Text(
                        stringResource(
                            when (state.why) {
                                Counsel.Why.STOP -> R.string.counsel_paused_stop
                                Counsel.Why.HANDOFF -> R.string.counsel_paused_handoff
                                Counsel.Why.NO_REPLY -> R.string.counsel_paused_no_reply
                                Counsel.Why.LIMIT -> R.string.counsel_paused_limit
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(onClick = onResume) { Text(stringResource(R.string.counsel_resume)) }
                        Button(onClick = counsellor::takeOver) { Text(stringResource(R.string.counsel_take_over)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, counsellor: Counsellor) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Button(onClick = counsellor::takeOver) { Text(stringResource(R.string.counsel_take_over)) }
    }
}
