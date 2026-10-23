package app.signsetu

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.ui.practice.PracticeScreen
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The standalone Type-to-Sign surface: typing, the word chips, Sign it, and the way back. */
@RunWith(AndroidJUnit4::class)
class PracticeScreenTest {

    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SignSetuApp
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        shell("settings put global animator_duration_scale 0")
        content = runBlocking { ContentRepository(app.assets).load() }
    }

    @After
    fun restore() = shell("settings put global animator_duration_scale 1")

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Test
    fun aTypedSentenceBecomesWordsAndTheScreenGoesBack() {
        var backed = false
        rule.setContent {
            SignSetuTheme { PracticeScreen(content, onBack = { backed = true }) }
        }

        rule.onNode(hasSetTextAction()).performTextInput("i want water yesterday")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(app.getString(R.string.practice_signed)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(app.getString(R.string.practice_sign)).assertIsEnabled().performClick()
        rule.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        assertTrue("back closes the practice screen", backed)
    }
}
