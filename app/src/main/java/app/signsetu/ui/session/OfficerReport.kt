package app.signsetu.ui.session

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.export.ReportText
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.start.profileTitle
import app.signsetu.ui.start.roleTitle
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.ui.theme.Spacing
import java.io.File
import java.text.DateFormat
import java.util.Date
import app.signsetu.ui.pair.linkText
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn

internal sealed interface ExportState {
    data object Working : ExportState
    data class Ready(val file: File, val passphrase: String) : ExportState
    data object Failed : ExportState
}

/** How the subject answered, by kind, for the report header: what a reader of the record should weigh first. */
internal fun answerSummary(res: Resources, events: List<SessionEvent>): Pair<String, String>? {
    val subject = events.filter { it.source == EventSource.SUBJECT }
    fun n(vararg t: EventType) = subject.count { it.type in t }
    val parts = listOfNotNull(
        n(EventType.ANSWER_CARD).takeIf { it > 0 }?.let { taps ->
            val cued = subject.count { it.type == EventType.ANSWER_CARD && it.reason?.startsWith("cue:") == true }
            if (cued > 0) res.getString(R.string.summary_taps_cued, taps, cued) else res.getString(R.string.summary_taps, taps)
        },
        // A signed answer is logged as the app's reading of it, one per sign window.
        events.count { it.type == EventType.ANSWER_SIGNED && it.source == EventSource.MACHINE }.takeIf { it > 0 }?.let { res.getString(R.string.summary_signed, it) },
        n(EventType.ANSWER_DRAWING).takeIf { it > 0 }?.let { res.getString(R.string.summary_drawn, it) },
        n(EventType.ANSWER_TEXT).takeIf { it > 0 }?.let { res.getString(R.string.summary_written, it) },
        n(EventType.ANSWER_GESTURE).takeIf { it > 0 }?.let { res.getString(R.string.summary_gestured, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.let { res.getString(R.string.report_answers) to it.joinToString(", ") }
}

internal fun reportText(res: Resources, ui: SessionUi, content: ContentBundle): ReportText {
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val language = content.language(ui.session.subjectLang)
    return ReportText(
        title = res.getString(R.string.report_title),
        disclaimer = res.getString(R.string.report_disclaimer) + "\n" + res.getString(R.string.report_grade_legend),
        header = listOfNotNull(
            res.getString(R.string.report_started) to time.format(Date(ui.session.startedAt)),
            res.getString(R.string.report_exported) to time.format(Date()),
            // A role the officer never set is left out rather than guessed.
            (res.getString(R.string.report_role) to res.getString(roleTitle(ui.session.role))).takeIf { ui.session.role != SessionRole.NOT_KNOWN },
            res.getString(R.string.report_profile) to res.getString(profileTitle(ui.session.profile)),
            res.getString(R.string.report_language) to (language?.let { "${it.english} (${it.name})" } ?: ui.session.subjectLang),
        ) + ui.events.filter { it.via != null }.let { remote ->
            val clock = DateFormat.getTimeInstance(DateFormat.SHORT)
            if (remote.isEmpty()) emptyList() else listOf(res.getString(R.string.report_second_screen) to res.getString(R.string.report_via_header, clock.format(Date(remote.first().ts)), clock.format(Date(remote.last().ts))))
        } + autoHeader(res, ui.events) + listOfNotNull(answerSummary(res, ui.events)),
        time = res.getString(R.string.report_time),
        event = res.getString(R.string.report_event),
        detail = res.getString(R.string.report_detail),
        inReplyTo = res.getString(R.string.report_in_reply),
        page = { n, total -> res.getString(R.string.report_page, n, total) },
        eventLabel = { res.getString(it.label()) },
        formatTime = { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
        notStatement = res.getString(R.string.report_not_statement),
        readings = res.getString(R.string.report_readings),
    )
}

/** The session log as plain lines, for the optional cloud report. */
internal fun sessionLog(events: List<SessionEvent>) = events.joinToString("\n") { ev ->
    "[${ev.type.name}] [${ev.source.name}] text=${ev.text ?: ""} card=${ev.cardId ?: ""} lang=${ev.lang ?: ""}"
}

internal fun eventDetail(res: Resources, e: SessionEvent, by: Map<Long, ShownBy> = emptyMap(), idle: Set<Long> = emptySet()): String = when {
    e.type == EventType.SYSTEM -> systemText(res, e.text)
    e.type == EventType.ANSWER_SIGNED -> "${signedDisplay(res, e.text)} (${res.getString(signedMark(e))})"
    e.drawingId != null -> res.getString(R.string.report_drawing)
    else -> (linkText(res, e) ?: e.text.orEmpty()) + autoSuffix(res, e, by, idle)
}.let { if (e.via != null) "$it (${res.getString(R.string.report_via_stage)})" else it }

@Composable
internal fun ExportDialog(state: ExportState, onShare: () -> Unit, onFinish: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Working -> GuardedAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.export_working))
                }
            },
            confirmButton = {},
        )
        is ExportState.Ready -> GuardedAlertDialog(
            // The password is shown once and the file is deleted on Finish, so Back must not close it.
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.export_body))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.export_password),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                state.passphrase,
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp),
                                // Read out character by character, as the officer would.
                                modifier = Modifier.semantics { contentDescription = state.passphrase.toCharArray().joinToString(" ") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = Spacing.sm))
                }
            },
            dismissButton = {
                // The session stays saved, so a lost file or password only means exporting again.
                TextButton(onClick = onFinish) { Text(stringResource(R.string.export_finish)) }
            },
        )
        ExportState.Failed -> GuardedAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.export_failed)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
internal fun AiReportDialog(reportText: String, onDismiss: () -> Unit) {
    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.ai_report_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_report_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.stage_done))
            }
        },
    )
}

@Composable
internal fun AiPreparingDialog() {
    GuardedAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.ai_preparing)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {},
    )
}
