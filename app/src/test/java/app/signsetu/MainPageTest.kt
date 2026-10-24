package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.IslSign
import app.signsetu.data.content.IslSigns
import app.signsetu.data.content.Key
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Signability
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.complete
import app.signsetu.data.content.signability
import app.signsetu.data.content.signabilitySpans
import app.signsetu.data.content.templatesFor
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.BackAction
import app.signsetu.ui.session.Grade
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.PromptRecord
import app.signsetu.ui.session.REF_PREFIX
import app.signsetu.ui.session.SOMETHING_ELSE
import app.signsetu.ui.session.SYSTEM_RUN_ENDED
import app.signsetu.ui.session.WrapUp
import app.signsetu.ui.session.advancesRun
import app.signsetu.ui.session.answerTrails
import app.signsetu.ui.session.backAction
import app.signsetu.ui.session.canSayBack
import app.signsetu.ui.session.choicePositions
import app.signsetu.ui.session.decodePrompt
import app.signsetu.ui.session.encode
import app.signsetu.ui.session.grades
import app.signsetu.ui.session.offered
import app.signsetu.ui.session.prompts
import app.signsetu.ui.session.reopensInput
import app.signsetu.ui.session.signedWords
import app.signsetu.ui.session.unseen
import app.signsetu.ui.session.wrapUp
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageTest {

    private val water = WordEntry(listOf("water", "wa", "paani", "पानी"), sign = "water.cup", card = "water")
    private val wash = WordEntry(listOf("wash", "washing"), card = "wash")
    private val wait = WordEntry(listOf("wait"), gesture = "wait.palm")
    private val bundle = ContentBundle(
        gestures = listOf("water.cup", "wait.palm").associateWith { Gesture(it, listOf(Key(0f))) },
        phrases = listOf(Phrase("want", mapOf("en" to "Do you want …?"), listOf("$"), listOf(PhraseSlot("water", "water.cup", mapOf("en" to "Do you want water?"))))),
        words = listOf(water, wash, wait).flatMap { e -> e.w.map { it to e } }.toMap(),
    )

    @Test
    fun completionsRankSignedFirstAndKeepOneFormPerWord() {
        assertEquals(listOf("water" to Signability.SIGN, "wait" to Signability.GESTURE, "wash" to Signability.PICTURE), bundle.complete("wa").map { it.form to it.kind })
        assertTrue("one letter of Latin is too little", bundle.complete("w").isEmpty())
        assertEquals(listOf("पानी"), bundle.complete("पा").map { it.form })
        assertTrue("the prefix itself is never offered", bundle.complete("water").isEmpty())
    }

    @Test
    fun aWordWithOnlyARecordedSignTintsAsSignable() {
        val recorded = bundle.copy(
            gestures = bundle.gestures + ("isl.teacher" to Gesture("isl.teacher", listOf(Key(0f)))),
            isl = IslSigns(gestures = listOf(IslSign("isl.teacher", "teacher", category = "People", rec = "People/teacher/MVI_1", keys = listOf(Key(0f))))),
        )
        assertEquals(Signability.SIGN, recorded.signability("Teacher"))
        assertEquals(Signability.SPELL, bundle.signability("teacher"))
    }

    @Test
    fun spansMarkWordsThatWillBeSpelled() {
        val spans = bundle.signabilitySpans("Ravi's water")
        assertEquals(listOf(0..5 to Signability.SPELL, 7..11 to Signability.SIGN), spans)
        assertEquals("Do you want water?", bundle.templatesFor("paani").single().second.text["en"])
    }

    private var nextId = 0L
    private fun ev(type: EventType, ts: Long, card: String? = null, text: String? = card, source: EventSource = EventSource.SUBJECT, lexiconId: String? = null) =
        SessionEvent(nextId++, ts, type, source, null, text, card, lexiconId, null)

    @Test
    fun changedAnswersFormOneTrailKeyedByTheLast() {
        val events = listOf(
            ev(EventType.QUESTION_SHOWN, 0, text = "Hurt?"),
            ev(EventType.ANSWER_CARD, 3_000, "yes"),
            ev(EventType.ANSWER_CARD, 5_000, "no"),
            ev(EventType.ANSWER_CARD, 9_000, "yes"),
        )
        val trail = answerTrails(events).getValue(events.last().id)
        assertEquals(listOf("yes", "no", "yes"), trail.answers)
        assertEquals(3_000, trail.latencyMs)
        assertEquals(9_000, trail.finalAfterMs)
        assertTrue(trail.unsteady)
        assertEquals(1, answerTrails(events).size)
    }

    @Test
    fun aStopRecordsHowLongThePauseLasted() {
        val events = listOf(
            ev(EventType.QUESTION_SHOWN, 0, text = "Hurt?"),
            ev(EventType.ANSWER_CARD, 2_000, "stop"),
            ev(EventType.SYSTEM, 2_001, text = "${SYSTEM_RUN_ENDED}1/3", source = EventSource.OFFICER),
            ev(EventType.NOTE, 82_000, text = "calm again", source = EventSource.OFFICER),
        )
        assertEquals(80_000L, answerTrails(events).getValue(events[1].id).pauseMs)
    }

    @Test
    fun aSystemStepEndsTheGroup() {
        val events = listOf(
            ev(EventType.QUESTION_SHOWN, 0, text = "Water?"),
            ev(EventType.ANSWER_CARD, 1_000, "yes"),
            ev(EventType.SYSTEM, 2_000, text = "drawing", source = EventSource.OFFICER),
            ev(EventType.ANSWER_CARD, 30_000, "no"),
        )
        val trails = answerTrails(events)
        assertEquals(listOf("yes"), trails.getValue(events[1].id).answers)
        assertFalse("an answer with no question has no trail", events[3].id in trails)
    }

    @Test
    fun saidBackYesConfirmsAndALaterNoWins() {
        val said = ev(EventType.ANSWER_CARD, 0, "water")
        val read = ev(EventType.ANSWER_SIGNED, 0, text = "mother", source = EventSource.OFFICER)
        val machine = ev(EventType.ANSWER_SIGNED, 0, text = "mother ?", source = EventSource.MACHINE)
        val yes = ev(EventType.ANSWER_GESTURE, 0, "yes", "Water?", lexiconId = "$REF_PREFIX${said.id}")
        val events = listOf(said, read, machine, yes)
        assertEquals(Grade.CONFIRMED, grades(events)[said.id])
        assertEquals(Grade.OFFICER_READ, grades(events)[read.id])
        assertEquals(Grade.MACHINE_READ, grades(events)[machine.id])
        assertEquals(Grade.SAID, grades(listOf(said))[said.id])
        val no = ev(EventType.ANSWER_UNCLEAR, 0, "no", "Water?", lexiconId = "$REF_PREFIX${said.id}")
        assertEquals(Grade.SAID_NO, grades(events + no)[said.id])
        val unanswered = ev(EventType.ANSWER_UNCLEAR, 0, null, "Water?", lexiconId = "$REF_PREFIX${said.id}")
        assertEquals("no answer leaves the line as it was", Grade.CONFIRMED, grades(events + unanswered)[said.id])
        assertFalse(canSayBack(said, Grade.CONFIRMED))
        assertTrue(canSayBack(read, Grade.OFFICER_READ))
    }

    @Test
    fun choicesAreShuffledWithWaysOut() {
        val pictures = listOf("home", "village", "road", "shop", "school", "hospital")
        val a = offered(pictures, Random(7))
        assertEquals(a, offered(pictures, Random(7)))
        assertEquals(5, a.size)
        assertEquals(SOMETHING_ELSE, a.last())
        assertTrue(pictures.containsAll(a.dropLast(1)))
        listOf(listOf("n1", "n2", "n3"), listOf("yes", "no", "dont_know"), listOf("phone", "draw"), listOf("water")).forEach {
            assertEquals(it, offered(it, Random(7)))
        }
    }

    @Test
    fun somethingElseShowsThePicturesNotYetSeenBeforeDrawing() {
        val all = listOf("mother", "father", "brother", "sister", "grandmother", "grandfather", "spouse", "children", "relatives")
        val seen = mutableListOf<String>()
        var left: List<String>? = all
        var pages = 0
        while (left != null) {
            val shown = offered(left, Random(pages))
            assertEquals(SOMETHING_ELSE, shown.last())
            seen += shown.dropLast(1)
            left = unseen(left, shown)
            pages++
        }
        assertEquals("every picture once, four a page, the last alone with a way out", all.sorted(), seen.sorted())
        assertEquals(3, pages)
        assertEquals(null, unseen(listOf("water", "food"), offered(listOf("water", "food"), Random(1))))
    }

    @Test
    fun theRecordKeepsWhatWasShownAndWherePicturesSat() {
        val shown = listOf("road", "home", SOMETHING_ELSE)
        val record = PromptRecord("आप कहाँ रहते हैं?", listOf("home.roof"), shown, "abc123")
        assertEquals(record, decodePrompt(record.encode()))
        assertNull(decodePrompt("not json"))
        val q = ev(EventType.QUESTION_SHOWN, 0, text = "Where do you live?", source = EventSource.PRESET)
        val events = listOf(
            q,
            ev(EventType.PROMPT, 0, text = record.encode(), source = EventSource.OFFICER),
            ev(EventType.ANSWER_CARD, 4_000, "home"),
        )
        assertEquals(record, prompts(events)[q.id])
        assertEquals(2 to 3, choicePositions(events)[events[2].id])
    }

    @Test
    fun unreadSignsNeverShowAsQuestionMarks() {
        assertEquals(null, signedWords("? ? ?"))
        assertEquals("water … home", signedWords("water ? home"))
        assertEquals("water", signedWords("water"))
        assertEquals(null, signedWords(null))
    }

    @Test
    fun backStepsToTheFirstTabBeforeAnythingElse() {
        assertEquals(BackAction.FIRST_TAB, backAction(page = 1, secondScreenShowing = false))
        assertEquals(BackAction.FIRST_TAB, backAction(page = 1, secondScreenShowing = true))
    }

    @Test
    fun leavingWithASecondScreenAsksFirstBecauseItDropsTheLink() {
        assertEquals(BackAction.ASK_LEAVE, backAction(page = 0, secondScreenShowing = true))
    }

    @Test
    fun leavingWithoutASecondScreenIsImmediateAndKeepsTheSessionOpen() {
        assertEquals(BackAction.LEAVE, backAction(page = 0, secondScreenShowing = false))
    }

    private fun ev(id: Long, type: EventType, text: String? = null, shownId: Long? = null) =
        SessionEvent(id, id * 1000, type, EventSource.SUBJECT, "hi", text, null, null, null, shownId = shownId)

    @Test
    fun countsQuestionsLeftOpenButNotGreetingsAndSignsStillToRead() {
        val events = listOf(
            ev(1, EventType.QUESTION_SHOWN, "I am a police officer. I am here to help you."),
            ev(2, EventType.QUESTION_SHOWN, "Are you hurt?"),
            ev(3, EventType.ANSWER_CARD, shownId = 2),
            ev(4, EventType.QUESTION_SHOWN, "Who do you live with?"),
            ev(5, EventType.ANSWER_SIGNED, "mother ?", shownId = 6),
            ev(6, EventType.QUESTION_SHOWN, "Where do you live?"),
        )
        assertEquals(WrapUp(minutes = 12, shown = 4, answers = 2, unanswered = 1, unreadSigns = 1), wrapUp(events, 0, 12 * 60_000L + 5))
    }

    private val sayBack = Pending.FreeText("they hit me", listOf("hit"), ref = 1L)
    private val showBack = Pending.ShowBack(LexiconEntry("hit", "hit", "hit", emptyList(), emptyList(), mapOf("en" to "hit"), mapOf("en" to "")))

    @Test fun noToAReadingReopensInput() {
        assertTrue(reopensInput(sayBack, yes = false))
        assertTrue(reopensInput(showBack, yes = false))
        assertFalse(reopensInput(sayBack, yes = true))
        // A plain question's No is the answer itself; a choice already offers "none of these".
        assertFalse(reopensInput(Pending.FreeText("are you hurt", emptyList()), yes = false))
        assertFalse(reopensInput(sayBack.copy(cardIds = listOf("hit", "push"), meant = "hit"), yes = false))
    }

    @Test fun noThatReopensInputHoldsTheRun() {
        assertFalse(advancesRun(sayBack, yes = false))
        assertFalse(advancesRun(showBack, yes = false))
        assertTrue(advancesRun(sayBack, yes = true))
        assertTrue(advancesRun(Pending.FreeText("are you hurt", emptyList()), yes = false))
    }

    private fun event(id: Long, type: EventType, source: EventSource, card: String?, ref: Long? = null) =
        SessionEvent(id, id, type, source, "en", null, card, ref?.let { REF_PREFIX + it }, null)

    @Test fun pickedMeaningConfirmsAndNoRejects() {
        val line = event(1, EventType.ANSWER_SIGNED, EventSource.OFFICER, null)
        val picked = event(2, EventType.ANSWER_GESTURE, EventSource.SUBJECT, "hit", ref = 1)
        assertEquals(Grade.CONFIRMED, grades(listOf(line, picked))[1])
        val no = event(3, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, "no", ref = 1)
        assertEquals(Grade.SAID_NO, grades(listOf(line, no))[1])
        // A Yes to a single reading is the subject's Yes; the reading stays the officer's.
        val yes = event(4, EventType.ANSWER_CARD, EventSource.SUBJECT, "yes", ref = 1)
        assertEquals(Grade.SAID, grades(listOf(line, yes))[4])
        assertEquals(Grade.OFFICER_READ, grades(listOf(line, yes))[1])
        // A "no" that checks nothing is unclear, not a rejection.
        assertEquals(Grade.UNCLEAR, grades(listOf(event(5, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, "no")))[5])
    }
}
