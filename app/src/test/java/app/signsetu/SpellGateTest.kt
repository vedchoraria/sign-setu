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

/** Word and spelling readers over one segment: only one side answers, and spelling must clearly win. */
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
        assertEquals(listOf("a", "b"), gate(0.3f, 0.92f))
    }

    private fun points(e: JsonElement?): List<Offset>? = (e as? JsonArray)?.map { it.jsonPrimitive.float }?.chunked(2) { Offset(it[0], it[1]) }

    @Test
    fun aslSpellingNeedsThreeSureLetters() {
        val assets = File("src/main/assets/content")
        val reader = SignReaders.load(SignLanguage.ASL) { p -> File(assets, p).takeIf { it.isFile }?.readBytes() } ?: return
        val fixture = Json.parseToJsonElement(javaClass.getResource("/asl_spell_fixture.json")!!.readText()).jsonObject
        val aspect = fixture.getValue("aspect").jsonPrimitive.float
        // The fixture has hands only, so the word reader gives nothing and any guess is the spelling's.
        val read = fixture.getValue("cases").jsonArray.associate { c ->
            val frames = c.jsonObject.getValue("frames").jsonArray.map { f ->
                val o = f.jsonObject
                LandmarkFrame(o.getValue("t").jsonPrimitive.long, leftHand = points(o["left"]), rightHand = points(o["right"]), aspect = aspect)
            }
            c.jsonObject.getValue("decoded").jsonPrimitive.content to reader.read(frames).map { it.word }
        }
        assertEquals(listOf("police"), read["police"])
        assertEquals(listOf("anjali"), read["anjali"])
        assertTrue(read.getValue("zz").isEmpty() && read.getValue("j").isEmpty())
    }
}
