package app.signsetu

import android.Manifest
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assert
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.SkeletonOnly
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.data.settings.AppSettings
import app.signsetu.ui.SessionRoute
import app.signsetu.ui.components.LocalContentRepository
import app.signsetu.ui.theme.SignSetuTheme
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real session screen on a device: an encrypted database, the bundled content and the emulator's camera, with
 * only the landmark model replaced. A question with pictures goes from the officer pane to the stage, the subject taps,
 * a nod makes Yes pulse, the officer reads the graded lines back, and Skeleton only still draws the camera window.
 */
@RunWith(AndroidJUnit4::class)
class StageEndToEndTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SignSetuApp
    private lateinit var db: SessionDatabase

    /** A face whose nose bobs while [nodding], and a body, so the overlay analyses at its usual rate. */
    private class NoddingTracker : LandmarkTracker {
        @Volatile var nodding = false
        @Volatile var frames = 0
        override fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame {
            val face = MutableList(468) { Offset(0.5f, 0.5f) }
            face[33] = Offset(0.4f, 0.4f)
            face[263] = Offset(0.6f, 0.4f)
            face[1] = Offset(0.5f, if (nodding && frames % 2 == 0) 0.62f else 0.5f)
            frames++
            return LandmarkFrame(timestampMs, face = face, pose = List(33) { Offset(0.5f, 0.6f) })
        }
    }

    /** No screen pinning or prompt in a test activity; the department setting that would prompt is off here. */
    private object TestGuard : DeviceGuard {
        override fun unlock(title: String, subtitle: String, onSuccess: () -> Unit, onFailure: () -> Unit) = onSuccess()
        override fun confirmOfficer(title: String, subtitle: String, cancel: String, onSuccess: () -> Unit) = onSuccess()
        override fun confirmPinned(title: String, subtitle: String, cancel: String, onSuccess: () -> Unit) = onSuccess()
        override fun pin() = Unit
        override fun unpin() = Unit
    }

    @Before
    fun setUp() {
        shell("settings put global animator_duration_scale 0")
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        System.loadLibrary("sqlcipher")
        db = Room.inMemoryDatabaseBuilder(app, SessionDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 7 }))
            .build()
    }

    @After
    fun tearDown() {
        SkeletonOnly.on.value = false
        app.container.stageGuard.onStage(false)
        db.close()
        shell("settings put global animator_duration_scale 1")
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun str(id: Int) = app.getString(id)

    private fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun waitFor(matcher: SemanticsMatcher, timeout: Long = 15_000) = rule.waitUntil(timeout) { count(matcher) > 0 }

    @Test
    fun aQuestionTheNodAndTheRecordOnTheRealScreens() {
        val container = app.container
        val content = runBlocking { container.content.load() }
        val repo = SessionRepository(db.dao())
        val id = runBlocking { repo.start(Profile.NOT_SURE, "en") }
        fun answers() = runBlocking { repo.eventsNow(id) }.filter { it.type == EventType.ANSWER_CARD }
        val tracker = NoddingTracker()
        val settings = AppSettings(firstRunDone = true, tiltToShow = false, volumeKeys = false)
        rule.setContent {
            CompositionLocalProvider(LocalContentRepository provides container.content) {
                SignSetuTheme {
                    SessionRoute(id, repo, content, settings, TestGuard, container.report, tracker, null, null, {}, {}, container, null)
                }
            }
        }

        // The session opens on the stage with the ground rules; the officer takes the phone back by the handle.
        val handle = hasContentDescription(str(R.string.show_officer))
        waitFor(handle)
        rule.onNode(handle).performClick()

        // 1. The officer picks "Does it hurt anywhere?" from its group and shows it.
        val h1 = content.question("h1")!!
        rule.onNodeWithText(str(R.string.officer_tab_more)).performClick()
        rule.onNodeWithText(content.groups.first { it.id == h1.group }.label.pick("en")!!).performClick()
        rule.onNodeWithText(h1.text.getValue("en")).performClick()
        rule.onNodeWithContentDescription(str(R.string.officer_show)).performClick()

        // 2. On the stage the subject taps a picture, then No; neither follows a gesture. Four of six pictures show, shuffled.
        val pictures = h1.cards.associateWith { hasContentDescription(content.cards.getValue(it).label.pick("en")!!) }
        rule.waitUntil(15_000) { pictures.values.any { count(it) == 1 } }
        val (picked, picture) = pictures.entries.first { count(it.value) == 1 }
        rule.onNode(picture).performClick()
        val no = hasContentDescription(content.subjectText("no", "en")!!)
        rule.onNode(no).performClick()
        rule.waitUntil(5_000) { answers().size == 2 }

        // 3. A nod on camera only makes Yes pulse; the tap after it is the answer, and its line names the nod.
        val yes = hasContentDescription(content.subjectText("yes", "en")!!)
        val suggested = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.stage_proposed))
        rule.waitUntil(15_000) { tracker.frames > 10 }
        rule.onNode(yes).assert(!suggested)
        assertEquals("nothing is logged for a gesture", 2, answers().size)
        tracker.nodding = true
        waitFor(yes and suggested)
        tracker.nodding = false
        rule.onNode(yes).performClick()
        rule.waitUntil(5_000) { answers().size == 3 }
        val taps = answers()
        assertEquals(listOf(picked, "no", "yes"), taps.map { it.cardId })
        assertEquals(listOf(null, null, "cue:nod"), taps.map { it.reason })

        // 4. Back on the officer pane, each answer carries its grade, and the Yes says it came after a nod.
        rule.onNode(handle).performClick()
        waitFor(hasText(str(R.string.officer_tab_more)))
        val cue = "(${str(R.string.cue_nod)})"
        waitFor(hasText(cue, substring = true))
        assertEquals(1, rule.onAllNodesWithText(cue, substring = true).fetchSemanticsNodes().size)
        assertEquals(3, rule.onAllNodesWithText(str(R.string.grade_said)).fetchSemanticsNodes().size)

        // 5. Skeleton only from the officer menu: the stage's camera window still draws, without the picture.
        rule.onNodeWithContentDescription(str(R.string.officer_more)).performClick()
        rule.onNodeWithText(str(R.string.officer_skeleton_only)).performClick()
        assertTrue(SkeletonOnly.on.value)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        val water = content.question("n1")!!.text.getValue("en")
        rule.onNodeWithText(water).performClick()
        rule.onNodeWithContentDescription(str(R.string.officer_show)).performClick()
        waitFor(handle)
        val seen = tracker.frames
        rule.waitUntil(15_000) { tracker.frames > seen + 5 }
        val skeletonPane = 0xFF1E2A33.toInt()
        rule.waitUntil(15_000) {
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            var n = 0
            for (x in 0 until pixels.width step 4) for (y in 0 until pixels.height step 4) if (pixels[x, y].toArgb() == skeletonPane) n++
            n > 500
        }
    }
}
