package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.pair.LinkState
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.pair.Break
import app.signsetu.ui.pair.CheckValue
import app.signsetu.ui.pair.CodeScreen
import app.signsetu.ui.pair.Ended
import app.signsetu.ui.pair.Waiting
import app.signsetu.ui.pair.Cover
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Accessibility Test Framework checks on the paired stage device's covers and pairing check value, in both themes. */
@RunWith(Parameterized::class)
class StageDeviceAccessibilityTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}")
        fun themes() = listOf(false, true)
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repo: ContentRepository
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        shell("settings put global animator_duration_scale 0")
        repo = ContentRepository(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        content = runBlocking { repo.load() }
        rule.enableAccessibilityChecks()
    }

    @After
    fun restore() = shell("settings put global animator_duration_scale 1")

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    private fun show(body: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalContentRepository provides repo) { SignSetuTheme(dark = dark) { SubjectTheme(body) } } }
        rule.onAllNodes(isRoot()).onLast().tryPerformAccessibilityChecks()
    }

    private fun cover(state: LinkState, lang: String = "hi") = show { Cover(state, content, lang, {}, {}, {}) }

    @Test fun holdCover() = cover(LinkState.Hold)

    @Test fun lostCover() = cover(LinkState.Lost)

    /** A language with pictures only: the cover's controls still carry names from the officer's strings. */
    @Test fun coverWithoutWordsInTheSubjectsLanguage() = cover(LinkState.Lost, lang = "bn")

    @Test fun pairingCheckValue() = show { CheckValue("482 913") }

    @Test fun waiting() = show { Waiting("Pixel 7") {} }

    @Test fun codeBeforeCheck() = show { CodeScreen("482913", null, {}, {}) }

    @Test fun fullCodeWithCancelAndMatch() = show { CodeScreen("482913", "482 913", {}, {}) }

    @Test fun breakScreen() = show { Break(content.subjectText("break", "hi") ?: rule.activity.getString(R.string.stage_break)) }

    @Test fun ended() = show { Ended {} }

    @Test
    fun coverOffersStopAndDontKnowEvenWhileHeld() {
        val sent = mutableListOf<String>()
        show { Cover(LinkState.Hold, content, "en", onStop = { sent += "stop" }, onUnsure = { sent += "dont_know" }, onExit = {}) }
        rule.onNodeWithText(rule.activity.getString(R.string.stage_stop), useUnmergedTree = true).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.stage_dont_know), useUnmergedTree = true).performClick()
        assertEquals(listOf("stop", "dont_know"), sent)
    }
}
