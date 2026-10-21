package app.signsetu.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.theme.Spacing

/** What a session gathered, for the officer before it ends. */
data class WrapUp(val minutes: Int, val shown: Int, val answers: Int, val unanswered: Int, val unreadSigns: Int)

fun wrapUp(events: List<SessionEvent>, startedAt: Long, now: Long): WrapUp {
    val shown = events.filter { it.type == EventType.QUESTION_SHOWN }
    val answers = events.filter { it.type in AnswerEvents }
    val answered = answers.mapNotNull { it.shownId }.toSet()
    // A signed answer still holding "?" has signs nobody has read yet: an interpreter may need the replay.
    val unread = events.count { it.type == EventType.ANSWER_SIGNED && (it.text.isNullOrBlank() || it.text.split(' ').any { w -> w == "?" }) }
    // Greetings and statements expect no answer; only items that ask count as unanswered.
    val asking = shown.filter { it.text?.trimEnd()?.endsWith("?") == true }
    return WrapUp(((now - startedAt) / 60_000L).toInt().coerceAtLeast(0), shown.size, answers.size, asking.count { it.id !in answered }, unread)
}

/**
 * Finishing a session: what it gathered, a thank-you for the person, then the report or a plain finish. Ending is the
 * normal close of an interview, so it is not dressed as a deletion; the delete is stated in the text.
 */
@Composable
internal fun WrapUpDialog(summary: WrapUp, onThank: (() -> Unit)?, onExport: () -> Unit, onFinishOnly: () -> Unit, onDismiss: () -> Unit) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
        title = { Text(stringResource(R.string.wrap_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Fact(pluralStringResource(R.plurals.wrap_minutes, summary.minutes, summary.minutes))
                        Fact(pluralStringResource(R.plurals.wrap_shown, summary.shown, summary.shown))
                        Fact(pluralStringResource(R.plurals.wrap_answers, summary.answers, summary.answers))
                        if (summary.unanswered > 0) Fact(pluralStringResource(R.plurals.wrap_unanswered, summary.unanswered, summary.unanswered), warn = true)
                        if (summary.unreadSigns > 0) Fact(pluralStringResource(R.plurals.wrap_unread, summary.unreadSigns, summary.unreadSigns), warn = true)
                    }
                }
                Text(stringResource(R.string.end_body), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                onThank?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wrap_thank)) } }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.end_export)) }
                TextButton(onClick = onFinishOnly, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.end_no_export), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wrap_keep_going)) }
            }
        },
    )
}

@Composable
private fun Fact(text: String, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (warn) Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
    }
}
