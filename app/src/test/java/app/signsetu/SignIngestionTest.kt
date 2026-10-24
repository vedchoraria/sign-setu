package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.BlurMeter
import app.signsetu.camera.LandmarkFrame
import app.signsetu.camera.laplacianVariance
import app.signsetu.data.content.WordEntry
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow
import app.signsetu.sign.cardFor
import app.signsetu.sign.decideRead
import app.signsetu.sign.inContext
import app.signsetu.sign.needsSlower
import app.signsetu.sign.parseSignCards
import app.signsetu.sign.parseSignWords
import app.signsetu.sign.readSigns
import app.signsetu.sign.signChoices
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignIngestionTest {
    private val content = ASSETS
    @Serializable private data class WordsFile(val words: List<WordEntry>)
    private val words = Json { ignoreUnknownKeys = true }.decodeFromString<WordsFile>(File(content, "words.json").readText())
        .words.flatMap { e -> e.w.map { it to e } }.toMap()
    private val signWords = parseSignWords(File(content, "sign_words.json").readText())
    private val labels = Json.parseToJsonElement(File(content, "models/sign_reader.json").readText()).jsonObject.getValue("labels").jsonArray.map { it.jsonPrimitive.content }

    private fun hand(x: Float) = List(21) { Offset(x + it * 0.01f, 0.5f) }
    private val face = List(468) { Offset(0.5f, 0.2f) }

    /** One frame every 33 ms: a hand at x, or no hand for null. */
    private fun stream(xs: List<Float?>, startMs: Long = 0) = xs.mapIndexed { i, x -> LandmarkFrame(startMs + i * 33L, rightHand = x?.let(::hand), face = face) }
    private fun moving(n: Int) = List<Float?>(n) { 0.2f + it * 0.03f }
    private fun still(n: Int) = List<Float?>(n) { 0.5f }
    private fun gone(n: Int) = List<Float?>(n) { null }

    @Test
    fun aWindowIsReadOneWordPerSignNotAsTheReadersAlternatives() {
        val reader = object : SignReader {
            override val labels = listOf("water", "help", "pain", "food")
            override fun read(frames: List<LandmarkFrame>) = labels.mapIndexed { i, w -> SignGuess(w, 0.4f - i * 0.1f) }
        }
        val capture = SignCapture()
        val signs = stream(moving(15) + still(15) + moving(15) + gone(40)).flatMap(capture::add) + capture.finish()
        assertEquals(listOf("water", "water"), readSigns(signs, reader, emptySet(), words))
        assertEquals(listOf("?", "?"), readSigns(signs, null, emptySet(), words))
    }

    @Test
    fun captureFindsTheSameSignsAsAFullScanAndKeepsGoingWhenHandsLeave() {
        // Fast signing with short drop-outs: hands gone for 0.2 s mid-sign must not end the capture or split the sign.
        val script = (1..12).flatMap { moving(10) + gone(6) + moving(8) + still(20) }
        val frames = stream(script)
        val capture = SignCapture(SignWindow.ANSWER, limitMs = Long.MAX_VALUE)
        val handed = frames.flatMap { capture.add(it) } + capture.finish()
        val full = segmentSigns(frames, final = true).map { frames.subList(it.first, it.last + 1) }
        assertEquals(12, full.size)
        assertEquals(full, handed)
    }

    @Test
    fun aLongUnbrokenNarrativeIsSegmentedFrameByFrameLikeTheFullScan() {
        // Minutes of signing with only short holds, and now and then a real pause: one step per frame, same signs.
        val script = (1..160).flatMap { i -> moving(20) + still(if (i % 40 == 0) 30 else 6) }
        val frames = stream(script)
        val capture = SignCapture(SignWindow.NARRATIVE, limitMs = Long.MAX_VALUE)
        val handed = frames.flatMap { capture.add(it) } + capture.finish()
        val window = SignWindow.NARRATIVE
        val full = segmentSigns(frames, final = true, pauseMs = window.pauseMs, minMs = window.minMs).map { frames.subList(it.first, it.last + 1) }
        assertEquals(4, full.size)
        assertEquals(full.map { s -> s.map { it.timestampMs } }, handed.map { s -> s.map { it.timestampMs } })
    }

    @Test
    fun narrativeTakesThreeMinutesWithoutFacesAndSplitsOnlyAtLongerPauses() {
        val capture = SignCapture(SignWindow.NARRATIVE)
        // 0.5 s of still hands splits a short answer but not a narrative.
        val script = moving(15) + still(15) + moving(15) + gone(40)
        val frames = stream(script)
        assertEquals(2, segmentSigns(frames, final = true, pauseMs = SignWindow.ANSWER.pauseMs).size)
        val told = frames.flatMap { capture.add(it) } + capture.finish()
        assertEquals(1, told.size)
        assertTrue(told.flatten().all { it.face == null })
        val long = SignCapture(SignWindow.NARRATIVE)
        stream(moving(10)).forEach { long.add(it) }
        assertFalse(long.full)
        long.add(LandmarkFrame(180_000L, rightHand = hand(0.1f)))
        assertTrue(long.full)
    }

    @Test
    fun guessesThatAnswerTheItemComeFirst() {
        val guesses = listOf(SignGuess("police", 0.5f), SignGuess("mother", 0.3f), SignGuess("house", 0.1f), SignGuess("king", 0.1f))
        val family = setOf("mother", "father", "brother", "sister")
        assertEquals(listOf("mother", "police", "house", "king"), inContext(guesses, family, signWords, words).map { it.word })
        assertEquals(guesses, inContext(guesses, emptySet(), signWords, words))
        assertEquals("home", cardFor("house", signWords, words))
        assertNull(cardFor("king", signWords, words))
    }

    @Test
    fun aLessonAnswerFarDownTheListIsOfferedWithItsOwnCard() {
        val signCards = parseSignCards(File(content, "sign_words.json").readText())
        val all = listOf("king", "queen", "gun", "war", "police").mapIndexed { i, w -> SignGuess(w, 0.5f / (i + 1)) }
        val near = setOf("temple", "school", "police_station", "bus_stand")
        val read = decideRead(List(33) { LandmarkFrame(it * 33L) }, all, null, near, signWords, words, signCards)
        assertEquals("police", read.guesses!!.first().word)
        assertEquals("police_station", signChoices(null, read, signWords, words, extra = signCards).first { it.text == "police" }.card)
        // Every alias names a reader label and a picture card that exist.
        val cards = Json.parseToJsonElement(File(content, "cards.json").readText()).jsonObject.getValue("cards").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        signCards.forEach { (label, cs) -> assertTrue(label, label in labels); cs.forEach { assertTrue("$label: $it", it in cards) } }
    }

    @Test
    fun theSubjectChoosesFromTwoOrThreeAlternativesWithTheOfficersReadingFirst() {
        val seg = SignSegment(emptyList(), listOf(SignGuess("mother", 0.6f), SignGuess("father", 0.2f), SignGuess("king", 0.1f)), context = setOf("mother", "father", "sister"))
        assertEquals(
            listOf(SignChoice("sister", "didi"), SignChoice("mother", "mother"), SignChoice("father", "father")),
            signChoices("didi", seg, signWords, words),
        )
        // A reading with no picture is shown as the officer's word; the item's own cards fill the rest.
        assertEquals(listOf(SignChoice(null, "ball"), SignChoice("mother", "mother"), SignChoice("father", "father")), signChoices("ball", seg, signWords, words))
        // One alternative is no choice: nothing is offered.
        assertTrue(signChoices("?", SignSegment(emptyList(), listOf(SignGuess("king", 0.9f))), signWords, words).isEmpty())
    }

    @Test
    fun signSlowerOnlyAfterAFailedReadOfFastOrLostHands() {
        val calm = stream(moving(20))
        val lost = stream(moving(5) + gone(10) + moving(5))
        val fast = (0 until 20).map { LandmarkFrame(it * 33L, rightHand = hand(if (it % 2 == 0) 0.1f else 0.8f)) }
        val failed = listOf(SignGuess("king", 0.1f))
        val read = listOf(SignGuess("mother", 0.9f))
        assertTrue(needsSlower(lost, failed))
        assertTrue(needsSlower(fast, failed))
        assertTrue(needsSlower(lost, emptyList()))
        assertFalse(needsSlower(calm, failed))
        assertFalse(needsSlower(fast, read))
    }

    @Test
    fun signWordsMatchTheReaderAndTheVocabulary() {
        for ((label, word) in signWords) {
            assertTrue("$label is not a reader label", label in labels)
            assertTrue("$word is not in words.json", word in words)
        }
        // Answer words the reader cannot read yet: a follow-up for data collection. Update when the model grows.
        val known = signWords.values.mapNotNull { words[it] }.toSet()
        val core = listOf("yes", "no", "water", "pain", "toilet", "home", "mother", "father", "police", "help", "stop", "don't know")
        val missing = core.filter { w -> words[w]?.let { it !in known } ?: (w !in labels) }
        assertEquals(listOf("yes", "no", "water", "pain", "help", "stop", "don't know"), missing)
    }

    private val hand = List(21) { androidx.compose.ui.geometry.Offset(0.5f, 0.5f) }
    private fun frames(gap: Long, blurry: Boolean = false) = List(10) { LandmarkFrame(it * gap, rightHand = hand, blurry = blurry) }
    private val weak = listOf(SignGuess("water", 0.1f))

    @Test
    fun smearedOrSlowPicturesAskForSlowerSigning() {
        assertFalse(decideRead(frames(33), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
        assertTrue(decideRead(frames(33, blurry = true), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
        assertTrue(decideRead(frames(200), weak, null, emptySet(), emptyMap(), emptyMap()).slower)
    }

    @Test
    fun aSureReadIsNeverBlamedOnThePicture() {
        val sure = listOf(SignGuess("water", 0.9f))
        assertFalse(decideRead(frames(33, blurry = true), sure, null, emptySet(), emptyMap(), emptyMap()).slower)
    }

    @Test
    fun homeSignComesFirstAndCandidatesStopAtThree() {
        val all = listOf("a", "b", "c", "d").map { SignGuess(it, 0.5f) }
        val seg = decideRead(frames(33), all, SignGuess("home", 1f), emptySet(), emptyMap(), emptyMap())
        assertEquals(listOf("home", "a", "b"), seg.guesses!!.map { it.word })
    }

    @Test
    fun edgesReadSharpAndSmearReadsBlurry() {
        val w = 32
        val sharp = IntArray(w * w) { if (it % w % 3 == 0) 0xFFFFFF else 0 }
        val flat = IntArray(w * w) { 0x808080 }
        val s = laplacianVariance(sharp, w, w)
        assertTrue(s > 1000f)
        assertEquals(0f, laplacianVariance(flat, w, w), 1e-3f)
        val m = BlurMeter()
        assertFalse(m.judge(s))
        assertTrue(m.judge(s * 0.1f))
        assertFalse("flat scene alone is not blur", BlurMeter().judge(1f))
    }
}
