package app.signsetu

import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.Card
import app.signsetu.data.content.CardCategory
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Given
import app.signsetu.data.content.Group
import app.signsetu.data.content.IslSigns
import app.signsetu.data.content.Method
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.signBack
import app.signsetu.data.content.withSignedQuestions
import app.signsetu.data.session.DrawingEntity
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionDao
import app.signsetu.data.session.SessionEntity
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.SessionReport
import app.signsetu.export.recordDigest
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignReaders
import app.signsetu.sign.SignSegment
import app.signsetu.sign.cardFor
import app.signsetu.sign.glossOf
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.session.Grade
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SOMETHING_ELSE
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.answerSummary
import app.signsetu.ui.session.eventDetail
import app.signsetu.ui.session.grades
import app.signsetu.ui.session.readBackLabels
import app.signsetu.ui.session.recordDetail
import app.signsetu.ui.session.reopensInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * One whole conversation through the view model with the bundled content and the real ISL reader: a question shown,
 * tapped answers with and without a gesture cue, a sign window over recorded landmarks, the officer's reading taught
 * back, the subject's picks and No, and the record built from it all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationE2ETest {

    @Serializable private data class Triage(val groups: List<Group>, val questions: List<Question>)
    @Serializable private data class Cards(val categories: List<CardCategory>, val cards: List<Card>)
    @Serializable private data class Words(val words: List<WordEntry>)
    @Serializable private data class Gestures(val gestures: List<Gesture>)
    @Serializable private data class SignWords(val words: Map<String, String>)

    private val dir = File("src/main/assets/content")
    private val json = Json { ignoreUnknownKeys = true }
    private fun text(name: String) = File(dir, name).readText()
    private val triage = json.decodeFromString<Triage>(text("triage.json"))
    private val cards = json.decodeFromString<Cards>(text("cards.json"))
    private val isl = json.decodeFromString<IslSigns>(text("isl_signs.json"))
    private val content = ContentBundle(
        groups = triage.groups, questions = triage.questions, categories = cards.categories, cards = cards.cards.associateBy { it.id },
        lexicon = emptyMap(), languages = emptyList(),
        subjectUi = json.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())), text("subject_ui.json")),
        rejected = emptySet(),
        gestures = (listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<Gestures>(text(it)).gestures } + isl.gestures.map { it.gesture }).associateBy { it.id },
        words = json.decodeFromString<Words>(text("words.json")).words.flatMap { e -> e.w.map { it to e } }.toMap(),
        isl = isl,
        signWords = json.decodeFromString<SignWords>(text("sign_words.json")).words,
    ).withSignedQuestions()
    private val reader: SignReader = SignReaders.load(SignLanguage.ISL) { File(dir, it).readBytes() }!!

    /** Strings from res/values by resource id, formatted as Android does, so record lines read as they ship. */
    private val res: Resources = run {
        val pattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val strings = File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") }!!.flatMap { f ->
            pattern.findAll(f.readText()).map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'").replace("\\\"", "\"") }
        }.toMap()
        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        object : Resources(null, null, null) {
            override fun getString(id: Int): String = strings.getValue(names.getValue(id))
            override fun getString(id: Int, vararg formatArgs: Any?): String = getString(id).format(*formatArgs)
            override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String = "$quantity unread"
        }
    }

    private class MemoryDao : SessionDao {
        val sessions = MutableStateFlow<Map<String, SessionEntity>>(emptyMap())
        val rows = MutableStateFlow<List<EventEntity>>(emptyList())
        private var next = 1L
        override suspend fun insertSession(session: SessionEntity) { sessions.value += session.id to session }
        override suspend fun openSession() = sessions.value.values.firstOrNull()
        override fun session(id: String): Flow<SessionEntity?> = sessions.map { it[id] }
        override suspend fun setSubjectLang(id: String, lang: String) = Unit
        override suspend fun setProfile(id: String, profile: String) = Unit
        override suspend fun setRole(id: String, role: String) = Unit
        override suspend fun insertEvent(event: EventEntity): Long = synchronized(this) {
            val id = next++
            rows.value += event.copy(id = id)
            id
        }
        override fun events(sessionId: String): Flow<List<EventEntity>> = rows
        override suspend fun insertDrawing(drawing: DrawingEntity) = next++
        override suspend fun drawing(id: Long): ByteArray? = null
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun deleteStartedBefore(cutoff: Long) = 0
    }

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun unmain() = Dispatchers.resetMain()

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

    /** The recorded signs for loud, t-shirt and page from tools/ml/export.py's fixture, each as its own frames. */
    private val signs: List<List<LandmarkFrame>> = run {
        val fixture = Json.parseToJsonElement(javaClass.getResource("/sign_reader_fixture.json")!!.readText()).jsonObject
        val aspect = fixture.getValue("aspect").jsonPrimitive.float
        fixture.getValue("cases").jsonArray.take(3).map { c ->
            c.jsonObject.getValue("frames").jsonArray.map { f ->
                val o = f.jsonObject
                LandmarkFrame(0, leftHand = points(o["left"]), rightHand = points(o["right"]), pose = points(o["pose"]), aspect = aspect)
            }
        }
    }

    /** The signs as one camera stream at 30 fps, with the hands down for 0.66 s before, between and after them. */
    private fun stream(browsUp: Boolean = false): List<LandmarkFrame> {
        val rest = List(20) { LandmarkFrame(0, aspect = signs[0][0].aspect) }
        return (rest + signs.flatMap { it + rest }).mapIndexed { i, f ->
            f.copy(timestampMs = 10_000L + i * 33L, face = if (browsUp) emptyList() else null, browsUp = browsUp)
        }
    }

    private fun TestScope.waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (true) {
            runCurrent()
            if (cond()) return
            if (System.currentTimeMillis() > end) fail("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private fun SessionViewModel.now(): SessionUi = ui.value!!
    private fun SessionViewModel.events(): List<SessionEvent> = now().events

    /** One sign window over [frames]; returns the app's signed line and the segments it read. */
    private fun TestScope.signWindow(vm: SessionViewModel, frames: List<LandmarkFrame>): Pair<SessionEvent, List<SignSegment>> {
        val before = vm.events().size
        vm.startReading()
        assertEquals("the window is open", emptyList<SignSegment>(), vm.now().reading)
        frames.forEach(vm::onSignFrame)
        vm.finishReading()
        waitFor("the signed answer and its note") {
            vm.events().drop(before).let { new -> new.any { it.type == EventType.ANSWER_SIGNED } && new.any { it.type == EventType.NOTE } }
        }
        assertEquals("the window closes", null, vm.now().reading)
        val line = vm.events().drop(before).single { it.type == EventType.ANSWER_SIGNED }
        return line to vm.now().signed.getValue(line.id)
    }

    @Test
    fun somethingElsePagesThroughEveryPictureBeforeTheDrawingPad() = runTest {
        val dao = MemoryDao()
        val repo = SessionRepository(dao)
        val vm = SessionViewModel(repo.start(Profile.ISL_USER, "hi"), repo, content, "en", SessionReport(ContextWrapper(null)), reader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        waitFor("the opening on the stage") { vm.ui.value?.onStage is Pending.GroundRules }
        vm.returnToOfficer()
        val d2 = content.question("d2")!!
        vm.prepare(Pending.Ask(d2))
        vm.show()
        waitFor("the first page") { (vm.now().onStage as? Pending.Ask)?.question?.cards?.lastOrNull() == SOMETHING_ELSE }
        val seen = mutableListOf<String>()
        repeat(2) { page ->
            val cards = (vm.now().onStage as Pending.Ask).question.cards
            assertEquals("page $page ends with a way out", SOMETHING_ELSE, cards.last())
            seen += cards.dropLast(1)
            vm.answerCard(SOMETHING_ELSE)
            if (page < 1) waitFor("page ${page + 2}") { (vm.now().onStage as? Pending.Ask)?.question?.cards?.none { it in seen } == true }
        }
        assertEquals("every colour was offered once", d2.cards.sorted(), seen.sorted())
        waitFor("the drawing pad once all are seen") { vm.now().onStage is Pending.Drawing }
        assertEquals("each page is logged as shown", 2, vm.events().count { it.type == EventType.QUESTION_SHOWN && it.itemId == "d2" })
        vm.submitDrawing(byteArrayOf(1))
        waitFor("the drawing is logged") { vm.events().any { it.type == EventType.ANSWER_DRAWING } }
        assertEquals("the stage keeps what was asked, for its thanks and hand-back", Pending.Drawing, vm.now().pending)
    }

    @Test
    fun aWholeConversationReachesTheRecord() = runTest {
        val dao = MemoryDao()
        var clock = 0L
        val repo = SessionRepository(dao, clock = { clock += 1_000; clock })
        val vm = SessionViewModel(repo.start(Profile.ISL_USER, "hi"), repo, content, "en", SessionReport(ContextWrapper(null)), reader)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        waitFor("the opening on the stage") { vm.ui.value?.onStage is Pending.GroundRules }
        vm.returnToOfficer()
        assertEquals(Mode.OFFICER, vm.now().mode)

        // 1. The officer prepares a question and shows it: it is logged, then it takes the stage.
        val toilet = content.question("n3")!!
        vm.prepare(Pending.Ask(toilet))
        vm.show()
        val n3 = vm.events().last { it.type == EventType.QUESTION_SHOWN }
        assertEquals("n3", n3.itemId)
        assertEquals(Mode.SHOW, vm.now().mode)
        assertEquals(n3.id, vm.now().itemId)
        assertEquals(toilet.id, (vm.now().onStage as Pending.Ask).question.id)
        assertEquals("the prompt is logged as shown", EventType.PROMPT, vm.events().last().type)

        // 2. A tap after a nod carries the cue; the next tap, with no gesture before it, does not.
        vm.noteCue("nod")
        vm.answerYesNo(true)
        vm.answerCard("toilet")
        val taps = vm.events().filter { it.type == EventType.ANSWER_CARD && it.shownId == n3.id }
        assertEquals(listOf("yes", "toilet"), taps.map { it.cardId })
        assertEquals(listOf("cue:nod", null), taps.map { it.reason })
        assertTrue(taps.all { it.source == EventSource.SUBJECT })
        vm.returnToOfficer()

        // 3. A signed answer to "Is your home far?": three signs split at their pauses and read on the phone.
        vm.prepare(Pending.Ask(content.question("l3")!!))
        vm.show()
        val l3 = vm.now().itemId!!
        val (machine, segments) = signWindow(vm, stream())
        assertEquals("one segment per sign", 3, segments.size)
        assertEquals(EventSource.MACHINE, machine.source)
        assertEquals(l3, machine.shownId)
        assertTrue(segments.all { it.context == setOf("home") && it.guesses!!.size in 1..3 })
        val read = segments.map { it.word!! }
        assertEquals(read.joinToString(" "), machine.text)
        // A sure read stays the reader's own: a 0.2% "house" does not outrank a 90% "loud" because the item is about home.
        assertEquals(listOf("loud", "t-shirt", "page"), read)
        val draft = glossToText(segments.flatMap { glossOf(it.guesses!!.first(), reader.words) }, content.words)
        val note = vm.events().last { it.type == EventType.NOTE }
        assertEquals(EventSource.MACHINE, note.source)
        assertEquals("${draft.en} / ${draft.hi}", note.text)
        assertTrue("no raised brows, a statement", draft.en.endsWith("."))
        assertEquals(Grade.MACHINE_READ, grades(vm.events())[machine.id])
        // The subject checks the reading at once on the stage, and the question is still there afterwards.
        waitFor("the reading is put to the subject") { vm.now().confirm?.eventId == machine.id }
        assertEquals("the choices are logged as shown", EventSource.SHOW_BACK, vm.events().last { it.type == EventType.QUESTION_SHOWN }.source)
        assertTrue("only pictures, never an English word", vm.now().confirm!!.queue.flatten().all { it.card != null })
        assertTrue("at most three checks, one per meaning", vm.now().confirm!!.queue.size <= 3 && vm.now().confirm!!.read.distinct().size == vm.now().confirm!!.read.size)
        while (vm.now().confirm != null) vm.chooseSign(null)
        assertEquals("l3", (vm.now().pending as Pending.Ask).question.id)

        // 4. The officer reads the second sign as "police": the record keeps both readings, and the sign is taught.
        val officerWords = listOf(read[0], "police", read[2])
        vm.acceptSigned(machine.id, officerWords)
        val officer = vm.events().last { it.type == EventType.ANSWER_SIGNED }
        assertEquals(EventSource.OFFICER, officer.source)
        assertEquals("police", officer.text!!.split(' ')[1])
        assertEquals(l3, officer.shownId)
        assertEquals(Grade.OFFICER_READ, grades(vm.events())[officer.id])
        val (again, reread) = signWindow(vm, stream(browsUp = true))
        assertEquals("the taught sign is offered first", listOf(read[0], "police", read[2]), reread.map { it.word })
        assertEquals("taught words rank first", "police", reread[1].guesses!!.first().word)
        assertTrue("raised brows make the draft a question", vm.events().last { it.type == EventType.NOTE }.text!!.substringBefore(" / ").endsWith("?"))

        // 5a. Ask them to choose: each set of choices is logged before it shows, read back from the reader's label.
        vm.askSubject(officer.id, officerWords)
        val confirm = vm.now().confirm
        assertNotNull("a sign with two meanings is asked", confirm)
        assertEquals(Mode.SHOW, vm.now().mode)
        assertTrue(confirm!!.queue.all { it.size in 2..3 })
        assertEquals(EventSource.SHOW_BACK, vm.events().last { it.type == EventType.QUESTION_SHOWN }.source)
        assertEquals(EventType.PROMPT, vm.events().last().type)
        val labels = readBackLabels(vm.now())
        assertEquals(listOfNotNull(confirm.read.first()), labels)
        assertTrue("every read label signs back", labels.all { content.signBack(it) != null })
        val pick = confirm.queue.first().first()
        vm.chooseSign(pick)
        val picked = vm.events().last { it.source == EventSource.SUBJECT }
        assertEquals(pick.card, picked.cardId)
        assertEquals("only the subject's pick makes a meaning theirs", Grade.CONFIRMED, grades(vm.events())[picked.id])
        while (vm.now().confirm != null) vm.chooseSign(null)
        val none = vm.events().last { it.source == EventSource.SUBJECT }
        if (confirm.queue.size > 1) assertEquals(Grade.UNCLEAR, grades(vm.events())[none.id])
        vm.returnToOfficer()

        // 5b. Say it back among the item's pictures: the pick of the reading confirms the officer's line.
        vm.sayBack(officer, "police", setOf(SignMode.SIGNS))
        val sayBack = vm.now().onStage as Pending.FreeText
        assertEquals("police", sayBack.meant)
        assertTrue("home" in sayBack.cardIds && "police" in sayBack.cardIds)
        vm.answerCard("police")
        assertEquals(Grade.CONFIRMED, grades(vm.events())[officer.id])
        vm.returnToOfficer()

        // 5c. A plain say-back: No marks the app's line, and the subject gets their own way to say it.
        vm.sayBack(machine, read[0], setOf(SignMode.SIGNS))
        vm.answerYesNo(false)
        assertEquals(Grade.SAID_NO, grades(vm.events())[machine.id])
        assertTrue("No reopens input", reopensInput(vm.now().pending, yes = false))
        assertEquals("the stage stays with the subject", Mode.SHOW, vm.now().mode)
        vm.answerText("it is far")
        val written = vm.events().last()
        assertEquals(EventType.ANSWER_TEXT to vm.now().itemId, written.type to written.shownId)
        vm.returnToOfficer()

        // A lesson-sheet answer read back and confirmed.
        vm.prepare(Pending.Ask(content.question("p2")!!))
        vm.show()
        vm.give(Given("p2", Method.PAD, confirmed = true, value = "9876543210"))
        val given = vm.events().last()
        assertEquals("given:PAD:1", given.reason)
        vm.returnToOfficer()

        // 6. The record: each line graded, the cue and the given method said, readings kept apart, and a stable digest.
        fun export() = vm.events().let { events -> vm.reportRows(recordDetail(res, content, "en", events) { eventDetail(res, it) }) }
        val rows = export()
        val line = rows.associate { it.event.id to it.detail }
        assertTrue(line.getValue(taps[0].id), line.getValue(taps[0].id).startsWith("[Said] Yes (after a nod or thumbs up)"))
        assertFalse(line.getValue(taps[1].id).contains("after a"))
        assertTrue(line.getValue(given.id), line.getValue(given.id).contains("(number pad, confirmed by the person)"))
        assertTrue(line.getValue(machine.id), line.getValue(machine.id).startsWith("[Said no] ${read.joinToString(" ")} (read by the app, not checked)"))
        assertTrue(line.getValue(officer.id), line.getValue(officer.id).startsWith("[Confirmed]"))
        assertTrue(line.getValue(again.id), line.getValue(again.id).startsWith("[App's reading]"))
        assertEquals("only an unconfirmed reading is filed with the readings", listOf(again.id), rows.filter { it.reading }.map { it.event.id })
        assertEquals("n3 rows pair with their question", toilet.text.getValue("en"), rows.first { it.event.id == taps[0].id }.question)
        assertEquals(
            "Answers" to "2 tapped (1 after a gesture), 2 signed, 2 written, 2 gestured",
            answerSummary(res, vm.events()),
        )
        assertEquals("the same session exports to the same digest", recordDigest(rows), recordDigest(export()))
        vm.addNote("checked")
        assertNotEquals(recordDigest(rows), recordDigest(export()))
    }
}
