package app.signsetu.ui.session

import app.signsetu.data.session.TITLE_MAX
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
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

/** The name a session is saved under until the officer types their own: its start, as the phone writes dates. */
fun defaultTitle(res: android.content.res.Resources, startedAt: Long): String =
    res.getString(R.string.wrap_default_name, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(startedAt)))

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
 * Finishing a session: what it gathered, a thank-you for the person, and a name to keep it under. It is saved
 * on this phone, encrypted, until the officer deletes it; a report can be exported now or later.
 */
@Composable
internal fun WrapUpDialog(
    summary: WrapUp,
    title: String,
    onTitle: (String) -> Unit,
    onThank: (() -> Unit)?,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { onTitle(it.take(TITLE_MAX)) },
                    label = { Text(stringResource(R.string.wrap_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.wrap_saved_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                onThank?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wrap_thank)) } }
                Button(onClick = onSave, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wrap_save)) }
                OutlinedButton(onClick = onExport, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.end_export)) }
                // Deleting is a second, deliberate step: the first tap only arms it.
                var sure by remember { mutableStateOf(false) }
                TextButton(onClick = { if (sure) onDelete() else sure = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (sure) R.string.wrap_delete_sure else R.string.wrap_delete), color = MaterialTheme.colorScheme.error)
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
