package app.signsetu

import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.SignLanguage
import app.signsetu.sign.CombinedReader
import app.signsetu.sign.SPELL_ODDS
import app.signsetu.sign.SignGuess
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignReaders
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

/** Word and spelling readers over one segment: spelling must clearly win, and then the top word is still offered. */
class SpellGateTest {
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

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

    @Test
    fun aslSpellingNeedsThreeSureLetters() {
        val assets = File("src/main/assets/content")
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
}
