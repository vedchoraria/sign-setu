package app.signsetu

import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Given
import app.signsetu.data.content.Method
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import app.signsetu.data.content.answerText
import app.signsetu.data.content.givenOf
import app.signsetu.data.content.groupPhone
import app.signsetu.data.content.padPress
import app.signsetu.data.content.reason
import app.signsetu.data.content.valid
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.Detail
import app.signsetu.ui.session.EVERYDAY
import app.signsetu.ui.session.REF_PREFIX
import app.signsetu.ui.session.personalDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The lesson-sheet questions end to end: content, answer checks, read-back text, report rows, and who is asked what. */
class LessonTest {

    private val content = shipped

    private fun q(id: String) = content.question(id)!!

    @Test
    fun everyLessonQuestionIsInTheBankWithBothLanguagesAndItsPictures() {
        val brief = listOf("p1", "i2", "p2", "p3", "f5", "f6", "f7", "f8", "f1", "a1", "a2", "a3", "a4", "a5", "sc1", "sc2", "sc3", "sc4", "sc5") +
            (1..8).map { "e$it" }
        for (id in brief) {
            val q = q(id)
            listOf("en", "hi").forEach { assertNotNull("$id $it", q.text[it]) }
            q.field?.let { f -> listOf("en", "hi").forEach { assertNotNull("$id field $it", f[it]) } }
            if (q.answer != null) assertNotNull("$id has no field", q.field)
            q.cards.filter { it != "draw" }.forEach { assertTrue("$id: $it", it in content.cards && File(ASSETS, "cards/$it.webp").exists()) }
        }
        assertEquals(content.questions.size, content.questions.map { it.id }.toSet().size)
        assertEquals(12, q("f1").cards.size)
        assertEquals(8, content.questions.count { it.group == EVERYDAY })
    }

    @Test
    fun theGroupCarouselReachesEveryLessonSheetQuestion() {
        val lesson = content.questions.filter { it.answer != null || it.group == EVERYDAY || it.group in setOf("identity", "family", "location", "school") }.map { it.id }.toSet()
        val reached = Role.entries.flatMap { r -> Profile.entries.flatMap { p -> listOf(false, true).flatMap { c -> content.groupsFor(r, c).flatMap { g -> content.questionsIn(g.id, r, p) } } } }.map { it.id }.toSet()
        assertEquals(emptySet<String>(), lesson - reached)
    }

    @Test
    fun placesListEveryStateAndTerritoryWithChhattisgarhFirst() {
        assertEquals(36, content.states.size)
        assertEquals("CG", content.states.first().id)
        assertEquals(33, content.states.first().districts.size)
        content.states.forEach { s -> listOf("en", "hi").forEach { assertNotNull("${s.id} $it", s.name[it]) } }
        content.states.first().districts.forEach { d -> assertNotNull(d.id, d.name["hi"]) }
    }

    @Test
    fun phoneNumbersHaveTenDigitsStartingSixToNine() {
        assertTrue(valid(AnswerType.PHONE, "9876543210"))
        assertFalse(valid(AnswerType.PHONE, "987654321"))
        assertFalse(valid(AnswerType.PHONE, "5876543210"))
        assertFalse(valid(AnswerType.PHONE, "98765x3210"))
        assertEquals("98765 43210", groupPhone("9876543210"))
        val full = "9876543210".fold("") { d, c -> padPress(AnswerType.PHONE, d, c) }
        assertEquals("9876543210", padPress(AnswerType.PHONE, full, '1'))
    }

    @Test
    fun agesCountsAndClassesStayInRange() {
        assertTrue(valid(AnswerType.AGE, "0"))
        assertTrue(valid(AnswerType.AGE, "120"))
        assertFalse(valid(AnswerType.AGE, "121"))
        assertFalse(valid(AnswerType.AGE, ""))
        assertEquals("12", padPress(AnswerType.COUNT, "12", '3'))
        assertFalse(valid(AnswerType.COUNT, "51"))
        assertTrue(valid(AnswerType.SIBLINGS, "2/0"))
        assertFalse(valid(AnswerType.SIBLINGS, "2"))
        assertTrue(valid(AnswerType.CLASS, "12"))
        assertTrue(valid(AnswerType.CLASS, "not_in_school"))
        assertFalse(valid(AnswerType.CLASS, "13"))
    }

