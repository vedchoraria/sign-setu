package app.signsetu

import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.Grade
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.REF_PREFIX
import app.signsetu.ui.session.advancesRun
import app.signsetu.ui.session.grades
import app.signsetu.ui.session.reopensInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadBackTest {
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
        // A Yes to a single reading is the subject's Yes; the reading stays the officer's (FR-66).
        val yes = event(4, EventType.ANSWER_CARD, EventSource.SUBJECT, "yes", ref = 1)
        assertEquals(Grade.SAID, grades(listOf(line, yes))[4])
    }
}
