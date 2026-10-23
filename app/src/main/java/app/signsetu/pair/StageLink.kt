package app.signsetu.pair

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

enum class LinkRole { OFFICER, STAGE }

sealed interface LinkState {
    data object Idle : LinkState
    /** Officer connecting, or stage waiting for the officer. */
    data object Connecting : LinkState
    /** Keys agreed; the stage holds [code], the officer (null) must type it. [check] appears on the officer's phone
     * once it accepts the code, and on the stage once that acceptance arrives. */
    data class Code(val code: String?, val check: String? = null) : LinkState
    data object Live : LinkState
    data object Lost : LinkState
    /** The officer stepped away from the app; the stage waits behind a cover. */
    data object Hold : LinkState
    data class Ended(val reason: String? = null) : LinkState
    /** Pairing failed before Live; [abort] null means the device could not be reached. */
    data class Failed(val abort: Abort?) : LinkState
}

class LinkTiming(
    val connectMs: Long = 12_000,
    val handshakeMs: Long = 15_000,
    val codeMs: Long = 90_000,
    val pingMs: Long = 10_000,
    val lostMs: Long = 30_000,
    val resumeWindowMs: Long = 120_000,
)

/** One officer-to-stage link. All logic runs on [scope], which must be single-threaded; blocking reads and
 * writes go to the IO dispatcher. */