    @Test
    fun namesNeedLettersAndNoDigits() {
        assertTrue(valid(AnswerType.NAME, "सीता देवी"))
        assertTrue(valid(AnswerType.NAME, "Ravi Kumar"))
        assertFalse(valid(AnswerType.NAME, "   "))
        assertFalse(valid(AnswerType.NAME, "98765"))
        assertFalse(valid(AnswerType.NAME, "x".repeat(61)))
        assertTrue(valid(AnswerType.TEXT, "Govt. School No. 2"))
    }

    @Test
    fun answersReadBackInTheSubjectsLanguage() {
        assertEquals("माँ, दोस्त", content.answerText(AnswerType.PICTURES, Given("f1", Method.PICTURE, true, picks = listOf("mother", "friends")), "hi"))
        assertEquals("Kharora, Raipur, Chhattisgarh", content.answerText(AnswerType.PLACE, Given("a1", Method.PICKER, true, "Kharora", listOf("CG", "raipur")), "en"))
        assertEquals("बिहार", content.answerText(AnswerType.STATE, Given("a4", Method.PICKER, true, picks = listOf("BR")), "hi"))
        assertEquals("Bihar", content.answerText(AnswerType.STATE, Given("a4", Method.PICKER, true, picks = listOf("BR")), "bn"))
        assertEquals("कक्षा 7", content.answerText(AnswerType.CLASS, Given("sc2", Method.PICKER, true, "7"), "hi"))
        assertEquals("College", content.answerText(AnswerType.CLASS, Given("sc2", Method.PICKER, true, "college"), "en"))
        assertEquals("Brothers 2, Sisters 1", content.answerText(AnswerType.SIBLINGS, Given("f8", Method.PAD, true, "2/1"), "en"))
        assertEquals("I want water.", content.answerText(null, Given("e4", Method.BOARD, true), "en"))
    }

    @Test
    fun howAnAnswerWasGivenSurvivesTheRecord() {
        val g = Given("i2", Method.PAD, true, "12")
        assertEquals(Method.PAD to true, givenOf(g.reason()))
        assertEquals(Method.WRITTEN to false, givenOf(g.copy(method = Method.WRITTEN, confirmed = false).reason()))
        assertEquals(null, givenOf("opening"))
    }

    private fun ev(id: Long, type: EventType, source: EventSource, text: String?, cardId: String? = null, itemId: String? = null, shownId: Long? = null, reason: String? = null, lexiconId: String? = null) =
        SessionEvent(id, id, type, source, "en", text, cardId, lexiconId, null, itemId, reason, shownId)

