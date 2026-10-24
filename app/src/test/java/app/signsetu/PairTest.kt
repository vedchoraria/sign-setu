package app.signsetu

import android.content.ContextWrapper
import app.signsetu.data.content.Card
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignMode
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionRepository
import app.signsetu.export.SessionReport
import app.signsetu.pair.Abort
import app.signsetu.pair.AnswerBody
import app.signsetu.pair.FRAME_CAP
import app.signsetu.pair.Hello1
import app.signsetu.pair.Identity
import app.signsetu.pair.LinkKeys
import app.signsetu.pair.LinkRole
import app.signsetu.pair.LinkSession
import app.signsetu.pair.LinkState
import app.signsetu.pair.LinkTiming
import app.signsetu.pair.PairAbort
import app.signsetu.pair.Peer
import app.signsetu.pair.Pipe
import app.signsetu.pair.Reveal
import app.signsetu.pair.StageLink
import app.signsetu.pair.StageTransport
import app.signsetu.pair.StageView
import app.signsetu.pair.ToOfficer
import app.signsetu.pair.ToStage
import app.signsetu.pair.WirePending
import app.signsetu.pair.b64
import app.signsetu.pair.closing
import app.signsetu.pair.decodeP256
import app.signsetu.pair.drawingBoundsOk
import app.signsetu.pair.encodeToOfficer
import app.signsetu.pair.fits
import app.signsetu.pair.officerHandshake
import app.signsetu.pair.readPlain
import app.signsetu.ui.pair.resolve
import app.signsetu.pair.sha256
import app.signsetu.pair.stageHandshake
import app.signsetu.ui.pair.toStageView
import app.signsetu.ui.pair.toWire
import app.signsetu.pair.writePlain
import app.signsetu.ui.session.Mode
import app.signsetu.ui.session.Pending
import app.signsetu.ui.session.SessionUi
import app.signsetu.ui.session.SessionViewModel
import app.signsetu.ui.session.VIA_STAGE_LATE
import app.signsetu.ui.session.withQuestions
import app.signsetu.ui.session.Grade
import app.signsetu.ui.session.grades
import app.signsetu.ui.session.checkChoices
import app.signsetu.ui.session.asksChoice
import app.signsetu.ui.session.SOMETHING_ELSE
import org.junit.Assert.assertNotEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException

@OptIn(ExperimentalCoroutinesApi::class)
class PairTest {

    private val me = Identity(7, "abc123", "Test Phone")
    private val pool = Executors.newCachedThreadPool()

    @After
    fun stopPool() {
        pool.shutdownNow()
    }

    /** Two directions of piped bytes; closing either end closes both, like a dropped socket. */
    private class Duplex(officerId: String = "officer", stageId: String = "stage") {
        private val o2s = PipedOutputStream()
        private val s2o = PipedOutputStream()
        private val stageIn = PipedInputStream(o2s, 2 shl 20)
        private val officerIn = PipedInputStream(s2o, 2 shl 20)
        val officer = Pipe(officerIn, o2s, stageId) { closeAll() }
        val stage = Pipe(stageIn, s2o, officerId) { closeAll() }
        fun closeAll() {
            runCatching { o2s.close() }
            runCatching { s2o.close() }
        }
    }

    private fun Pipe.data() = DataInputStream(input) to DataOutputStream(output)

    private fun handshake(officerResume: ByteArray? = null, stageResume: ByteArray? = null, stageMe: Identity = me): Pair<Result<LinkKeys>, Result<LinkKeys>> {
        val d = Duplex()
        val (oi, oo) = d.officer.data()
        val (si, so) = d.stage.data()
        val o = pool.submit(Callable { runCatching { officerHandshake(oi, oo, me, officerResume) }.also { if (it.isFailure) d.closeAll() } })
        val s = pool.submit(Callable { runCatching { stageHandshake(si, so, stageMe, stageResume) }.also { if (it.isFailure) d.closeAll() } })
        return o.get(10, TimeUnit.SECONDS) to s.get(10, TimeUnit.SECONDS)
    }

    private fun abortOf(r: Result<LinkKeys>) = (r.exceptionOrNull() as? PairAbort)?.reason

