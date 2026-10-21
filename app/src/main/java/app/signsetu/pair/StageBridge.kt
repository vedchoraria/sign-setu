package app.signsetu.pair

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signsetu.BuildConfig
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.settings.AppSettings
import app.signsetu.ui.pair.LinkKey
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** What the officer pane shows and does while a second screen is in use (D-26). */
@Immutable
class PairedPane(
    val label: String,
    val state: LinkState,
    val status: ToOfficer.Status?,
    val breakPending: Boolean,
    /** Set after a first Show of something already asked; the next tap asks again. */
    val askedAt: Long?,
    val onDone: () -> Unit,
    val onReconnect: () -> Unit,
    val onUseThisPhone: () -> Unit,
    val onBreakGiven: () -> Unit,
    /** Gate for every Show: false while the link is down or a break is pending, or once for a repeat. */
    val allowShow: () -> Boolean,
) {
    val unpinned get() = (state as? LinkState.Ended)?.reason == ByeReason.UNPINNED
}

/** When [p] was last shown in this session, if it was. */
fun askedAt(events: List<SessionEvent>, p: Pending, officerLang: String): Long? = events.lastOrNull { e ->
    e.type == EventType.QUESTION_SHOWN && when (p) {
        is Pending.Ask -> e.text == p.question.text.pick(officerLang)
        is Pending.ShowBack -> e.lexiconId == p.entry.id
        is Pending.FreeText -> e.text == listOfNotNull(p.text, p.caption).joinToString("\n")
        is Pending.PhraseAsk -> e.text == p.slot.text.pick(officerLang) && e.cardId == p.slot.card
        else -> false
    }
}?.ts

/** Model, build, content and the last 4 hex of the hashed address: never the Bluetooth name, which may name a person. */
private fun connectedDetail(link: StageLink, contentHash: String): String {
    val address = sha256(link.pinned.orEmpty().toByteArray()).toHexString().takeLast(4)
    return "${link.peerModel.orEmpty()} v${BuildConfig.VERSION_CODE} ${contentHash.take(8)} $address"
}

/**
 * Mounted by the session screen while this phone has an officer link: sends snapshots of what is on stage, logs
 * every answer before acking it, and records the link's life in the session log.
 */
@Composable
fun rememberPairedStage(
    vm: SessionViewModel,
    link: StageLink,
    settings: AppSettings,
    cameraOn: Boolean,
    officerLang: String,
    onFallback: () -> Unit,
): PairedPane {
    val state by link.state.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<ToOfficer.Status?>(null) }
    var breakPending by rememberSaveable { mutableStateOf(false) }
    var asked by remember { mutableStateOf<Long?>(null) }
    var warnedFor by remember { mutableStateOf<Pending?>(null) }
    var wasLive by rememberSaveable { mutableStateOf(false) }
    var logged by rememberSaveable { mutableStateOf("") }
    val confirmed = remember { mutableSetOf<Long>() }
    val look by rememberUpdatedState(Triple(settings.largeCaptions, if (settings.slowGestures) 0.75f else 1f, cameraOn))

    val activity = LocalActivity.current
    DisposableEffect(vm, link) {
        vm.paired = true
        // A fallback has already cleared [paired]; any other loss of the pane must not leave a stage on this phone.
        onDispose { if (vm.paired && activity?.isChangingConfigurations != true) vm.leavePaired() }
    }
    LaunchedEffect(vm, link) {
        combine(vm.ui.filterNotNull(), snapshotFlow { look }) { ui, (large, speed, camera) -> ui.itemId to ui.toStageView(large, speed, camera) }
            .distinctUntilChanged()
            .collect { (id, view) -> link.show(id, view) }
    }
    LaunchedEffect(vm, link) {
        link.fromStage.collect { m ->
            when (m) {
                is ToOfficer.Answer -> {
                    val fresh = vm.logRemote(m.itemId, m.n, m.body, m.late, from = link)
                    link.ack(m.itemId, m.n)
                    if (fresh && (m.body as? AnswerBody.Card)?.id == "stop") breakPending = true
                }
                is ToOfficer.Displayed -> confirmed += m.itemId
                // An error report carries no battery reading (it sends 0), so it must not replace the last real one.
                is ToOfficer.Status -> if (m.error == null) status = m
                else -> Unit
            }
        }
    }
    LaunchedEffect(vm, link) {
        link.state.collect { s ->
            val key = when {
                s == LinkState.Live && !wasLive -> {
                    vm.logLink(LinkKey.CONNECTED, connectedDetail(link, vm.content.manifestHash))
                    wasLive = true
                    LinkKey.CODE_VERIFIED
                }
                s == LinkState.Live && logged == LinkKey.LOST -> LinkKey.RESUMED
                s == LinkState.Lost -> LinkKey.LOST
                s == LinkState.Hold -> LinkKey.HOLD
                s is LinkState.Ended -> if (s.reason == ByeReason.UNPINNED) LinkKey.UNPINNED else LinkKey.ENDED
                else -> null
            }
            if (s == LinkState.Live && key == null) logged = "live"
            if (key != null && key != logged) {
                vm.logLink(key)
                logged = key
                val item = vm.ui.value?.itemId
                if (key == LinkKey.LOST && item != null && item !in confirmed) vm.logLink(LinkKey.DISPLAY_NOT_CONFIRMED, "$item")
            }
        }
    }

    return PairedPane(
        label = link.peerLabel ?: link.peerModel.orEmpty(),
        state = state,
        status = status,
        breakPending = breakPending,
        askedAt = asked,
        onDone = vm::returnToOfficer,
        onReconnect = { link.reconnect() },
        onUseThisPhone = onFallback,
        onBreakGiven = { vm.breakGiven(); breakPending = false },
        allowShow = {
            val ui = vm.ui.value
            val p = ui?.pending
            val before = if (ui != null && p != null) askedAt(ui.events, p, officerLang) else null
            when {
                link.state.value != LinkState.Live || breakPending -> false
                p != null && before != null && p != warnedFor -> { warnedFor = p; asked = before; false }
                else -> { warnedFor = null; asked = null; true }
            }
        },
    )
}
