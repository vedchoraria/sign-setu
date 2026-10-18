package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Group
import app.signsetu.data.content.Question
import app.signsetu.data.session.DrawingEntity
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionDao
import app.signsetu.data.session.SessionEntity
import app.signsetu.data.session.SessionRepository
import app.signsetu.ui.session.AutoKey
import app.signsetu.ui.session.AutoPilot
import app.signsetu.ui.session.Conv
import app.signsetu.ui.session.PauseCause
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.reportMode
import app.signsetu.data.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutoPilotTest {

    @Serializable private data class Triage(val groups: List<Group>, val questions: List<Question>)

    private val triage = Json { ignoreUnknownKeys = true }.decodeFromString<Triage>(File("src/main/assets/content/triage.json").readText())
    private val bank = ContentBundle(
        groups = triage.groups, questions = triage.questions, categories = emptyList(), cards = emptyMap(),
        lexicon = emptyMap(), languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
    )

    private class Dao : SessionDao {
        val rows = MutableStateFlow<List<EventEntity>>(emptyList())
        val session = MutableStateFlow<SessionEntity?>(null)
        var closed = false
        override suspend fun insertSession(session: SessionEntity) { this.session.value = session }
        override suspend fun openSession() = session.value
        override fun session(id: String): Flow<SessionEntity?> = session
        override suspend fun setSubjectLang(id: String, lang: String) = Unit
        override suspend fun setProfile(id: String, profile: String) = Unit
        override suspend fun setRole(id: String, role: String) { session.value = session.value?.copy(role = role) }
        override suspend fun insertEvent(event: EventEntity): Long {
            check(!closed) { "database closed" }
            val id = rows.value.size + 1L
            rows.value = rows.value + event.copy(id = id)
            return id
        }
        override fun events(sessionId: String): Flow<List<EventEntity>> = rows
        override suspend fun insertDrawing(drawing: DrawingEntity) = 0L
        override suspend fun drawing(id: Long): ByteArray? = null
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun deleteStartedBefore(cutoff: Long) = 0
    }

    /** The view model's side of auto mode, reduced to what the pilot calls. */
    private inner class Phone(private val test: TestScope) {
        // Not backgroundScope: advanceUntilIdle does not wait for background work.
        val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler))
        val dao = Dao()
        val repo = SessionRepository(dao, clock = { dao.rows.value.size.toLong() })
        var showing = false
        private var shownId: Long? = null
        private var id = ""
        lateinit var pilot: AutoPilot

        suspend fun start() {
            id = repo.start(Profile.NOT_SURE, "hi", Role.VICTIM)
            pilot = AutoPilot(id, repo, bank, "en", scope, showing = { showing }, present = { q, lang -> present(q, lang) })
            settle()
        }

        private suspend fun present(q: Question, lang: String) {
            shownId = repo.log(id, EventType.QUESTION_SHOWN, EventSource.PRESET, lang, q.text["en"], itemId = q.id)
            showing = true
        }

        suspend fun manualShow(questionId: String) {
            val q = bank.question(questionId)!!
            pilot.onManualShow(Pending.Ask(q))
            present(q, "hi")
            settle()
        }

        suspend fun answer(card: String) {
            repo.log(id, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", card, cardId = card, shownId = shownId)
            settle()
        }

        fun back() {
            showing = false
            pilot.onReturn()
            settle()
        }

        fun settle() = test.advanceUntilIdle()
        val conv get() = pilot.conv.value
        val proposed get() = (conv as? Conv.Proposed)?.p?.q?.id
        val rows get() = dao.rows.value
        fun act(block: AutoPilot.() -> Unit) { pilot.block(); settle() }

        /** Approves s1 and the four ground rules, leaving the first welfare question proposed. */
        fun throughGroundRules() {
            act { setAuto(true) }
            repeat(5) { act { approve() } }
            back()
        }
    }

    private fun phone(block: suspend Phone.() -> Unit) = runTest {
        val phone = Phone(this)
        try {
            phone.start()
            phone.block()
        } finally {
            phone.scope.cancel()
        }
    }

    @Test
    fun anUnsureAnswerIsNeverProposedAgain() = phone {
        throughGroundRules()
        assertEquals("s2", proposed)
        act { approve() }
        answer("dont_know")
        assertEquals("s3", proposed)
    }

    @Test
    fun approvalIsLoggedBetweenSuggestionAndShowing() = phone {
        act { setAuto(true) }
        act { approve() }
        val s1 = rows.filter { it.itemId == "s1" }.map { it.type }
        assertEquals(listOf(EventType.SUGGESTED.name, EventType.APPROVED.name, EventType.QUESTION_SHOWN.name), s1)
        val i = rows.indexOfFirst { it.type == EventType.APPROVED.name }
        assertEquals(EventType.QUESTION_SHOWN.name, rows[i + 1].type)
    }

    @Test
    fun skippingAndChoosingSomethingElseAreBothLogged() = phone {
        act { setAuto(true) }
        act { skip() }
        assertEquals("officer", rows.last { it.type == EventType.SKIPPED.name }.reason)
        val before = rows.size
        val proposedNow = proposed!!
        manualShow("n1")
        val added = rows.drop(before)
        assertEquals(listOf(EventType.SKIPPED.name, EventType.QUESTION_SHOWN.name), added.map { it.type })
        assertEquals("officer_chose_other" to proposedNow, added[0].reason to added[0].itemId)
    }

    @Test
    fun stopPausesAndASecondStopEndsAuto() = phone {
        throughGroundRules()
        act { approve() }
        answer("stop")
        assertEquals(Conv.Paused(PauseCause.STOP), conv)
        back()
        act { resume() }
        assertEquals("k3", proposed)
        act { approve() }
        answer("stop")
        assertEquals(Conv.Off, conv)
        assertTrue(rows.any { it.text == AutoKey.ENDED_STOP })
    }

    @Test
    fun aBreakRequestPausesAuto() = phone {
        throughGroundRules()
        act { approve() }
        answer("dont_know")
        act { approve() }
        answer("dont_know")
        assertEquals("k1", proposed)
        back()
        act { approve() }
        answer("yes")
        assertEquals(Conv.Paused(PauseCause.BREAK), conv)
        assertTrue(rows.any { it.text == AutoKey.BREAK })
    }

    @Test
    fun statementsAdvanceOnlyOnTheStage() = phone {
        act { setAuto(true) }
        act { setAutoAdvance(true) }
        assertFalse("nothing advances on the officer's screen", (conv as Conv.Proposed).advance)
        act { approve() }
        assertTrue("s1 to g1 waits for the subject's Next", (conv as Conv.Proposed).advance)
        act { advance() }
        assertEquals(EventType.AUTO_ADVANCED.name, rows[rows.size - 3].type)
    }

    @Test
    fun askingAboutEventsByHandTurnsAutoOff() = phone {
        act { setAuto(true) }
        manualShow("x1")
        assertEquals(Conv.Off, conv)
        assertTrue(rows.any { it.text == AutoKey.MODE_MANUAL_SUBSTANTIVE })
    }

    @Test
    fun aTypedQuestionTurnsAutoOffAndTheReportSaysSo() = phone {
        act { setAuto(true) }
        assertEquals(R.string.report_mode_auto, reportMode(rows.map(::event)))
        pilot.onManualShow(Pending.FreeText("What did he do?", emptyList()))
        settle()
        assertEquals(Conv.Off, conv)
        assertEquals(R.string.report_mode_auto_then_manual, reportMode(rows.map(::event)))
    }

    private fun event(e: EventEntity) = SessionEvent(e.id, e.ts, EventType.valueOf(e.type), EventSource.valueOf(e.source), e.lang, e.text, e.cardId, e.lexiconId, e.drawingId)

    @Test
    fun changingTheRoleDropsTheProposalAndAutoAdvance() = phone {
        act { setAuto(true) }
        act { setAutoAdvance(true) }
        act { onRoleChanged() }
        assertEquals("role_changed", rows.last { it.type == EventType.SKIPPED.name }.reason)
        assertFalse(pilot.autoAdvance.value)
    }

    @Test
    fun aClosedDatabaseTurnsAutoOffInsteadOfCrashing() = phone {
        act { setAuto(true) }
        dao.closed = true
        act { skip() }
        assertEquals(Conv.Off, conv)
    }
}