    @Test
    fun theReportListsEachDetailWithHowAndWhetherConfirmed() {
        val events = listOf(
            ev(1, EventType.QUESTION_SHOWN, EventSource.PRESET, "How old are you?", itemId = "i2"),
            ev(2, EventType.ANSWER_TEXT, EventSource.SUBJECT, "12", itemId = "i2", shownId = 1, reason = Given("i2", Method.PAD, true).reason()),
            ev(3, EventType.QUESTION_SHOWN, EventSource.PRESET, "What is your mobile number?", itemId = "p2"),
            ev(4, EventType.ANSWER_CARD, EventSource.SUBJECT, "No phone", cardId = "no_phone", shownId = 3),
            ev(5, EventType.QUESTION_SHOWN, EventSource.PRESET, "What is your name?", itemId = "p1"),
            ev(6, EventType.ANSWER_TEXT, EventSource.SUBJECT, "Ravi", itemId = "p1", shownId = 5, reason = Given("p1", Method.WRITTEN, false).reason()),
            ev(7, EventType.ANSWER_CARD, EventSource.SUBJECT, "Stop", cardId = "stop", shownId = 5),
            // The phrase board is the subject talking, not a detail, even with a question on screen.
            ev(8, EventType.ANSWER_TEXT, EventSource.SUBJECT, "I want water.", itemId = "e4", shownId = 5, reason = Given("e4", Method.BOARD, true).reason()),
            // A question with no answer type is not a personal detail.
            ev(9, EventType.QUESTION_SHOWN, EventSource.PRESET, "Are you hurt?", itemId = "s2"),
            ev(10, EventType.ANSWER_CARD, EventSource.SUBJECT, "Yes", cardId = "yes", shownId = 9),
            // A signed name: the app's reading, the officer's say-back, and the subject's Yes.
            ev(11, EventType.QUESTION_SHOWN, EventSource.PRESET, "What is your father's name?", itemId = "f5"),
            ev(12, EventType.ANSWER_SIGNED, EventSource.MACHINE, "? ?", shownId = 11),
            ev(13, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, "Mohan", lexiconId = REF_PREFIX + 12),
            ev(14, EventType.ANSWER_CARD, EventSource.SUBJECT, "Yes", cardId = "yes", shownId = 13, lexiconId = REF_PREFIX + 12),
        )
        assertEquals(
            listOf(
                Detail("Age", "12", Method.PAD, true),
                Detail("Mobile number", "No phone", Method.PICTURE, false),
                Detail("Name", "Ravi", Method.WRITTEN, false),
                Detail("Father's name", "Mohan", Method.SIGN, true),
            ),
            personalDetails(events, content, "en"),
        )
        assertEquals("उम्र", personalDetails(events, content, "hi").first().field)
    }

    private fun shown(group: String, role: Role, profile: Profile = Profile.NOT_SURE) = content.questionsIn(group, role, profile).map { it.id }

    @Test
    fun aYoungChildIsNotAskedForAMobileNumber() {
        assertFalse("p2" in shown("identity", Role.VICTIM, Profile.YOUNG_CHILD))
        assertTrue("p2" in shown("identity", Role.VICTIM, Profile.ISL_USER))
        assertTrue("p1" in shown("identity", Role.VICTIM, Profile.YOUNG_CHILD))
    }

    @Test
    fun anAccusedKeepsTheFactualQuestionsButNoWelfareOnlyItems() {
        val school = shown("school", Role.ACCUSED)
        assertEquals(listOf("sc1", "sc2", "sc3"), school)
        assertFalse("p3" in shown("identity", Role.ACCUSED))
        assertTrue(shown("identity", Role.ACCUSED).containsAll(listOf("p1", "i2", "p2")))
        assertTrue(shown("location", Role.ACCUSED).containsAll(listOf("a1", "a2", "a3", "a4", "a5")))
        assertTrue(shown("family", Role.ACCUSED).containsAll(listOf("f1", "f5", "f6", "f7", "f8")))
        assertEquals(5, shown("school", Role.NOT_KNOWN).size)
        val groups = content.groupsFor(Role.ACCUSED).map { it.id }
        assertTrue(groups.containsAll(listOf("identity", "family", "location", "school", "everyday")))
    }

    @Test
    fun lessonQuestionsAreSignedInDraftIslOrder() {
        for (q in content.questions.filter { it.signed }) assertNotNull("${q.id} has no motion", content.gestureFor(q))
        val name = content.planTextIds("What is your name?")
        assertEquals(listOf("isl.you", "isl.name", "isl.what"), name)
        val siblings = content.planTextIds("How many brothers and sisters do you have?")
        assertFalse("sign.how" in siblings)
        assertTrue(siblings.indexOf("isl.how many") > siblings.indexOf("isl.sister") && "isl.sister" in siblings)
    }

    private fun ContentBundle.planTextIds(text: String) = planText(text, setOf(SignMode.SIGNS)).motions
}