    // 1
    @Test
    fun bothSidesDeriveTheSameCodeAndMirroredKeys() {
        val (o, s) = handshake()
        val ok = o.getOrThrow()
        val sk = s.getOrThrow()
        assertEquals(ok.code, sk.code)
        assertTrue(ok.code.matches(Regex("\\d{6}")))
        assertEquals(ok.check, sk.check)
        assertTrue(ok.check.matches(Regex("\\d{4}")))
        assertArrayEquals(ok.send, sk.receive)
        assertArrayEquals(ok.receive, sk.send)
        assertFalse(ok.send.contentEquals(ok.receive))
        assertArrayEquals(ok.resumeKey, sk.resumeKey)
        assertEquals("Test Phone", sk.peerModel)
    }

    // 2
    @Test
    fun aRevealThatBreaksTheCommitmentAborts() {
        val d = Duplex()
        val (oi, oo) = d.officer.data()
        val (si, so) = d.stage.data()
        val stage = pool.submit(Callable { runCatching { stageHandshake(si, so, me, null) } })
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val committed = gen.generateKeyPair().public.encoded
        val revealed = gen.generateKeyPair().public.encoded
        val n = ByteArray(32).also(SecureRandom()::nextBytes)
        writePlain(oo, Json.encodeToString(Hello1.serializer(), Hello1(1, me.versionCode, me.contentHash, me.model, "new", sha256(committed, n).b64())).toByteArray())
        readPlain(oi)
        writePlain(oo, Json.encodeToString(Reveal.serializer(), Reveal(revealed.b64(), n.b64())).toByteArray())
        assertEquals(Abort.COMMIT, abortOf(stage.get(10, TimeUnit.SECONDS)))
    }

    // 3
    @Test
    fun aDifferentBuildOrContentAborts() {
        val (o, s) = handshake(stageMe = me.copy(contentHash = "other"))
        assertEquals(Abort.VERSION, abortOf(o))
        assertEquals(Abort.VERSION, abortOf(s))
        val (o2, s2) = handshake(stageMe = me.copy(versionCode = 8))
        assertEquals(Abort.VERSION, abortOf(o2))
        assertEquals(Abort.VERSION, abortOf(s2))
    }

