package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.Counsel
import app.signsetu.ui.session.CounselLine
import app.signsetu.ui.session.Counsellor
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.SHOWN_AI
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.counselTranscript
import app.signsetu.ui.session.parseCounselLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CounsellorTest {

    private fun ev(id: Long, type: EventType, text: String? = null, card: String? = null, reason: String? = null, source: EventSource = EventSource.SUBJECT) =
        SessionEvent(id, id, type, source, "hi", text, card, null, null, reason = reason)

    private fun ui(events: List<SessionEvent>) = SessionUi(
        Session("s", 0, Profile.ISL_USER, "hi"), "", false, null, Mode.OFFICER, false, null, null, null, events, null, emptyMap(),
    )

    @Test
    fun repliesBecomeOneShortLineWithItsCaption() {
        assertEquals(
            CounselLine("Hello, I am here to help. Are you comfortable?", "नमस्ते, मैं मदद के लिए हूँ। क्या आप ठीक हैं?", false),
            parseCounselLine("""Sure! {"say": "Hello, I am here to help. Are you comfortable? Who hurt you?", "caption": "नमस्ते, मैं मदद के लिए हूँ। क्या आप ठीक हैं?", "handoff": false}"""),
        )
        assertTrue(parseCounselLine("""{"say": "You are safe now.", "handoff": true}""")!!.handoff)
        assertEquals("Take your time.", parseCounselLine("Take your time.")!!.say)
        assertNull(parseCounselLine("   "))
    }

    @Test
    fun theTranscriptNamesWhoSpokeAndWhatWasPicked() {
        val events = listOf(
            ev(1, EventType.QUESTION_SHOWN, "I am a police officer.", source = EventSource.PRESET),
            ev(2, EventType.QUESTION_SHOWN, "Hello, I am a helper.\nनमस्ते", reason = SHOWN_AI, source = EventSource.MACHINE),
            ev(3, EventType.ANSWER_CARD, card = "water"),
            ev(4, EventType.ANSWER_TEXT, "my uncle"),
        )
        assertEquals(
            "Officer: I am a police officer.\nCounsellor: Hello, I am a helper.\nPerson: Water\nPerson: my uncle",
            counselTranscript(events, shipped),
        )
    }

    @Test
    fun aLineWaitsForTheOfficerThenTheAnswerStartsTheNextTurnAndStopHandsBack() = runTest {
        val flow = MutableStateFlow<SessionUi?>(ui(emptyList()))
        val sent = mutableListOf<String>()
        val c = Counsellor(this, flow, send = { sent += it.say }, holdMs = 6_000)
        var n = 0
        c.start("system", write = { _, _ -> """{"say": "Line ${++n}."}""" }, transcript = { "" })
        runCurrent()
        assertTrue(c.counsel.value is Counsel.Holding)
        c.skip()
        runCurrent()
        assertEquals("Line 2.", (c.counsel.value as Counsel.Holding).line.say)
        // The officer can send at once; left alone, the line still goes out when the hold passes.
        assertTrue(sent.isEmpty())
        advanceTimeBy(6_500)
        runCurrent()
        assertEquals(listOf("Line 2."), sent)
        assertEquals(Counsel.Waiting, c.counsel.value)
        flow.value = ui(listOf(ev(10, EventType.ANSWER_CARD, card = "yes")))
        advanceTimeBy(900)
        runCurrent()
        c.sendNow()
        runCurrent()
        assertEquals(listOf("Line 2.", "Line 3."), sent)
        flow.value = ui(listOf(ev(10, EventType.ANSWER_CARD, card = "yes"), ev(11, EventType.ANSWER_CARD, card = "stop")))
        runCurrent()
        assertEquals(Counsel.Paused(Counsel.Why.STOP), c.counsel.value)
        c.takeOver()
        assertEquals(Counsel.Off, c.counsel.value)
    }
}
