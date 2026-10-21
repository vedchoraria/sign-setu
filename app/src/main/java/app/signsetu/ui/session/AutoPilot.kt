package app.signsetu.ui.session

import android.database.SQLException
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Auto mode's state. Memory only: after a lock or restart the session reopens in Manual. */
sealed interface Conv {
    data object Off : Conv
    data class Proposed(val p: Proposal, val advance: Boolean = false) : Conv
    /** Something is on the stage; the next proposal waits for its answer or the officer's return. */
    data object Waiting : Conv
    data class Paused(val cause: PauseCause) : Conv
    data object Exhausted : Conv
}

enum class PauseCause { OFFICER, STOP, BREAK }

/**
 * Runs auto mode beside the view model: every proposal, approval and skip is logged before it changes anything, and
 * every decision is re-derived from a fresh read of the log. [present] shows an item exactly as a manual Show does.
 */
class AutoPilot(
    private val sessionId: String,
    private val repo: SessionRepository,
    private val content: ContentBundle,
    private val officerLang: String,
    private val scope: CoroutineScope,
    private val showing: () -> Boolean,
    private val present: suspend (Question, String) -> Unit,
) {
    private val state = MutableStateFlow<Conv>(Conv.Off)
    val conv: StateFlow<Conv> = state.asStateFlow()
    private val advanceOn = MutableStateFlow(false)
    val autoAdvance: StateFlow<Boolean> = advanceOn.asStateFlow()
    private var chain = 0
    private val lock = Mutex()

    init {
        // Answers arrive from the stage through the log, whoever logged them.
        scope.launch {
            var seen: Long? = null
            repo.events(sessionId).collect { events ->
                val answer = events.lastOrNull { it.type in AnswerEvents }
                val before = seen
                seen = answer?.id ?: -1L
                if (answer != null && before != null && answer.id > before) act { onAnswer(answer) }
            }
        }
    }

    fun setAuto(on: Boolean) = act {
        if (on == (state.value != Conv.Off)) return@act
        if (on) {
            system(AutoKey.MODE_AUTO)
            propose()
        } else {
            skipProposed("mode_off")
            system(AutoKey.MODE_MANUAL)
            turnOff()
        }
    }

    fun approve() = act {
        val p = (state.value as? Conv.Proposed)?.p ?: return@act
        mark(EventType.APPROVED, EventSource.OFFICER, p.q)
        show(p.q)
    }

    /** The subject's Next tap on a statement. */
    fun advance() = act {
        val proposed = state.value as? Conv.Proposed ?: return@act
        if (!proposed.advance || !showing()) return@act
        chain++
        mark(EventType.AUTO_ADVANCED, EventSource.RULE, proposed.p.q)
        show(proposed.p.q)
    }

    fun skip() = act {
        if (skipProposed(SKIP_OFFICER)) propose()
    }

    fun pause() = act {
        if (state.value == Conv.Off) return@act
        system(AutoKey.PAUSED)
        state.value = Conv.Paused(PauseCause.OFFICER)
    }

    fun resume() = act {
        if (state.value !is Conv.Paused) return@act
        system(AutoKey.RESUMED)
        propose()
    }

    fun wrapUp() = act {
        if (state.value == Conv.Off) return@act
        system(AutoKey.WRAP_UP)
        propose()
    }

    fun setAutoAdvance(on: Boolean) = act {
        if (state.value == Conv.Off || on == advanceOn.value) return@act
        system(if (on) AutoKey.ADVANCE_ON else AutoKey.ADVANCE_OFF)
        advanceOn.value = on
    }

    /** Called before a manual Show is logged, so the record says the officer chose something else. */
    suspend fun onManualShow(pending: Pending) = lock.withLock {
        guarded {
            if (state.value == Conv.Off) return@guarded
            skipProposed("officer_chose_other")
            val q = (pending as? Pending.Ask)?.question
            // Typed words and show-backs can ask about events, and Auto covers welfare triage only.
            val substantive = pending is Pending.FreeText || pending is Pending.ShowBack ||
                q != null && (content.group(q.group)?.sensitive == true || q.phase == Phase.FREE_RECALL || q.phase == Phase.FOLLOW_UP)
            if (substantive) {
                system(AutoKey.MODE_MANUAL_SUBSTANTIVE)
                turnOff()
            } else {
                state.value = Conv.Waiting
            }
        }
    }

    fun onReturn() = act {
        chain = 0
        if (state.value == Conv.Waiting || (state.value as? Conv.Proposed)?.advance == true) propose()
    }

    fun onRoleChanged() = act {
        if (state.value == Conv.Off) return@act
        skipProposed("role_changed")
        if (advanceOn.value) {
            system(AutoKey.ADVANCE_OFF)
            advanceOn.value = false
        }
        if (state.value !is Conv.Paused) propose()
    }

    private suspend fun onAnswer(answer: SessionEvent) {
        val now = state.value
        if (now == Conv.Off) return
        val events = repo.eventsNow(sessionId)
        val item = events.firstOrNull { it.id == answer.shownId }?.itemId
        when {
            answer.isStop() -> {
                val since = events.indexOfLast { it.type == EventType.SYSTEM && it.text == AutoKey.MODE_AUTO }
                if (events.drop(since + 1).count { it.isStop() && it.type in AnswerEvents } >= 2) {
                    system(AutoKey.ENDED_STOP)
                    turnOff()
                } else {
                    state.value = Conv.Paused(PauseCause.STOP)
                }
            }
            now is Conv.Paused -> Unit
            item == "k1" && answer.cardId == "yes" -> {
                system(AutoKey.BREAK)
                state.value = Conv.Paused(PauseCause.BREAK)
            }
            item == "k3" && answer.cardId != "yes" -> state.value = Conv.Paused(PauseCause.STOP)
            else -> propose()
        }
    }

    private suspend fun show(q: Question) {
        val session = repo.session(sessionId).filterNotNull().first()
        state.value = Conv.Waiting
        present(q, session.subjectLang)
        // A statement asks for no answer, so the next item is ready at once, for the subject's Next tap if allowed.
        if (q.kind == Kind.STATEMENT) propose()
    }

    private suspend fun propose() {
        val session = repo.session(sessionId).filterNotNull().first()
        val events = repo.eventsNow(sessionId)
        val next = next(content, session.role, events)
        if (next == null) {
            state.value = Conv.Exhausted
            return
        }
        if ((state.value as? Conv.Proposed)?.p?.q?.id != next.q.id) mark(EventType.SUGGESTED, EventSource.RULE, next.q, next.reason.name)
        val advance = advanceOn.value && showing() &&
            lastShownAnswer(content, events)?.let { (from, answer) -> autoAdvanceAllowed(from, answer, next.q, session.subjectLang, chain) } == true
        state.value = Conv.Proposed(next, advance)
    }

    private suspend fun skipProposed(reason: String): Boolean {
        val p = (state.value as? Conv.Proposed)?.p ?: return false
        mark(EventType.SKIPPED, EventSource.OFFICER, p.q, reason)
        state.value = Conv.Waiting
        return true
    }

    private fun turnOff() {
        state.value = Conv.Off
        advanceOn.value = false
    }

    // The outcome row and the QUESTION_SHOWN after it are two inserts, not one transaction; a crash between
    // them leaves an approval with nothing shown, which the sequencer reads as not shown.
    private suspend fun mark(type: EventType, source: EventSource, q: Question, reason: String? = null) {
        repo.log(sessionId, type, source, officerLang, text = q.text.pick(officerLang), itemId = q.id, reason = reason)
    }

    private suspend fun system(key: String) {
        repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = key)
    }

    private fun act(block: suspend () -> Unit) {
        scope.launch { lock.withLock { guarded(block) } }
    }

    /** A lock can close the database under a running action; auto mode then stops instead of crashing in a subject's hands. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            turnOff()
        } catch (_: SQLException) {
            turnOff()
        }
    }
}
