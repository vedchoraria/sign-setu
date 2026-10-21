package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.BoardSheet
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.PhraseBoard
import app.signsetu.ui.session.RuleSteps
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.Stage
import app.signsetu.ui.theme.SignSetuTheme
import app.signsetu.ui.theme.SubjectTheme
import app.signsetu.ui.theme.SunMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Accessibility Test Framework checks (labels, contrast, target size) on every stage state and the picture board, in both themes. */
@RunWith(Parameterized::class)
class StageAccessibilityTest(private val dark: Boolean) {

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
        // The character's idle breathing never settles; with animations off (a setting the app honours) the UI goes idle.
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

    private fun show(atf: Boolean = true, body: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalContentRepository provides repo) { SignSetuTheme(dark = dark, content = body) } }
        // A sheet opens its own window; the last root is the one on top.
        if (atf) rule.onAllNodes(isRoot()).onLast().tryPerformAccessibilityChecks()
    }

    private fun stage(pending: Pending?, profile: Profile = Profile.YOUNG_CHILD, answeredCard: String? = null, lang: String = "hi", size: DpSize? = null, atf: Boolean = true, lesson: Boolean = false) {
        val ui = SessionUi(
            session = Session("test", 0L, profile, lang),
            selectedGroup = "safety",
            sensitiveUnlocked = false,
            pending = pending,
            mode = Mode.SHOW,
            rotated = false,
            answeredCard = answeredCard,
            answeredYes = null,
            chosenLanguage = null,
            events = emptyList(),
            reading = null,
            signed = emptyMap(),
        )
        show(atf) {
            SubjectTheme {
                Box(if (size != null) Modifier.size(size) else Modifier) {
                    Stage(ui, content, tracker = null, cameraOn = false, largeCaptions = false, gestureSpeed = 1f, {}, {}, {}, {}, {}, {}, {}, {}, {}, onGiven = if (lesson) { _ -> } else null)
                }
            }
        }
    }

    private fun ask(id: String) = Pending.Ask(content.questions.first { it.id == id })

    @Test fun greeting() = stage(null)

    @Test fun questionWithPictureHeldUp() = stage(ask("s2"))

    @Test fun questionWithChoices() = stage(ask("h1"))

    @Test fun numberPad() = stage(ask("i2"))

    @Test fun signsForAdult() = stage(ask("s1"), Profile.ISL_USER)

    @Test fun phrase() = content.phrases.first().let { stage(Pending.PhraseAsk(it, it.slots.first())) }

    @Test fun languageFinder() = stage(Pending.LanguageFinder, Profile.OTHER_LANGUAGE)

    @Test fun drawing() = stage(Pending.Drawing)

    @Test fun groundRule() = stage(Pending.GroundRules(2))

    @Test fun practiceRound() = stage(Pending.GroundRules(RuleSteps.lastIndex), Profile.ISL_USER)

    @Test fun pauseAfterStop() = stage(ask("s2"), answeredCard = "stop")

    /** A language with pictures only: every control still has a name, from the officer's strings. */
    @Test fun answersWithoutWordsInTheSubjectsLanguage() = stage(ask("h1"), Profile.NOT_SURE, lang = "bn")

    @Test
    fun aLongRightInHindiIsReadToItsEndOnASmallPhone() {
        val q = content.questions.first { it.id == "r6" }
        // The checks run on their own above; here only the caption's layout is read.
        stage(Pending.Ask(q), Profile.NOT_SURE, size = DpSize(360.dp, 640.dp), atf = false)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(q.text.getValue("hi"), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse(layouts.first().hasVisualOverflow)
    }

    @Test
    fun sunMode() {
        SunMode.forced.value = true
        try { stage(ask("h1")) } finally { SunMode.forced.value = false }
    }

    @Test
    fun pictureBoard() = show { BoardSheet(content, "en", emptyList(), setOf("water"), {}, {}, {}) }

    // Lesson-sheet answer inputs, each with the ways out below them.
    @Test fun agePad() = stage(ask("i2"), Profile.ISL_USER, lesson = true)

    @Test fun phonePadWithNoPhone() = stage(ask("p2"), Profile.ISL_USER, lesson = true)

    @Test fun siblingPads() = stage(ask("f8"), Profile.NOT_SURE, lang = "en", lesson = true)

    @Test fun classGrid() = stage(ask("sc2"), Profile.YOUNG_CHILD, lesson = true)

    @Test fun statePicker() = stage(ask("a4"), Profile.ISL_USER, lesson = true)

    @Test fun manyPictures() = stage(ask("f1"), Profile.YOUNG_CHILD, lesson = true)

    @Test fun inputsWithoutWordsInTheSubjectsLanguage() = stage(ask("a5"), Profile.NOT_SURE, lang = "bn", lesson = true)

    @Test fun phraseBoard() = show { SubjectTheme { PhraseBoard(content, "hi", null, {}, {}, {}, Modifier) } }

    @Test fun nameEntry() = stage(ask("p1"), Profile.ISL_USER, lesson = true)
}
