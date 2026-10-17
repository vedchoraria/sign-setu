package app.signsetu.ui.session

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftsSaverTest {
    private val scope = SaverScope { true }

    @Test
    fun draftsKeyedByEventIdSurviveSaveRestore() {
        val drafts = mutableMapOf("say:7" to "water", "sign:9" to "a\u0001b", "show" to "c1,c2")
        val saved = with(DraftsSaver) { scope.save(drafts) }!!
        assertEquals(drafts, DraftsSaver.restore(saved))
    }
}