class StageLink(
    val role: LinkRole,
    private val transport: StageTransport,
    private val me: Identity,
    private val scope: CoroutineScope,
    private val timing: LinkTiming = LinkTiming(),
    private val log: (String) -> Unit = { Log.w("SignSetu", it) },
) {
    private val officer = role == LinkRole.OFFICER
    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** Peer's stable id (Bluetooth address), pinned at the first accept or connect; a resume accepts only it. */
    var pinned: String? = null
        private set
    var peerModel: String? = null
        private set
    var peerLabel: String? = null

    private var resumeKey: ByteArray? = null
    private var session: LinkSession? = null
    private var pipe: Pipe? = null
    private var conn: Job? = null
    private var outgoing: Channel<ByteArray>? = null
    private var writer: Job? = null
    private var window: Job? = null
    private var codeTimer: Job? = null
    private var gen = 0
    private var everLive = false
    private var myConfirm = false
    private var peerConfirm = false
    private var lastRx = 0L
    private var code: String? = null
    private var check: String? = null
    private var wrong = 0

    private val officerInbox = Channel<ToOfficer>(Channel.UNLIMITED)
    /** Answers, display confirmations and status from the stage. */
    val fromStage: Flow<ToOfficer> = officerInbox.receiveAsFlow()
    private var rev = 0L
    private var lastShow: ToStage.Show? = null

    private val _view = MutableStateFlow<ToStage.Show?>(null)
    /** The latest snapshot; the stage keeps nothing else. */
    val view: StateFlow<ToStage.Show?> = _view.asStateFlow()
    private val outbox = LinkedHashMap<Int, ToOfficer.Answer>()
    private val _unacked = MutableStateFlow<List<ToOfficer.Answer>>(emptyList())
    val unacked: StateFlow<List<ToOfficer.Answer>> = _unacked.asStateFlow()
    private var nextN = 0

    fun connect(peer: String) = scope.launch {
        if (!officer || _state.value is LinkState.Live) return@launch
        start(resume = false) { open(peer) }
    }

    /** No code and no tap: the resume key proves both sides. */
    fun reconnect() = scope.launch {
        val peer = pinned
        if (!officer || _state.value != LinkState.Lost || peer == null || resumeKey == null) return@launch
        start(resume = true) { open(peer) }
    }

    /** True when the typed digits match; three misses close the link. */
    fun submitCode(typed: String, onResult: (Boolean) -> Unit) = scope.launch {
        val c = code
        if (!officer || c == null || _state.value !is LinkState.Code) return@launch
        if (codeMatches(typed, c)) {
            onResult(true)
            code = null
            _state.value = LinkState.Code(null, check)
            confirm()
        } else {
            onResult(false)
            if (++wrong >= 3) abortPairing(Abort.CODE)
        }
    }

    fun show(itemId: Long?, view: StageView) = scope.launch {
        val m = ToStage.Show(++rev, itemId, view)
        lastShow = m
        if (_state.value == LinkState.Live) send(encodeToStage(m))
    }

    fun ack(itemId: Long, n: Int) = scope.launch { if (up) send(encodeToStage(ToStage.Ack(itemId, n))) }

    fun hold() = scope.launch {
        if (officer && _state.value == LinkState.Live) {
            send(encodeToStage(ToStage.Hold))
            _state.value = LinkState.Hold
        }
    }

    fun unhold() = scope.launch {
        if (officer && _state.value == LinkState.Hold) {
            _state.value = LinkState.Live
            lastShow?.let { send(encodeToStage(it)) }
        }
    }

    fun listen() = scope.launch {
        if (officer) return@launch
        relisten()
    }

    /** The stage's Match, possible only once a peer has accepted the code. Any peer can send that, so the person
     * taps Match only when the officer's phone shows the same check value as this screen. */
    fun match() = scope.launch { if (!officer && !myConfirm && peerConfirm && _state.value is LinkState.Code) confirm() }

    /** Cancel on the code screen: back to waiting for the officer. */
    fun cancelCode() = scope.launch { if (!officer && _state.value is LinkState.Code) abortPairing(null) }

    /** Queued until the officer acks it after logging, and resent after a resume. */
    fun answer(itemId: Long, body: AnswerBody) = scope.launch {
        if (officer || !body.fits()) return@launch
        val live = up
        val a = ToOfficer.Answer(itemId, nextN++, body, late = !live)
        outbox[a.n] = a
        _unacked.value = outbox.values.toList()
        if (live) send(encodeToOfficer(a))
    }

    fun displayed(itemId: Long) = scope.launch { if (_state.value == LinkState.Live) send(encodeToOfficer(ToOfficer.Displayed(itemId))) }

    fun status(s: ToOfficer.Status) = scope.launch { if (up) send(encodeToOfficer(s)) }

    private val up get() = _state.value == LinkState.Live || _state.value == LinkState.Hold

    /** Sends Bye when keys exist, then closes and wipes. Idempotent. */
    fun end(reason: String) = scope.launch {
        if (_state.value is LinkState.Ended) return@launch
        val w = writer
        outgoing?.let { it.trySend(bye(reason)); it.close() }
        withTimeoutOrNull(2_000) { w?.join() }
        finish(LinkState.Ended(reason))
    }

    private fun bye(reason: String) = if (officer) encodeToStage(ToStage.Bye(reason)) else encodeToOfficer(ToOfficer.Bye(reason))

    private suspend fun open(peer: String): Pipe {
        try {
            return withTimeout(timing.connectMs) { transport.connect(peer) }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
        }
        // The first connect after the stage re-registers its service often fails once.
        delay(1_000)
        return withTimeout(timing.connectMs) { transport.connect(peer) }
    }

    private suspend fun relisten() {
        val resume = everLive
        if (!resume) _state.value = LinkState.Connecting
        start(resume) { transport.accept(if (resume) pinned else null) }
    }

    private fun start(resume: Boolean, opener: suspend () -> Pipe) {
        teardown()
        val g = ++gen
        if (!resume) _state.value = LinkState.Connecting
        conn = scope.launch {
            try {
                val p = opener()
                if (resume && p.peer != pinned) { p.close(); throw PairAbort(Abort.PROTOCOL) }
                pipe = p
                val input = DataInputStream(BufferedInputStream(p.input))
                val out = DataOutputStream(BufferedOutputStream(p.output))
                val keys = withTimeout(timing.handshakeMs) {
                    closing(p::close) { if (officer) officerHandshake(input, out, me, resumeKey) else stageHandshake(input, out, me, resumeKey) }
                }
                if (!resume) {
                    resumeKey = keys.resumeKey
                    pinned = p.peer
                    peerModel = keys.peerModel
                }
                val s = LinkSession(input, out, keys, officer)
                session = s
                myConfirm = false
                peerConfirm = false
                lastRx = System.nanoTime()
                val q = Channel<ByteArray>(Channel.UNLIMITED)
                outgoing = q
                coroutineScope {
                    writer = launch { for (b in q) closing(p::close) { s.send(b) } }
                    launch { while (true) handle(closing(p::close) { s.receive() }) }
                    launch { heartbeat() }
                    if (resume) {
                        confirm()
                    } else {
                        code = keys.code
                        check = keys.check
                        wrong = 0
                        _state.value = LinkState.Code(if (officer) null else keys.code)
                        if (!officer) codeTimer = launch { delay(timing.codeMs); throw PairAbort(Abort.CODE) }
                    }
                }
            } catch (e: CancellationException) {
                if (g == gen && e is TimeoutCancellationException) fault(g, e) else throw e
            } catch (e: Exception) {
                fault(g, e)
            }
        }
    }

    private suspend fun heartbeat() {
        val ping = if (officer) encodeToStage(ToStage.Ping) else encodeToOfficer(ToOfficer.Ping)
        while (true) {
            delay(timing.pingMs)
            if ((System.nanoTime() - lastRx) / 1_000_000 > timing.lostMs) throw IOException("silent")
            send(ping)
        }
    }

    private fun send(bytes: ByteArray) {
        outgoing?.trySend(bytes)
    }

    private fun confirm() {
        myConfirm = true
        send(if (officer) encodeToStage(ToStage.Confirm) else encodeToOfficer(ToOfficer.Confirm))
        maybeLive()
    }

    private fun maybeLive() {
        if (!myConfirm || !peerConfirm) return
        codeTimer?.cancel()
        window?.cancel()
        val resumed = everLive
        everLive = true
        _state.value = LinkState.Live
        if (officer) {
            lastShow?.let { send(encodeToStage(it)) }
        } else if (resumed) {
            outbox.replaceAll { _, a -> a.copy(late = true) }
            outbox.values.forEach { send(encodeToOfficer(it)) }
        }
    }

    private suspend fun handle(bytes: ByteArray) {
        lastRx = System.nanoTime()
        val live = up
        if (officer) {
            when (val m = decodeToOfficer(bytes)) {
                ToOfficer.Confirm -> { peerConfirm = true; maybeLive() }
                ToOfficer.Ping -> Unit
                is ToOfficer.Bye -> if (everLive) finish(LinkState.Ended(m.reason)) else abortPairing(null)
                else -> if (live) officerInbox.send(m) else throw PairAbort(Abort.PROTOCOL)
            }
        } else {
            val m = decodeToStage(bytes)
            when {
                m == ToStage.Confirm -> {
                    peerConfirm = true
                    (_state.value as? LinkState.Code)?.let { _state.value = it.copy(check = check) }
                    maybeLive()
                }
                m == ToStage.Ping -> Unit
                m is ToStage.Bye -> if (everLive) finish(LinkState.Ended(m.reason)) else abortPairing(null)
                !live -> throw PairAbort(Abort.PROTOCOL)
                m == ToStage.Hold -> _state.value = LinkState.Hold
                m is ToStage.Ack -> {
                    outbox.remove(m.n)
                    _unacked.value = outbox.values.toList()
                }
                m is ToStage.Show -> {
                    if (_state.value == LinkState.Hold) _state.value = LinkState.Live
                    _view.value = m
                }
            }
        }
    }

    private fun fault(g: Int, e: Throwable) {
        if (g != gen || _state.value is LinkState.Ended) return
        log("link fault ${e.javaClass.simpleName}")
        if (everLive) {
            teardown()
            if (_state.value != LinkState.Lost) {
                _state.value = LinkState.Lost
                window = scope.launch {
                    delay(timing.resumeWindowMs)
                    finish(LinkState.Ended(null))
                }
            }
            if (!officer) scope.launch { delay(500); if (_state.value == LinkState.Lost) relisten() }
        } else {
            abortPairing((e as? PairAbort)?.reason, sendBye = false)
        }
    }

    /** Before Live: the officer sees why; the stage goes back to waiting. */
    private fun abortPairing(reason: Abort?, sendBye: Boolean = true) {
        if (sendBye) send(bye(ByeReason.CANCEL))
        val w = writer
        scope.launch {
            outgoing?.close()
            withTimeoutOrNull(1_000) { w?.join() }
            teardown()
            wipeKeys()
            pinned = null
            if (officer) {
                _state.value = LinkState.Failed(reason)
            } else {
                // A listener that fails at once (Bluetooth just turned off) must not spin.
                delay(1_000)
                relisten()
            }
        }
    }

    private fun teardown() {
        gen++
        // The socket closes first; blocked reads and writes then fail and their coroutines end.
        pipe?.close()
        pipe = null
        conn?.cancel()
        conn = null
        codeTimer?.cancel()
        outgoing?.close()
        outgoing = null
        session?.close()
        session = null
    }

    private fun wipeKeys() {
        resumeKey?.fill(0)
        resumeKey = null
        code = null
        check = null
    }

    private fun finish(end: LinkState.Ended) {
        teardown()
        window?.cancel()
        wipeKeys()
        outbox.clear()
        _unacked.value = emptyList()
        _view.value = null
        lastShow = null
        _state.value = end
    }
}
