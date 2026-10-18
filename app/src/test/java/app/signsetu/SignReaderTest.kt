package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.SignLanguage
import app.signsetu.sign.LandmarkSignReader
import app.signsetu.sign.SignReaders
import app.signsetu.sign.handFeatures
import app.signsetu.sign.signFeatures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class SignReaderTest {
    private val models = File("src/main/assets/content/models")
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
        val content = File("src/main/assets/content")
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
}
