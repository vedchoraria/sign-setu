package app.signsetu.ui.session

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Phase
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Role as SessionRole
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Who put a shown item on the stage, for the bubble tag and the report. */
enum class ShownBy(@StringRes val tag: Int?, @StringRes val report: Int) {
    APP(R.string.conv_tag_approved, R.string.report_by_app),
    AUTO(R.string.conv_tag_auto, R.string.report_by_auto),
    OFFICER(null, R.string.report_by_officer),
}

/** Read from the row logged just before each QUESTION_SHOWN; empty when auto mode was never used. */
fun shownBy(events: List<SessionEvent>): Map<Long, ShownBy> {
    if (events.none { it.type == EventType.SYSTEM && it.text == AutoKey.MODE_AUTO }) return emptyMap()
    return events.withIndex().filter { it.value.type == EventType.QUESTION_SHOWN }.associate { (i, e) ->
        val before = events.getOrNull(i - 1)?.takeIf { it.itemId != null && it.itemId == e.itemId }
        e.id to when (before?.type) {
            EventType.APPROVED -> ShownBy.APP
            EventType.AUTO_ADVANCED -> ShownBy.AUTO
            else -> ShownBy.OFFICER
        }
    }
}

/** Suggestions with no approval, auto-advance or skip before the next suggestion. */
fun notActedOn(events: List<SessionEvent>): Set<Long> {
    val idle = mutableSetOf<Long>()
    var open: SessionEvent? = null
    for (e in events) when (e.type) {
        EventType.SUGGESTED -> { open?.let { idle += it.id }; open = e }
        EventType.APPROVED, EventType.AUTO_ADVANCED, EventType.SKIPPED -> if (e.itemId == open?.itemId) open = null
        else -> Unit
    }
    open?.let { idle += it.id }
    return idle
}

@StringRes
fun autoKeyLabel(key: String?): Int? = when (key) {
    AutoKey.MODE_AUTO -> R.string.sys_mode_auto
    AutoKey.MODE_MANUAL -> R.string.sys_mode_manual
    AutoKey.MODE_MANUAL_SUBSTANTIVE -> R.string.sys_mode_manual_substantive
    AutoKey.PAUSED -> R.string.sys_auto_paused
    AutoKey.RESUMED -> R.string.sys_auto_resumed
    AutoKey.WRAP_UP -> R.string.sys_auto_wrap_up
    AutoKey.ENDED_STOP -> R.string.sys_auto_ended_stop
    AutoKey.ADVANCE_ON -> R.string.sys_auto_advance_on
    AutoKey.ADVANCE_OFF -> R.string.sys_auto_advance_off
    AutoKey.BREAK -> R.string.sys_auto_break
    else -> null
}

/** Which mode the report names: Manual, Auto, or Auto that was later switched off. */
@StringRes
fun reportMode(events: List<SessionEvent>): Int {
    val modes = events.filter { e ->
        e.type == EventType.SYSTEM && (e.text == AutoKey.MODE_AUTO || e.text == AutoKey.ENDED_STOP || e.text?.startsWith(AutoKey.MODE_MANUAL) == true)
    }
    val auto = modes.indexOfFirst { it.text == AutoKey.MODE_AUTO }
    return when {
        auto < 0 -> R.string.report_mode_manual
        modes.drop(auto + 1).any { it.text != AutoKey.MODE_AUTO } -> R.string.report_mode_auto_then_manual
        else -> R.string.report_mode_auto
    }
}

/** Report header lines: the mode, and that the officer answers for every item shown. */
fun autoHeader(res: Resources, events: List<SessionEvent>): List<Pair<String, String>> {
    return listOf(
        res.getString(R.string.report_mode) to res.getString(reportMode(events)),
        res.getString(R.string.report_responsible_title) to res.getString(R.string.report_responsible),
    )
}

/** What the report adds to a row: a rights mark, who chose a shown item, and suggestions left alone. */
fun autoSuffix(res: Resources, e: SessionEvent, by: Map<Long, ShownBy>, idle: Set<Long>): String = buildString {
    if (e.reason == SHOWN_RIGHTS) append(" (").append(res.getString(R.string.rights_shown_mark)).append(')')
    by[e.id]?.let { append(" · ").append(res.getString(it.report)) }
    if (e.id in idle) append(" (").append(res.getString(R.string.report_not_acted)).append(')')
}

