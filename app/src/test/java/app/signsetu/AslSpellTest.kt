package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.CtcSpellReader
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
import java.io.File
import kotlin.math.abs

/** The fingerspelling runtime against tools/ml/spell_ctc.py's fixture: same per-frame logits, same letters. */
class AslSpellTest {
    private val models = File(ASSETS, "models")
    private val reader = CtcSpellReader(File(models, "asl_spell.json").readText(), File(models, "asl_spell.bin").readBytes())
    private val fixture = Json.parseToJsonElement(javaClass.getResource("/asl_spell_fixture.json")!!.readText()).jsonObject
    private val aspect = fixture.getValue("aspect").jsonPrimitive.float

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

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
            val logits = reader.logits(frames)!!
            assertEquals(expected.size, logits.size)
            val worst = expected.indices.maxOf { t -> expected[t].indices.maxOf { abs(expected[t][it] - logits[t][it]) } }
            assertTrue("max logit difference $worst", worst < 1e-3f)
            assertEquals(c.getValue("decoded").jsonPrimitive.content, reader.decode(logits))
        }
    }

    @Test
    fun underFiveMegabytes() {
        assertTrue(File(models, "asl_spell.bin").length() < 5_000_000)
        assertEquals("", reader.read(listOf(LandmarkFrame(0), LandmarkFrame(100))))
    }
}