    // 4
    @Test
    fun aKeyOffP256Aborts() {
        val p384 = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp384r1")) }.generateKeyPair().public.encoded
        assertAbort(Abort.KEY) { decodeP256(p384) }
        val real = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair().public as ECPublicKey
        val offCurve = runCatching {
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(real.w.affineX, real.w.affineY.add(BigInteger.ONE)), real.params)).encoded
        }.getOrNull()
        if (offCurve != null) assertAbort(Abort.KEY) { decodeP256(offCurve) }
        assertEquals(real, decodeP256(real.encoded))
    }

    // 5
    @Test
    fun anOversizedHandshakeFrameIsRefusedBeforeAllocation() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(4 * 1024 + 1) }.toByteArray()
        assertAbort(Abort.SIZE) { readPlain(DataInputStream(ByteArrayInputStream(bytes))) }
    }

    // 6
    @Test
    fun aStalledPeerHitsTheHandshakeTimeout() = runBlocking {
        val d = Duplex()
        val (oi, oo) = d.officer.data()
        val started = System.nanoTime()
        try {
            withTimeout(300) { closing(d.officer::close) { officerHandshake(oi, oo, me, null) } }
            fail("no timeout")
        } catch (_: TimeoutCancellationException) {
        } catch (_: java.io.IOException) {
            // The watchdog closed the pipe under the blocked read, which is how a socket times out.
        }
        // It blocked until the deadline, not failing at once on something else.
        assertTrue((System.nanoTime() - started) / 1_000_000 in 250..5_000)
    }

    private fun sender(keys: LinkKeys, wire: ByteArrayOutputStream) =
        LinkSession(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire), keys, officer = true)

    private fun receiver(keys: LinkKeys, bytes: ByteArray) =
        LinkSession(DataInputStream(ByteArrayInputStream(bytes)), DataOutputStream(ByteArrayOutputStream()), keys, officer = false)

    // 7
    @Test
    fun aFlippedBitFailsTheTag() {
        val (o, s) = handshake()
        val wire = ByteArrayOutputStream()
        LinkSession(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire), o.getOrThrow(), officer = true).send("hello".toByteArray())
        val bytes = wire.toByteArray().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        try {
            receiver(s.getOrThrow(), bytes).receive()
            fail("tampered frame opened")
        } catch (_: AEADBadTagException) {
        }
    }

    // 8
    @Test
    fun replayedAndSkippedCountersAreRefused() {
        val (o, s) = handshake()
        val sk = s.getOrThrow()
        // A session zeroes the arrays it is given, so each receiver gets its own copy.
        val (k1, k2, k3) = List(3) { LinkKeys(sk.send.copyOf(), sk.receive.copyOf(), "", null, "", "") }
        val wire = ByteArrayOutputStream()
        val out = sender(o.getOrThrow(), wire)
        out.send("a".toByteArray())
        val first = wire.toByteArray()
        out.send("b".toByteArray())
        val second = wire.toByteArray().copyOfRange(first.size, wire.size())
        assertEquals("b", receiver(k1, first + second).run { receive(); receive() }.decodeToString())
        assertAbort(Abort.PROTOCOL) { receiver(k2, first + first).run { receive(); receive() } }
        assertAbort(Abort.PROTOCOL) { receiver(k3, second).receive() }
    }

    // 9
    @Test
    fun anOversizedEncryptedFrameIsRefusedBeforeAllocation() {
        val (_, s) = handshake()
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(FRAME_CAP + 1); writeLong(0) } }.toByteArray()
        assertAbort(Abort.SIZE) { receiver(s.getOrThrow(), bytes).receive() }
    }

    // 10
    @Test
    fun resumeNeedsTheResumeKey() {
        val (o, s) = handshake()
        val rk = o.getOrThrow().resumeKey!!
        val (ro, rs) = handshake(officerResume = rk.copyOf(), stageResume = rk.copyOf())
        val wire = ByteArrayOutputStream()
        LinkSession(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire), ro.getOrThrow(), officer = true).send(encodeToStageConfirm())
        assertNull("a resume issues no new resume key", rs.getOrThrow().resumeKey)
        assertEquals(ToStage.Confirm, app.signsetu.pair.decodeToStage(receiver(rs.getOrThrow(), wire.toByteArray()).receive()))
        assertNotNull(s.getOrThrow())

        val wrong = ByteArray(32).also(SecureRandom()::nextBytes)
        val (wo, ws) = handshake(officerResume = rk.copyOf(), stageResume = wrong)
        val wire2 = ByteArrayOutputStream()
        LinkSession(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire2), wo.getOrThrow(), officer = true).send(encodeToStageConfirm())
        try {
            receiver(ws.getOrThrow(), wire2.toByteArray()).receive()
            fail("a stage without the resume key opened the frame")
        } catch (_: AEADBadTagException) {
        }
        // A resume offered to a stage expecting a new pairing is refused by both sides, and the other way round.
        val (mo, ms) = handshake(officerResume = rk.copyOf(), stageResume = null)
        assertEquals(Abort.MODE, abortOf(mo))
        assertEquals(Abort.MODE, abortOf(ms))
        val (no, ns) = handshake(officerResume = null, stageResume = rk.copyOf())
        assertEquals(Abort.MODE, abortOf(no))
        assertEquals(Abort.MODE, abortOf(ns))
    }

    private fun encodeToStageConfirm() = app.signsetu.pair.encodeToStage(ToStage.Confirm)

    // 11
    @Test
    fun aFullDrawingFitsAFrameAndLongTextIsRefused() {
        val png = ByteArray(600_000).also(SecureRandom()::nextBytes)
        val body = AnswerBody.Drawing(java.util.Base64.getEncoder().encodeToString(png))
        assertTrue(body.fits())
        val signedBody = AnswerBody.Signed(listOf("police", "help"), java.util.Base64.getEncoder().encodeToString(png))
        assertTrue(signedBody.fits())
        assertTrue(encodeToOfficer(ToOfficer.Answer(1, 0, signedBody)).size + 16 < FRAME_CAP)
        assertFalse(AnswerBody.Text("x".repeat(2_001)).fits())
        assertTrue(AnswerBody.Text("x".repeat(2_000)).fits())
    }

    private val question = Question("q1", "safety", mapOf("en" to "Are you hurt?"), cards = listOf("yes"), lexicon = "pain")
    private val entry = LexiconEntry("pain", "pain", "yes", emptyList(), emptyList(), mapOf("en" to "Pain"), mapOf("en" to ""))
    private val slot = PhraseSlot("water", text = mapOf("en" to "Do you want water?"))
    private val phrase = Phrase("want", mapOf("en" to "Want"), listOf("want", Phrase.SLOT), listOf(slot))
    private val bundle = ContentBundle(
        questions = listOf(question),
        cards = listOf("yes", "no", "water", "stop").associateWith { Card(it, "c", mapOf("en" to it)) },
        lexicon = mapOf(entry.id to entry),
        phrases = listOf(phrase),
    )

    // 12
    @Test
    fun everyItemSurvivesTheWire() {
        val items = listOf(
            Pending.Ask(question),
            Pending.FreeText("hello", listOf("water"), setOf(SignMode.SIGNS), caption = "नमस्ते"),
            Pending.Cards(listOf("yes", "no")),
            Pending.ShowBack(entry),
            Pending.PhraseAsk(phrase, slot),
            Pending.LanguageFinder,
            Pending.Drawing,
        )
        items.forEach { p ->
            val json = app.signsetu.pair.WireJson.encodeToString(WirePending.serializer(), p.toWire())
            assertEquals(p, app.signsetu.pair.WireJson.decodeFromString(WirePending.serializer(), json).resolve(bundle))
        }
        assertNull(WirePending.Ask("nope").resolve(bundle))
        assertNull(WirePending.Cards(listOf("nope")).resolve(bundle))
        assertNull(WirePending.PhraseAsk("want", "nope").resolve(bundle))
    }

    private fun ui(pending: Pending?, onStage: Pending?, mode: Mode) = SessionUi(
        Session("s", 0, Profile.ISL_USER, "hi"), "safety", false, pending, mode, false, null, null, null, emptyList(), null, emptyMap(), onStage, 3L,
    )

    // 13
    @Test
    fun theStageSeesWhatIsOnStageNeverTheDraft() {
        assertNull(ui(Pending.Drawing, null, Mode.OFFICER).toStageView(false, 1f, true).item)
        val view = ui(Pending.Cards(listOf("no")), Pending.Ask(question), Mode.SHOW).toStageView(false, 1f, true)
        assertTrue(view.live)
        assertEquals(WirePending.Ask("q1"), view.item)
        assertFalse(ui(null, Pending.Ask(question), Mode.OFFICER).toStageView(false, 1f, true).live)
    }

    @Test
    fun aSignedAnswerIsSignedBackFromTheReadersTopLabel() {
        val read = app.signsetu.sign.SignSegment(emptyList(), listOf(app.signsetu.sign.SignGuess("water", 0.8f), app.signsetu.sign.SignGuess("home", 0.1f)))
        val base = ui(null, null, Mode.SHOW).copy(signed = mapOf(7L to listOf(read)))
        val confirm = app.signsetu.sign.SignConfirm(7L, listOf(listOf(app.signsetu.sign.SignChoice("water", "water"), app.signsetu.sign.SignChoice("home", "home"))), listOf("water"))
        assertEquals(listOf("water"), app.signsetu.ui.session.readBackLabels(base.copy(confirm = confirm)))
        assertEquals(listOf("water"), app.signsetu.ui.session.readBackLabels(base.copy(pending = Pending.FreeText("paani", emptyList(), ref = 7L))))
        assertEquals(emptyList<String>(), app.signsetu.ui.session.readBackLabels(base.copy(pending = Pending.FreeText("paani", emptyList()))))
    }

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun unmain() = Dispatchers.resetMain()

    private fun vmTest(paired: Boolean = true, body: suspend kotlinx.coroutines.test.TestScope.(SessionViewModel, MemoryDao) -> Unit) = runTest {
        val dao = MemoryDao()
        val repo = SessionRepository(dao, clock = { 1L })
        val id = repo.start(Profile.ISL_USER, "hi")
        val vm = SessionViewModel(id, repo, bundle, "en", SessionReport(ContextWrapper(null)))
        vm.paired = paired
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ui.collect {} }
        body(vm, dao)
    }

    private fun MemoryDao.events() = rows.value.map { it.type to it }

    // 14
    @Test
    fun preparingWhileLiveLeavesTheStageAlone() = vmTest { vm, _ ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val before = vm.ui.value!!.toStageView(false, 1f, true)
        vm.prepare(Pending.Cards(listOf("water")))
        assertEquals(before, vm.ui.value!!.toStageView(false, 1f, true))
        assertEquals(Pending.Cards(listOf("water")), vm.ui.value!!.pending)
    }

    // 15
    @Test
    fun twoAnswersToOneItemAreBothLogged() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val item = vm.ui.value!!.itemId!!
        vm.logRemote(item, 0, AnswerBody.YesNo(true))
        vm.toggleRotation()
        vm.logRemote(item, 1, AnswerBody.YesNo(false))
        val answers = dao.rows.value.filter { it.type == EventType.ANSWER_CARD.name }
        assertEquals(listOf("yes", "no"), answers.map { it.cardId })
        assertTrue(answers.all { it.shownId == item && it.via == "stage" })
    }

    @Test
    fun aGestureCueFromTheStagePhoneReachesTheRecord() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val item = vm.ui.value!!.itemId!!
        vm.logRemote(item, 0, AnswerBody.YesNo(true, cue = "nod"))
        vm.logRemote(item, 1, AnswerBody.Card("stop", cue = "palm"))
        vm.logRemote(item, 2, AnswerBody.YesNo(false))
        val answers = dao.rows.value.filter { it.type == EventType.ANSWER_CARD.name }
        assertEquals(listOf("cue:nod", "cue:palm", null), answers.map { it.reason })
    }

    // 16
    @Test
    fun aLateAnswerStaysWithItsQuestion() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val first = vm.ui.value!!.itemId!!
        vm.prepare(Pending.Cards(listOf("water")))
        vm.show()
        vm.logRemote(first, 0, AnswerBody.Card("stop"))
        val events = vm.ui.value!!.events
        val (answer, q) = events.withQuestions().last()
        assertEquals(EventType.ANSWER_CARD, answer.type)
        assertEquals("Are you hurt?", q)
        assertEquals(VIA_STAGE_LATE, answer.via)
        assertEquals(VIA_STAGE_LATE, dao.rows.value.last().via)
    }

    // 17
    @Test
    fun aResentAnswerIsLoggedOnce() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val item = vm.ui.value!!.itemId!!
        vm.logRemote(item, 4, AnswerBody.Text("my house"))
        vm.logRemote(item, 4, AnswerBody.Text("my house"), late = true)
        assertEquals(1, dao.rows.value.count { it.type == EventType.ANSWER_TEXT.name })
    }

    @Test
    fun aPairedGivenIsCommittedWithStageProvenance() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val first = vm.ui.value!!.itemId!!
        // Nothing the view model launches may run before the row is checked.
        Dispatchers.setMain(StandardTestDispatcher())
        vm.logRemote(first, 0, AnswerBody.Given("q1", "WRITTEN", confirmed = true, value = "home"))
        vm.logRemote(first, 1, AnswerBody.Given("q1", "WRITTEN", confirmed = true, value = "again"), late = true)
        val given = dao.rows.value.filter { it.type == EventType.ANSWER_TEXT.name }
        assertEquals(listOf("stage", VIA_STAGE_LATE), given.map { it.via })
        assertTrue(given.all { it.shownId == first })
    }

    @Test
    fun aLateUnconfirmedGivenForAClosedItemIsLogged() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val first = vm.ui.value!!.itemId!!
        vm.prepare(Pending.Cards(listOf("water")))
        vm.show()
        vm.logRemote(first, 0, AnswerBody.Given("q1", "WRITTEN", confirmed = false, value = "home"))
        val row = dao.rows.value.last { it.type == EventType.ANSWER_TEXT.name }
        assertEquals(VIA_STAGE_LATE, row.via)
        assertEquals(first, row.shownId)
        assertEquals("given:WRITTEN:0", row.reason)
    }

    @Test
    fun aHeldUnconfirmedGivenKeepsItsProvenanceWhenTheItemCloses() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val first = vm.ui.value!!.itemId!!
        vm.logRemote(first, 0, AnswerBody.Given("q1", "WRITTEN", confirmed = false, value = "home"))
        assertTrue(dao.rows.value.none { it.type == EventType.ANSWER_TEXT.name })
        vm.prepare(Pending.Cards(listOf("water")))
        vm.show()
        val row = dao.rows.value.single { it.type == EventType.ANSWER_TEXT.name }
        assertEquals("stage", row.via)
        assertEquals("given:WRITTEN:0", row.reason)
    }

    @Test
    fun aSignedAnswerFromStageLogsTranscriptAndSkeletonEvidence() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val item = vm.ui.value!!.itemId!!
        val png = ByteArray(1024).also(SecureRandom()::nextBytes)
        val b64 = java.util.Base64.getEncoder().encodeToString(png)
        vm.reencodePng = { it }
        vm.logRemote(item, 0, AnswerBody.Signed(listOf("police", "help"), b64))
        assertNull("snapshot is opt-in", dao.rows.value.firstOrNull { it.type == EventType.ANSWER_DRAWING.name })

        vm.show()
        val on = app.signsetu.camera.EvidenceMode.autoCapture
        on.value = true
        try { vm.logRemote(item, 1, AnswerBody.Signed(listOf("police", "help"), b64)) } finally { on.value = false }

        val signedRow = dao.rows.value.firstOrNull { it.type == EventType.ANSWER_SIGNED.name }
        assertNotNull(signedRow)
        assertEquals("police help", signedRow?.text)
        assertEquals("stage", signedRow?.via)
        // The stage's reader made the reading; only the subject's confirmation makes a meaning theirs.
        assertEquals(EventSource.MACHINE.name, signedRow?.source)

        val drawingRow = dao.rows.value.firstOrNull { it.type == EventType.ANSWER_DRAWING.name }
        assertNotNull(drawingRow)
        assertEquals("evidence:skeleton", drawingRow?.reason)
        assertEquals("stage", drawingRow?.via)
        assertEquals(EventSource.MACHINE.name, drawingRow?.source)
    }

    // 18
    @Test
    fun replacingAnUnansweredShowBackIsTheOfficersAct() = vmTest { vm, dao ->
        vm.prepare(Pending.ShowBack(entry))
        vm.show()
        vm.prepare(Pending.Ask(question))
        vm.show()
        val unclear = dao.rows.value.single { it.type == EventType.ANSWER_UNCLEAR.name }
        assertEquals(EventSource.OFFICER.name, unclear.source)
    }

    @Test
    fun singlePhoneReturnLogsTheOfficerClosingIt() = vmTest(paired = false) { vm, dao ->
        vm.prepare(Pending.ShowBack(entry))
        vm.show()
        vm.returnToOfficer()
        val unclear = dao.rows.value.single { it.type == EventType.ANSWER_UNCLEAR.name }
        assertEquals(EventSource.OFFICER.name, unclear.source)
        assertNull(vm.ui.value!!.pending)
    }

    // 19
    @Test
    fun linkRowsDoNotBreakQuestionPairing() = vmTest { vm, _ ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        vm.logLink("lost")
        vm.answerCard("yes")
        val (answer, q) = vm.ui.value!!.events.withQuestions().last()
        assertEquals(EventType.ANSWER_CARD, answer.type)
        assertEquals("Are you hurt?", q)
    }

    // 20
    @Test
    fun hugeRemoteDrawingsAreRefusedOnTheirBounds() {
        assertFalse(drawingBoundsOk(30_000, 30_000))
        assertFalse(drawingBoundsOk(0, 10))
        assertTrue(drawingBoundsOk(1080, 1920))
    }

    private class FakeHub {
        val waiting = Channel<Pipe>(Channel.UNLIMITED)
        var last: Duplex? = null

        fun side(id: String) = object : StageTransport {
            override fun peers(): Flow<List<Peer>> = flowOf(emptyList())
            override suspend fun connect(peer: String): Pipe {
                val d = Duplex(officerId = id, stageId = peer)
                last = d
                waiting.send(d.stage)
                return d.officer
            }
            override suspend fun accept(onlyPeer: String?): Pipe {
                while (true) {
                    val p = waiting.receive()
                    if (onlyPeer == null || p.peer == onlyPeer) return p
                    p.close()
                }
            }
        }
    }

    @Test
    fun pairShowAnswerLoseAndResume() = runBlocking {
        val hub = FakeHub()
        val timing = LinkTiming(connectMs = 3_000, handshakeMs = 3_000, pingMs = 100, lostMs = 2_000, resumeWindowMs = 20_000)
        val oScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val sScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val officer = StageLink(LinkRole.OFFICER, hub.side("officer-1"), me, oScope, timing) {}
        val stage = StageLink(LinkRole.STAGE, hub.side("stage-1"), me, sScope, timing) {}
        try {
            withTimeout(15_000) {
                stage.listen()
                officer.connect("stage-1")
                val code = (stage.state.first { it is LinkState.Code } as LinkState.Code).code!!
                officer.state.first { it is LinkState.Code }
                var results = listOf<Boolean>()
                officer.submitCode("000000".takeIf { it != code } ?: "111111") { results = results + it }.join()
                officer.submitCode(code) { results = results + it }.join()
                assertEquals(listOf(false, true), results)
                // Both screens show the same check value before the stage may go live.
                val officerCheck = (officer.state.value as LinkState.Code).check!!
                val stageCheck = (stage.state.first { (it as? LinkState.Code)?.check != null } as LinkState.Code).check
                assertEquals(officerCheck, stageCheck)
                stage.match()
                officer.state.first { it == LinkState.Live }
                stage.state.first { it == LinkState.Live }

                val view = StageView("hi", "ISL_USER", true, WirePending.Ask("q1"), false, false, 1f, true)
                officer.show(5L, view)
                assertEquals(view, stage.view.first { it != null }!!.view)

                stage.answer(5L, AnswerBody.YesNo(true))
                val a = officer.fromStage.first { it is ToOfficer.Answer } as ToOfficer.Answer
                assertFalse(a.late)
                officer.ack(a.itemId, a.n)
                stage.unacked.first { it.isEmpty() }

                // The link drops; an answer given meanwhile arrives after the resume, marked late.
                hub.last!!.closeAll()
                officer.state.first { it == LinkState.Lost }
                stage.state.first { it == LinkState.Lost }
                stage.answer(5L, AnswerBody.Card("stop"))
                officer.reconnect()
                officer.state.first { it == LinkState.Live }
                val late = officer.fromStage.first { it is ToOfficer.Answer } as ToOfficer.Answer
                assertTrue(late.late)
                assertEquals(AnswerBody.Card("stop"), late.body)

                officer.end("end")
                stage.state.first { it is LinkState.Ended }
                assertNull(stage.view.value)
            }
        } finally {
            oScope.cancel()
            sScope.cancel()
        }
    }

    @Test
    fun aPeerThatConfirmsWithoutTheCodeShowsItsOwnCheckValue() = runBlocking {
        val hub = FakeHub()
        val timing = LinkTiming(connectMs = 3_000, handshakeMs = 3_000, pingMs = 100, lostMs = 2_000)
        val sScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        val stage = StageLink(LinkRole.STAGE, hub.side("stage-1"), me, sScope, timing) {}
        try {
            withTimeout(15_000) {
                stage.listen()
                val pipe = hub.side("rogue").connect("stage-1")
                val (input, output) = pipe.data()
                val keys = kotlinx.coroutines.withContext(Dispatchers.IO) { officerHandshake(input, output, me, null) }
                val code = stage.state.first { it is LinkState.Code } as LinkState.Code
                // Before any peer has accepted the code, Match does nothing and shows no check value.
                stage.match().join()
                assertEquals(code, stage.state.value)
                assertNull(code.check)
                // The rogue confirms at once without anyone typing; the stage shows the rogue's own check value, which the
                // officer's phone, never connected, cannot show.
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    LinkSession(input, output, keys, officer = true).send(app.signsetu.pair.encodeToStage(ToStage.Confirm))
                }
                val shown = stage.state.first { (it as? LinkState.Code)?.check != null } as LinkState.Code
                assertEquals(keys.check, shown.check)
                assertTrue(stage.state.value is LinkState.Code)
                pipe.close()
            }
        } finally {
            sScope.cancel()
        }
    }

    @Test
    fun aSingleYesToAShowBackIsTheirYesNotAConfirmedMeaning() = vmTest(paired = false) { vm, dao ->
        vm.prepare(Pending.ShowBack(entry))
        vm.show()
        vm.answerYesNo(true)
        assertTrue(dao.rows.value.none { it.type == EventType.ANSWER_GESTURE.name })
        val events = vm.ui.value!!.events
        assertTrue(grades(events).values.none { it == Grade.CONFIRMED })
    }

    @Test
    fun pickingTheMeaningAmongPicturesConfirmsIt() = vmTest(paired = false) { vm, dao ->
        val water = entry.copy(card = "water")
        vm.prepare(Pending.ShowBack(water, checkChoices(water.card, listOf("home", "no"))))
        vm.show()
        val shown = vm.ui.value!!.pending as Pending.ShowBack
        assertEquals(setOf("water", "home", SOMETHING_ELSE), shown.choices.toSet())
        vm.answerCard(water.card)
        assertEquals(1, dao.rows.value.count { it.type == EventType.ANSWER_GESTURE.name })
        assertTrue(Grade.CONFIRMED in grades(vm.ui.value!!.events).values)
    }

    @Test
    fun sayBackOffersTheItemsPicturesAndAddsNoQuestionMark() = vmTest(paired = false) { vm, dao ->
        vm.prepare(Pending.Ask(question.copy(cards = listOf("water", "yes"))))
        vm.show()
        vm.answerCard("water")
        vm.returnToOfficer()
        val line = vm.ui.value!!.events.last { it.type == EventType.ANSWER_CARD }
        vm.sayBack(line, "water", emptySet())
        val back = vm.ui.value!!.pending as Pending.FreeText
        assertEquals("water", back.text)
        // The question's only other card is Yes, an answer rather than a meaning, so there is no choice to offer.
        assertFalse(back.asksChoice)
        vm.answerYesNo(true)
        assertNotEquals(Grade.CONFIRMED, grades(vm.ui.value!!.events)[line.id])
    }

    @Test
    fun answersFromANewLinkAreNotTakenForResends() = vmTest { vm, dao ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        val item = vm.ui.value!!.itemId!!
        val first = Any()
        val second = Any()
        assertTrue(vm.logRemote(item, 0, AnswerBody.YesNo(true), from = first))
        assertFalse(vm.logRemote(item, 0, AnswerBody.YesNo(true), from = first))
        // A re-paired stage numbers from 0 again: its answer is new and must be logged.
        assertTrue(vm.logRemote(item, 0, AnswerBody.YesNo(false), from = second))
        assertEquals(listOf("yes", "no"), dao.rows.value.filter { it.type == EventType.ANSWER_CARD.name }.map { it.cardId })
    }

    @Test
    fun losingThePairedPaneWithoutFallbackLeavesNoStageOnThisPhone() = vmTest { vm, _ ->
        vm.prepare(Pending.Ask(question))
        vm.show()
        vm.prepare(Pending.Cards(listOf("water")))
        vm.leavePaired()
        val ui = vm.ui.value!!
        assertEquals(Mode.OFFICER, ui.mode)
        assertNull(ui.onStage)
        assertEquals(Pending.Cards(listOf("water")), ui.pending)
    }

    private fun assertAbort(reason: Abort, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: PairAbort) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun linkKeysDependOnTheSharedSecret() {
        // Both phones see the transcript; only the ECDH secret is private, so keys must change with it.
        val transcript = ByteArray(64) { it.toByte() }
        val a = app.signsetu.pair.derive(ByteArray(32) { 1 }, transcript, null, true, "m")
        val b = app.signsetu.pair.derive(ByteArray(32) { 2 }, transcript, null, true, "m")
        assertFalse(a.send.contentEquals(b.send))
        assertFalse(a.receive.contentEquals(b.receive))
        assertFalse(a.code == b.code && a.check == b.check)
        val again = app.signsetu.pair.derive(ByteArray(32) { 1 }, transcript, null, true, "m")
        assertTrue(a.send.contentEquals(again.send))
    }
}
