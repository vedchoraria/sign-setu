package app.signsetu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.content.pick
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.MorePage
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The More page is search-only for its list, so a category chip must lead to that category's questions. */
@RunWith(AndroidJUnit4::class)
class MoreBrowseTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repo: ContentRepository
    private lateinit var content: ContentBundle

    @Before
    fun load() {
        repo = ContentRepository(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        content = runBlocking { repo.load() }
    }

    @Test
    fun aCategoryChipOpensThatCategorysQuestions() {
        val role = Role.VICTIM
        val profile = Profile.NOT_SURE
        val group = content.groupsFor(role).first { content.questionsIn(it.id, role, profile).isNotEmpty() }
        val label = group.label.pick("en") ?: group.id
        val question = content.questionsIn(group.id, role, profile).first()
        val title = question.text.pick("en")!!

        val ui = SessionUi(
            session = Session("test", 0L, profile, "hi", role),
            selectedGroup = "",
            sensitiveUnlocked = false,
            pending = null,
            mode = Mode.OFFICER,
            rotated = false,
            answeredCard = null,
            answeredYes = null,
            chosenLanguage = null,
            events = emptyList(),
            reading = null,
            signed = emptyMap(),
        )
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides repo) {
                SignSetuTheme {
                    Box {
                        MorePage(
                            ui = ui,
                            content = content,
                            officerLang = "en",
                            answers = 0,
                            onGroup = {},
                            onQuestion = {},
                            onPhrase = {},
                            onAddToRun = {},
                            onTool = {},
                            onLog = {},
                            onChange = {},
                            onSecondScreen = null,
                            onFinish = {},
                            onCounsel = null,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Browse by category").assertExists()
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithText(title, substring = true).performScrollTo().assertExists()
        // The chip row is replaced by results, so the page has one clear state at a time.
        rule.onNodeWithText("Browse by category").assertDoesNotExist()
    }
}