/** The officer's Manual or Auto switch and, in Auto, the next proposed item with its reason. */
@Composable
fun AutoPanel(auto: AutoPilot, ui: SessionUi, content: ContentBundle, officerLang: String, modifier: Modifier = Modifier) {
    val conv by auto.conv.collectAsStateWithLifecycle()
    val advance by auto.autoAdvance.collectAsStateWithLifecycle()
    Column(modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            SegmentedButton(
                selected = conv == Conv.Off,
                onClick = { auto.setAuto(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text(stringResource(R.string.conv_manual)) },
            )
            SegmentedButton(
                selected = conv != Conv.Off,
                onClick = { auto.setAuto(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text(stringResource(R.string.conv_auto)) },
            )
        }
        when (val c = conv) {
            is Conv.Proposed -> if (ui.pending == null) NextCard(c.p, advance, auto, ui, content, officerLang)
            is Conv.Paused -> Notice(
                stringResource(
                    when (c.cause) {
                        PauseCause.STOP -> R.string.conv_paused_stop
                        PauseCause.BREAK -> R.string.conv_paused_break
                        PauseCause.OFFICER -> R.string.conv_paused_officer
                    },
                ),
                R.string.conv_resume to auto::resume,
                R.string.conv_switch_manual to { auto.setAuto(false) },
            )
            Conv.Exhausted -> Notice(
                stringResource(R.string.conv_exhausted),
                R.string.conv_wrap_up to auto::wrapUp,
                R.string.conv_switch_manual to { auto.setAuto(false) },
            )
            Conv.Off, Conv.Waiting -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextCard(p: Proposal, advance: Boolean, auto: AutoPilot, ui: SessionUi, content: ContentBundle, officerLang: String) {
    val q = p.q
    val colors = MaterialTheme.colorScheme
    val subjectLang = ui.session.subjectLang
    val (step, total) = remember(ui.events, q) { phaseStep(content, ui.session.role, ui.events, q) }
    val first = remember(ui.events) { ui.events.count { it.type == EventType.SUGGESTED } <= 1 }
    // A question cannot be tapped through by a double tap: Show wakes a second after the card appears.
    var ready by remember(q.id) { mutableStateOf(q.kind == Kind.STATEMENT || q.kind == Kind.DIRECTIVE) }
    LaunchedEffect(q.id) { delay(1_000); ready = true }
    // Capped and scrollable, so on a small phone with large text the conversation above keeps its room.
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.4f
    Surface(shape = MaterialTheme.shapes.large, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, modifier = Modifier.heightIn(max = maxHeight)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.conv_phase_step, stringResource(phaseLabel(q.phase)), step, total),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerLowest) {
                    Text(stringResource(R.string.conv_source_rule), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(q.text.pick(officerLang) ?: q.id, style = MaterialTheme.typography.titleMedium)
            val caption = q.text.pick(subjectLang)
            if (caption == null) {
                Text(stringResource(R.string.media_missing, stringResource(R.string.media_caption)), style = MaterialTheme.typography.bodySmall, color = colors.error)
            } else if (subjectLang != officerLang) {
                Text(caption, style = MaterialTheme.typography.bodyMedium)
            }
            Text(reasonText(p, content, officerLang), style = MaterialTheme.typography.bodySmall)
            if (first || p.reason == Reason.CLOSING) Text(stringResource(R.string.conv_statement_reminder), style = MaterialTheme.typography.bodySmall)
            if (ui.session.role == SessionRole.ACCUSED) Text(stringResource(R.string.conv_rights_reminder), style = MaterialTheme.typography.bodySmall)
            if (q.kind == Kind.STATEMENT) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(advance, role = Role.Switch, onValueChange = auto::setAutoAdvance),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.conv_auto_greetings), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = advance, onCheckedChange = null)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Button(onClick = auto::approve, enabled = ready) { Text(stringResource(R.string.conv_show)) }
                OutlinedButton(onClick = auto::skip) { Text(stringResource(R.string.conv_skip)) }
                TextButton(onClick = auto::pause) { Text(stringResource(R.string.conv_pause)) }
                TextButton(onClick = auto::wrapUp) { Text(stringResource(R.string.conv_wrap_up)) }
            }
        }
    }
}

@Composable
private fun reasonText(p: Proposal, content: ContentBundle, officerLang: String): String = when (p.reason) {
    Reason.FOLLOWS_ANSWER -> {
        val ref = p.q.after?.substringBefore(':')?.let(content::question)
        stringResource(R.string.reason_follows, ref?.text?.pick(officerLang) ?: ref?.id.orEmpty())
    }
    Reason.BREAK_AFTER_UNSURE -> stringResource(R.string.reason_unsure_break)
    Reason.CARRY_ON -> stringResource(R.string.reason_carry_on)
    Reason.CLOSING -> stringResource(R.string.reason_closing)
    Reason.OPENING, Reason.NEXT_PHASE -> stringResource(R.string.reason_next_phase, stringResource(phaseLabel(p.q.phase)))
}

@Composable
private fun Notice(text: String, vararg actions: Pair<Int, () -> Unit>) {
    val answer = LocalAnswerColors.current
    Surface(shape = MaterialTheme.shapes.large, color = answer.pause, contentColor = answer.onPause) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(text, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                actions.forEach { (label, onClick) -> TextButton(onClick = onClick) { Text(stringResource(label), color = answer.onPause) } }
            }
        }
    }
}

@StringRes
private fun phaseLabel(phase: Phase?) = when (phase) {
    Phase.RAPPORT -> R.string.phase_rapport
    Phase.GROUND_RULES -> R.string.phase_ground_rules
    Phase.WELFARE -> R.string.phase_welfare
    Phase.PRACTICAL -> R.string.phase_practical
    Phase.CLOSING -> R.string.phase_closing
    else -> R.string.phase_break
}
