package app.signsetu

import app.signsetu.data.content.BodyLocation
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.HandAction
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.eagerlyChecked
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.settings.whatsappNumber
import app.signsetu.ui.session.withQuestions
import app.signsetu.ui.sanitizeSandesHandoffClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun contentLoadSkipsHashingModels() {
        assertFalse(eagerlyChecked("models/holistic_landmarker.task"))
        assertTrue(eagerlyChecked("triage.json"))
        assertFalse(eagerlyChecked("audio/en/s1.ogg"))
    }

    private fun entry(id: String, locations: List<BodyLocation>, actions: List<HandAction>) =
        LexiconEntry(id, id, "card", locations, actions, mapOf("en" to id), mapOf("en" to ""))

    private val bundle = ContentBundle(
        lexicon = listOf(
            entry("mother.bindi", listOf(BodyLocation.FOREHEAD), listOf(HandAction.TOUCH)),
            entry("police.salute", listOf(BodyLocation.FOREHEAD), listOf(HandAction.FLAT_HAND)),
            entry("eat.fingers", listOf(BodyLocation.MOUTH), listOf(HandAction.TOUCH)),
            entry("home.roof", listOf(BodyLocation.AWAY), listOf(HandAction.BOTH_HANDS)),
        ).associateBy { it.id },
    )

    @Test
    fun onlyAFailedTextFileBlocksSessions() {
        assertFalse(bundle.copy(rejected = setOf("gestures.json", "cards/water.webp")).damaged)
        assertTrue(bundle.copy(rejected = setOf("triage.json")).damaged)
        assertFalse(bundle.copy(files = emptySet()).hasCardImage("water"))
        assertTrue(bundle.copy(files = setOf("cards/water.webp")).hasCardImage("water"))
    }

    @Test
    fun lookupRanksFullMatchThenLocationThenAction() {
        val ids = bundle.lookup(BodyLocation.FOREHEAD, HandAction.TOUCH).map { it.id }
        assertEquals(listOf("mother.bindi", "police.salute", "eat.fingers"), ids)
    }

    @Test
    fun lookupWithNothingSelectedReturnsNothing() {
        assertTrue(bundle.lookup(null, null).isEmpty())
    }

    private var nextId = 0L
    private fun ev(type: EventType, text: String?) =
        SessionEvent(nextId++, nextId, type, EventSource.SUBJECT, null, text, null, null, null)

    @Test
    fun answersPairWithTheQuestionShownBeforeThem() {
        val paired = listOf(
            ev(EventType.QUESTION_SHOWN, "Water?"),
            ev(EventType.ANSWER_CARD, "Yes"),
            ev(EventType.SYSTEM, "language_finder"),
            ev(EventType.LANGUAGE_CHOSEN, "Bengali"),
            ev(EventType.NOTE, "calm"),
        ).withQuestions()
        assertEquals("Water?", paired[1].second)
        assertNull("a language choice is not an answer to the last question", paired[3].second)
        assertNull("notes are never paired", paired[4].second)
    }

    @Test
    fun whatsappNumbersBecomeInternational() {
        assertEquals("919876543210", whatsappNumber("98765 43210"))
        assertEquals("919876543210", whatsappNumber("09876543210"))
        assertEquals("919876543210", whatsappNumber("+91-98765-43210"))
        assertEquals("447700900123", whatsappNumber("+44 7700 900123"))
        assertNull(whatsappNumber("Interpreter desk"))
        assertNull(whatsappNumber("12345"))
    }

    @Test
    fun theInterpreterHandoffCopiesOnlyTheConfiguredContact() {
        assertEquals("+919876543210", sanitizeSandesHandoffClipboard("  +919876543210\n"))
        assertEquals("officer.interpreter.nic", sanitizeSandesHandoffClipboard("officer.interpreter.nic"))
    }

    @Test
    fun assistantTranslatesOnlyWhenTheSubjectReadsSomethingElse() {
        assertTrue(app.signsetu.assistant.needsTranslation("are you hungry?", "hi"))
        assertTrue(app.signsetu.assistant.needsTranslation("are you hungry?", "or"))
        assertFalse(app.signsetu.assistant.needsTranslation("क्या तुम भूखे हो?", "hi"))
        assertFalse(app.signsetu.assistant.needsTranslation("are you hungry?", "en"))
    }
}
