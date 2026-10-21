package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.WrapUp
import app.signsetu.ui.session.wrapUp
import org.junit.Assert.assertEquals
import org.junit.Test

class WrapUpTest {
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
}
