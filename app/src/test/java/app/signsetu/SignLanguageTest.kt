package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.IslSign
import app.signsetu.data.content.IslSigns
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.forSignLanguage
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.planText
import app.signsetu.sign.LandmarkSignReader
import app.signsetu.sign.SignReaders
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SignLanguageTest {
    private val entries = listOf(
        WordEntry(listOf("i", "me", "मैं"), sign = "me.chest"),
        WordEntry(listOf("you", "तुम"), sign = "you.palm"),
        WordEntry(listOf("water", "पानी"), sign = "sign.water"),
        WordEntry(listOf("want", "चाहिए")),
        WordEntry(listOf("home", "घर"), sign = "home.roof"),
        WordEntry(listOf("go", "जाना"), sign = "sign.go"),
        WordEntry(listOf("no", "not", "नहीं"), sign = "no.shake"),
        WordEntry(listOf("what", "where", "क्या"), sign = "ask"),
        WordEntry(listOf("yesterday"), sign = "sign.yesterday"),
    )
    private val words = entries.flatMap { e -> e.w.map { it to e } }.toMap()

    @Test
    fun sentenceDemo() {
        val cases = listOf(
            listOf("me", "water", "want") to ("I want water." to "मैं पानी चाहिए।"),
            listOf("yesterday", "r", "a", "v", "i", "home", "go", "no") to ("Yesterday Ravi did not go home." to "कल ravi घर नहीं जाना।"),
            listOf("you", "go", "where") to ("Where do you go?" to "तुम कहाँ जाना?"),
            listOf("?", "water", "?") to ("Water." to "पानी।"),
        )
        for ((gloss, expected) in cases) {
            val text = glossToText(gloss, words)
            assertEquals(gloss.toString(), expected, text.en to text.hi)
        }
    }

    private val assets = File("src/main/assets/content")
    private fun open(path: String) = File(assets, path).takeIf { it.isFile }?.readBytes()

    @Test
    fun missingModelsGiveNoReaderWithoutCrashing() {
        assertNull(SignReaders.load(SignLanguage.ISL) { null })
        assertNull(SignReaders.load(SignLanguage.ISL) { p -> if (p.startsWith("models/")) null else open(p) })
        if (!File(assets, "models/asl_reader.bin").isFile) assertNull(SignReaders.load(SignLanguage.ASL, ::open))
        else assertTrue(SignReaders.load(SignLanguage.ASL, ::open)!!.labels.containsAll(listOf("a", "z")))
        // isl_spell missing: the word reader alone still reads.
        val isl = SignReaders.load(SignLanguage.ISL) { p -> if (p.startsWith("models/isl_spell")) null else open(p) }
        assertTrue(isl is LandmarkSignReader)
        assertEquals(262, isl!!.labels.size)
    }

    @Test
    fun selectionSwitchesLexicon() {
        fun g(id: String, t: Float) = Gesture(id, listOf(Key(0f), Key(t)))
        val isl = g("sign.water", 0.5f)
        val asl = IslSign("asl.water", "water", category = "Words", rec = "test/water/a.mp4", keys = g("asl.water", 0.9f).keys)
        val everyday = g("namaste", 0.4f)
        val bundle = ContentBundle(
            emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptySet(),
            gestures = mapOf(isl.id to isl, everyday.id to everyday),
            words = words,
            everyday = mapOf(everyday.id to everyday),
            aslSigns = IslSigns(gestures = listOf(asl)),
        )
        assertEquals(isl, bundle.forSignLanguage(SignLanguage.ISL).gestures["sign.water"])
        val signedAsl = bundle.forSignLanguage(SignLanguage.ASL)
        assertNull(signedAsl.gestures["sign.water"])
        assertEquals(everyday, signedAsl.gestures["namaste"])
        assertEquals(listOf("asl.water"), signedAsl.planText("water", setOf(SignMode.SIGNS)).motions)
        assertEquals(0.9f, signedAsl.planText("water", setOf(SignMode.SIGNS)).beats.single().end - 0.3f, 1e-4f)
        assertEquals("models/asl_reader", SignLanguage.ASL.reader)
        assertEquals("asl_signs.json", SignLanguage.ASL.signs)
    }
}
