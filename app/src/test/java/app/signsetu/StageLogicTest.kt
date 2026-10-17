package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Question
import app.signsetu.data.content.WordBeat
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.RuleSteps
import app.signsetu.ui.session.StagePace
import app.signsetu.ui.session.beatAt
import app.signsetu.ui.session.buzzPattern
import app.signsetu.ui.session.glanceText
import app.signsetu.ui.session.initialPace
import app.signsetu.ui.session.nextPace
import app.signsetu.ui.session.pauseSeconds
import app.signsetu.ui.session.ruleText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StageLogicTest {

    private var id = 0L
    private val none = { _: String -> false }
    private fun event(type: EventType, text: String?, cardId: String? = null, ts: Long = 0) =
        SessionEvent(++id, ts, type, EventSource.SUBJECT, "en", text, cardId, null, null)

    @Test
    fun glanceShowsTheLastQuestionAndItsLatestAnswer() {
        assertNull(glanceText(emptyList(), none))
        val asked = event(EventType.QUESTION_SHOWN, "Hurt?")
        assertEquals("Hurt?", glanceText(listOf(asked), none))
        assertEquals("Hurt? → YES", glanceText(listOf(asked, event(EventType.ANSWER_CARD, "Yes", "yes")), none))
        // A machine reading is nobody's answer yet.
        assertEquals("Hurt?", glanceText(listOf(asked, event(EventType.ANSWER_SIGNED, "pain")), none))
        val long = glanceText(listOf(event(EventType.QUESTION_SHOWN, "Where were you before the police found you?\nsecond line")), none)!!
        assertEquals(32, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun glanceNeverShowsAWrittenAnswer() {
        val asked = event(EventType.QUESTION_SHOWN, "Who hurt you?")
        assertEquals("Who hurt you?", glanceText(listOf(asked, event(EventType.ANSWER_TEXT, "Ravi Kumar")), none))
    }

    @Test
    fun glanceNeverShowsAPersonalDetail() {
        val personal = { item: String -> item == "name" }
        val asked = event(EventType.QUESTION_SHOWN, "Your name?").copy(itemId = "name")
        val typed = event(EventType.ANSWER_TEXT, "Ravi Kumar").copy(shownId = asked.id)
        assertEquals("Your name?", glanceText(listOf(asked, typed), personal))
        val signed = event(EventType.ANSWER_SIGNED, "ravi").copy(shownId = asked.id)
        val sayBack = event(EventType.QUESTION_SHOWN, "Ravi").copy(lexiconId = "ref:${signed.id}")
        assertNull(glanceText(listOf(asked, signed, sayBack), personal))
        val other = event(EventType.QUESTION_SHOWN, "Hurt?").copy(itemId = "h1")
        assertEquals("Hurt? → YES", glanceText(listOf(other, event(EventType.ANSWER_CARD, "Yes", "yes")), personal))
    }

    @Test
    fun buzzCodes() {
        assertArrayEquals(longArrayOf(0, 400), buzzPattern(true, null))
        assertArrayEquals(longArrayOf(0, 120, 120, 120), buzzPattern(false, null))
        listOf("stop", "break", "dont_know", "dont_understand", "wrong").forEach {
            assertArrayEquals(longArrayOf(0, 80, 100, 80, 100, 80), buzzPattern(null, it))
        }
        assertArrayEquals(longArrayOf(0, 30), buzzPattern(null, "water"))
        assertNull(buzzPattern(null, null))
    }

    @Test
    fun paceLastsOneSessionAndCycles() {
        StagePace.forSession("a", 0.75f)
        StagePace.speed.floatValue = 0.5f
        StagePace.glanceHidden.value = true
        StagePace.forSession("a", 0.75f)
        assertEquals(0.5f, StagePace.speed.floatValue)
        StagePace.forSession("b", 1f)
        assertEquals(1f, StagePace.speed.floatValue)
        assertEquals(false, StagePace.glanceHidden.value)

        assertEquals(listOf(0.75f, 1f, 0.5f), listOf(0.5f, 0.75f, 1f).map(::nextPace))
        assertEquals(0.75f, initialPace(Profile.YOUNG_CHILD, 1f))
        assertEquals(1f, initialPace(Profile.ISL_USER, 1f))
    }

    @Test
    fun tappedOffsetFindsItsWord() {
        val beats = listOf(WordBeat(0..3, null, null, 0f, 1f), WordBeat(5..9, "water", null, 1f, 2f))
        assertEquals("water", beatAt(beats, 7)?.card)
        assertNull(beatAt(beats, 4))
    }

    @Test
    fun pauseLengthRunsFromTheLastStopOrBreak() {
        val events = listOf(event(EventType.ANSWER_CARD, "Break", "break", ts = 10_000), event(EventType.ANSWER_CARD, "Yes", "yes", ts = 20_000))
        assertEquals(32L, pauseSeconds(events, 42_000))
        assertNull(pauseSeconds(events.drop(1), 42_000))
    }

    @Test
    fun everyOpeningStepAndStripAnswerHasWordsAndPictures() {
        val json = Json { ignoreUnknownKeys = true }
        val content = File("src/main/assets/content")
        val subjectUi = json.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())), File(content, "subject_ui.json").readText())
        val triage = json.parseToJsonElement(File(content, "triage.json").readText()).jsonObject
        val questions = json.decodeFromJsonElement(ListSerializer(Question.serializer()), triage.getValue("questions"))
        val bundle = ContentBundle(
            groups = emptyList(), questions = questions, categories = emptyList(), cards = emptyMap(),
            lexicon = emptyMap(), languages = emptyList(), subjectUi = subjectUi, rejected = emptySet(),
        )
        val gestures = File(content, "gestures.json").readText()
        RuleSteps.forEach { step ->
            listOf("en", "hi").forEach { assertNotNull("${step.key} $it", ruleText(bundle, step.key, it)) }
            assertTrue(step.gesture, "\"id\": \"${step.gesture}\"" in gestures)
            step.card?.let { assertTrue(it, File(content, "cards/$it.webp").exists()) }
        }
        listOf("yes", "no", "dont_know", "dont_understand", "wrong", "break", "stop", "ready", "next").forEach { key ->
            listOf("en", "hi").forEach { assertNotNull("$key $it", subjectUi[key]?.get(it)) }
        }
        listOf("dont_understand", "wrong", "break", "stop").forEach { assertTrue(it, File(content, "cards/$it.webp").exists()) }
    }
}
