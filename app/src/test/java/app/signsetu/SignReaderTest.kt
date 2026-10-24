package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.SignLanguage
import app.signsetu.sign.CombinedReader
import app.signsetu.sign.CtcSpellReader
import app.signsetu.sign.LandmarkSignReader
import app.signsetu.sign.SPELL_ODDS
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignReaders
import app.signsetu.sign.handFeatures
import app.signsetu.sign.signFeatures
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignReaderTest {
    private val models = File(ASSETS, "models")
    private fun reader(name: String) = LandmarkSignReader(File(models, "$name.json").readText(), File(models, "$name.bin").readBytes())
    private val reader = reader("sign_reader")

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

    /** Frames and the Python logits from tools/ml/export.py's fixture for [name]. */
    private fun cases(name: String): List<Pair<List<LandmarkFrame>, List<Float>>> {
        val fixture = Json.parseToJsonElement(javaClass.getResource("/${name}_fixture.json")!!.readText()).jsonObject
        val aspect = fixture.getValue("aspect").jsonPrimitive.float
        return fixture.getValue("cases").jsonArray.map { c ->
            val frames = c.jsonObject.getValue("frames").jsonArray.mapIndexed { i, f ->
                val o = f.jsonObject
                LandmarkFrame(i * 33L, leftHand = points(o["left"]), rightHand = points(o["right"]), pose = points(o["pose"]), aspect = aspect)
            }
            frames to c.jsonObject.getValue("logits").jsonArray.map { it.jsonPrimitive.float }
        }
    }

    private val cases = cases("sign_reader")

    @Test
    fun logitsMatchPython() {
        // The ISL word reader, the ISL letter and digit reader and the ASL word reader share format 1.
        for (name in listOf("sign_reader", "isl_spell", "asl_reader")) {
            val r = reader(name)
            assertTrue("$name fixture has no cases", cases(name).isNotEmpty())
            for ((frames, expected) in cases(name)) {
                val logits = r.logits(frames)!!
                assertEquals(expected.size, logits.size)
                val worst = expected.indices.maxOf { abs(expected[it] - logits[it]) }
                assertTrue("$name max logit difference $worst", worst < 1e-3f)
            }
        }
    }

    @Test
    fun resamplingTakesTheNearestFrame() {
        // Seven frames whose right hand sits further right each frame; row j of 32 must show frame round(j * 6 / 31).
        val pose = MutableList(17) { Offset(0.5f, 0.5f) }.apply { this[11] = Offset(0.6f, 0.4f); this[12] = Offset(0.4f, 0.4f) }
        val frames = (0 until 7).map { i -> LandmarkFrame(i * 33L, rightHand = List(21) { k -> Offset(0.05f * i + 0.01f * k, 0.5f + 0.01f * k) }, pose = pose, aspect = 1f) }
        for (x in listOf(signFeatures(frames, 32)!!, handFeatures(frames, 32)!!)) {
            val wrist = (0 until 32).map { j -> x[j * 98 + 42] }
            val source = wrist.map { ((it - wrist[0]) / (wrist[31] - wrist[0]) * 6).roundToInt() }
            assertEquals((0 until 32).map { (it * 6 / 31.0).roundToInt() }, source)
        }
    }

    @Test
    fun guessesAreEveryLabelByProbability() {
        val guesses = reader.read(cases[0].first)
        assertEquals(reader.labels.size, guesses.size)
        assertTrue(guesses.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertEquals(1.0, guesses.sumOf { it.score.toDouble() }, 1e-3)
        assertTrue(reader.read(listOf(LandmarkFrame(0, rightHand = List(21) { Offset(0.5f, 0.5f) }))).isEmpty())
    }

    @Test
    fun alteredModelBytesGiveNoReader() {
        val content = ASSETS
        fun load(lang: SignLanguage, altered: String?) = SignReaders.load(lang) { path ->
            File(content, path).readBytes().let { if (path == altered) it.also { b -> b[0] = (b[0] + 1).toByte() } else it }
        }
        for (lang in SignLanguage.entries) {
            val full = load(lang, null)!!
            assertTrue("$lang reads words and spelling", full !is LandmarkSignReader)
            for (ext in listOf("bin", "json")) {
                assertTrue("${lang.reader}.$ext", load(lang, "${lang.reader}.$ext") == null)
                // A tampered spelling model leaves the word reader alone.
                val words = load(lang, "${lang.spell}.$ext")
                assertTrue("${lang.spell}.$ext", words is LandmarkSignReader && words.labels.size < full.labels.size)
            }
        }
    }

    private class Fixed(vararg val guesses: SignGuess) : SignReader {
        override val labels = guesses.map { it.word }
        override fun read(frames: List<LandmarkFrame>) = guesses.toList()
    }

    private fun gate(word: Float, spell: Float?) = CombinedReader(
        Fixed(SignGuess("water", word), SignGuess("home", word / 2)),
        if (spell == null) Fixed() else Fixed(SignGuess("a", spell), SignGuess("b", spell / 4)),
        SPELL_ODDS,
    ).read(emptyList()).map { it.word }

    @Test
    fun wordsWinUnlessSpellingIsClearlySurer() {
        assertEquals(listOf("water", "home"), gate(0.5f, 0.92f))
        assertEquals(listOf("water", "home"), gate(0.9f, 0.99f))
        assertEquals(listOf("water", "home"), gate(0.1f, null))
        assertEquals(listOf("a", "water", "b"), gate(0.3f, 0.92f))
    }

    @Test
    fun aMovingSegmentIsAWordHoweverSureTheLetterIs() {
        val pose = List(33) { Offset(0.5f, 0.6f) }.toMutableList().apply { this[11] = Offset(0.6f, 0.4f); this[12] = Offset(0.4f, 0.4f) }
        // Shoulders 0.2 apart: a wrist sweeping 0.6 across ten frames travels 3 widths each way, 6 in all.
        fun frames(sweep: Float) = (0 until 10).map { i ->
            val x = 0.3f + sweep * (if (i < 5) i else 9 - i) / 4f
            LandmarkFrame(i * 40L, rightHand = List(21) { Offset(x, 0.5f) }, pose = pose, aspect = 1f)
        }
        val reader = CombinedReader(Fixed(SignGuess("water", 0.2f)), Fixed(SignGuess("a", 0.9f)), SPELL_ODDS)
        assertEquals("a", reader.read(frames(0.05f)).first().word)
        assertEquals("water", reader.read(frames(0.6f)).first().word)
    }


    @Test
    fun aslSpellingNeedsThreeSureLetters() {
        val assets = ASSETS
        val reader = SignReaders.load(SignLanguage.ASL) { p -> File(assets, p).takeIf { it.isFile }?.readBytes() }!!
        val fixture = Json.parseToJsonElement(javaClass.getResource("/asl_spell_fixture.json")!!.readText()).jsonObject
        val aspect = fixture.getValue("aspect").jsonPrimitive.float
        // The fixture has hands only, so the word reader gives nothing and any guess is the spelling's.
        val frames = fixture.getValue("cases").jsonArray.associate { c ->
            c.jsonObject.getValue("decoded").jsonPrimitive.content to c.jsonObject.getValue("frames").jsonArray.map { f ->
                val o = f.jsonObject
                LandmarkFrame(o.getValue("t").jsonPrimitive.long, leftHand = points(o["left"]), rightHand = points(o["right"]), aspect = aspect)
            }
        }
        val read = frames.mapValues { (_, f) -> reader.read(f).map { it.word } }
        assertEquals(listOf("police"), read["police"])
        assertEquals(listOf("anjali"), read["anjali"])
        assertTrue(read.getValue("zz").isEmpty() && read.getValue("j").isEmpty())
        // Cut into its first letter, "anjali" still decodes in full but that letter is only about 0.6 sure.
        assertTrue(reader.read(frames.getValue("anjali").drop(6)).isEmpty())
    }

    private val ctc = CtcSpellReader(File(models, "asl_spell.json").readText(), File(models, "asl_spell.bin").readBytes())
    private val fixture = Json.parseToJsonElement(javaClass.getResource("/asl_spell_fixture.json")!!.readText()).jsonObject
    private val aspect = fixture.getValue("aspect").jsonPrimitive.float


    @Test
    fun logitsAndLettersMatchPython() {
        val cases = fixture.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val frames = c.getValue("frames").jsonArray.map { f ->
                val o = f.jsonObject
                LandmarkFrame(o.getValue("t").jsonPrimitive.long, leftHand = points(o["left"]), rightHand = points(o["right"]), aspect = aspect)
            }
            val expected = c.getValue("logits").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.float } }
            val logits = ctc.logits(frames)!!
            assertEquals(expected.size, logits.size)
            val worst = expected.indices.maxOf { t -> expected[t].indices.maxOf { abs(expected[t][it] - logits[t][it]) } }
            assertTrue("max logit difference $worst", worst < 1e-3f)
            assertEquals(c.getValue("decoded").jsonPrimitive.content, ctc.decode(logits))
        }
    }

    @Test
    fun underFiveMegabytes() {
        assertTrue(File(models, "asl_spell.bin").length() < 5_000_000)
        assertEquals("", ctc.read(listOf(LandmarkFrame(0), LandmarkFrame(100))))
    }
}
