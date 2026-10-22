package app.signsetu

import app.signsetu.ui.session.BackAction
import app.signsetu.ui.session.backAction
import org.junit.Assert.assertEquals
import org.junit.Test

class BackActionTest {
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
}
