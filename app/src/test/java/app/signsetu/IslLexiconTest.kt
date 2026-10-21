package app.signsetu

import app.signsetu.data.content.ALPHABET
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.DRAFT_CATEGORY
import app.signsetu.data.content.IslSign
import app.signsetu.data.content.IslSigns
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.dictionary
import app.signsetu.data.content.forSignLanguage
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.motionFor
import app.signsetu.data.content.planText
import app.signsetu.data.content.search
import app.signsetu.data.content.signBack
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.HandKey
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Key
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.handShape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IslLexiconTest {

    @Test
    fun measuredShapesParseAndClamp() {
        assertEquals(HandShape.ALL["FIST"], handShape("FIST"))
        assertEquals(HandShape(0.1f, 0.2f, 0.95f, 1f, 1f, 0.5f, -0.3f), handShape("c:10,20,95,100,100,50,-30"))
        assertEquals(HandShape(0f, 1f, 0f, 0f, 0f, 0f, 1f), handShape("c:-5,140,0,0,0,0,200"))
        for (bad in listOf("c:1,2,3", "c:a,0,0,0,0,0,0", "c:", "SPOON")) assertNull(bad, handShape(bad))
    }

    @Test
    fun aMeasuredShapePlaysOnTheRig() {
        val g = Gesture("isl.x", listOf(Key(0f, R = HandKey("chest", shape = "c:0,100,100,100,100,0,0")), Key(0.5f)))
        val pose = Timeline(g, AdultBuild).pose(Timeline.LEAD_IN)
        assertEquals(1f, pose.r.shape.index, 1e-4f)
        assertEquals(0f, pose.r.shape.thumb, 1e-4f)
    }

    private fun gesture(id: String) = Gesture(id, listOf(Key(0f, R = HandKey("chest")), Key(0.4f)))
    private fun isl(word: String, homonym: Boolean = false) =
        IslSign("isl.$word", word, category = "People", rec = "People/$word/MVI_1", homonym = homonym, keys = gesture("isl.$word").keys)

    private val water = WordEntry(listOf("water", "pani"), sign = "sign.water", gesture = "water.cup")
    private val phone = WordEntry(listOf("phone", "mobile"), sign = "phone.ear", gesture = "phone.ear")
    private val small = ContentBundle(
        gestures = listOf("sign.water", "water.cup", "phone.ear", "isl.telephone", "isl.cell phone", "isl.thank you", "isl.fall", "isl.teacher")
            .associateWith(::gesture),
        words = listOf(water, phone).flatMap { e -> e.w.map { it to e } }.toMap(),
        isl = IslSigns(gestures = listOf(isl("telephone"), isl("cell phone"), isl("thank you"), isl("fall", homonym = true), isl("teacher"))),
        signWords = mapOf("telephone" to "phone", "cell phone" to "mobile"),
    )
    private val signs = setOf(SignMode.SIGNS)

    @Test
    fun recordedSignsComeFirstThenAuthoredThenGestureThenSpelling() {
        assertEquals("isl.teacher", small.motionFor("teacher", null, signs)?.id)
        assertEquals("sign.water", small.motionFor("water", water, signs)?.id)
        assertEquals("water.cup", small.motionFor("water", water, setOf(SignMode.GESTURES))?.id)
        assertNull(small.motionFor("teacher", null, setOf(SignMode.GESTURES)))
        assertNull(small.motionFor("ravi", null, signs))
        // A letter is a fingerspelling key, never the sign for a typed word.
        val letter = IslSign("isl.x", "x", category = ALPHABET, rec = "Alphabet/x/MVI_1", keys = gesture("isl.x").keys)
        assertNull(small.copy(isl = small.isl.copy(gestures = small.isl.gestures + letter)).motionFor("x", null, signs))
    }

    @Test
    fun aConceptPrefersTheLabelWrittenAsTyped() {
        assertEquals("isl.cell phone", small.motionFor("mobile", phone, signs)?.id)
        assertEquals("isl.telephone", small.motionFor("phone", phone, signs)?.id)
        // A reader label with no recording of its own signs back as the word it stands for.
        val unrecorded = small.copy(signWords = small.signWords + ("cellular" to "phone"))
        val back = unrecorded.signBack("cellular")
        assertNotNull(back)
        assertEquals(small.planText("phone", setOf(SignMode.SIGNS, SignMode.SPELL)).gesture, back)
    }

    @Test
    fun homonymsAndMultiWordSignsAreHandled() {
        assertNull(small.motionFor("fall", null, signs))
        val text = "Thank you, teacher"
        val plan = small.planText(text, signs)
        assertEquals(listOf("Thank you", "teacher"), plan.beats.map { text.substring(it.range) })
        assertEquals(listOf("isl.thank you", "isl.teacher"), plan.motions)
    }

    @Test
    fun reorderOffKeepsTypedOrder() {
        val text = "What is your name?"
        val typed = bundle.planText(text, signs + SignMode.TYPED_ORDER).motions
        val reordered = bundle.planText(text, signs).motions
        assertEquals(listOf("isl.what", "isl.you", "isl.name"), typed)
        assertEquals(reordered.toSet(), typed.toSet())
        assertTrue(reordered != typed)
    }

    @Test
    fun dictionaryFindsRecordedAndDraftSignsInEitherLanguage() {
        val dict = small.dictionary()
        assertTrue(dict.single { it.gestureId == "sign.water" }.let { !it.recorded && it.category == DRAFT_CATEGORY })
        assertEquals(listOf("sign.water"), dict.search("PANI").map { it.gestureId })
        assertEquals(listOf("isl.teacher"), dict.search("teach", "People").map { it.gestureId })
        assertTrue(dict.search("teach", DRAFT_CATEGORY).isEmpty())
    }

    // The real content: every lesson question signs, every reader label signs back, every motion stays in frame.

    private val content = ASSETS
    private val json = Json { ignoreUnknownKeys = true }
    private val bundle = shipped
    private val islFile = bundle.isl

    @Test
    fun everyIncludeWordIsRetargetedWithAttribution() {
        val labels = json.parseToJsonElement(File(content, "models/sign_reader.json").readText()).jsonObject["labels"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(islFile.source.contains("CC BY 4.0"))
        // Words INCLUDE lacks come from the ISLRTC dictionary (retarget_islrtc.py) and are credited there.
        val (dictionary, include) = islFile.gestures.partition { it.rec.startsWith("ISLRTC/") }
        assertEquals(labels.toSet(), include.map { it.word }.toSet())
        for (s in include) {
            assertTrue("${s.id}: no recording", s.rec.count { it == '/' } == 2)
            assertTrue("${s.id}: no category name", s.category in islFile.categories)
            assertTrue("${s.id}: no Hindi", s.hi.isNotBlank())
        }
        assertTrue(dictionary.isEmpty() || islFile.source.contains("Government Open Data License"))
        for (s in dictionary) assertTrue("${s.id}: no category name", s.category in islFile.categories)
        // A word read from the camera can be signed back for the subject to confirm.
        for (label in labels) assertNotNull("cannot sign back $label", bundle.signBack(label))
    }

    private val asl = bundle.forSignLanguage(SignLanguage.ASL)

    private val manifest = json.parseToJsonElement(File(content, "manifest.json").readText()).jsonObject["files"]!!.jsonObject.keys

    /** Labels of a bundled reader; one missing from the manifest or the assets fails rather than passing on nothing. */
    private fun readerLabels(file: String): List<String> {
        val path = "models/$file.json"
        assertTrue("$path is not in the manifest", path in manifest)
        val f = File(content, path)
        assertTrue("$path is in the manifest but missing", f.isFile)
        val spec = json.parseToJsonElement(f.readText()).jsonObject
        // Format 2 (CTC) names its classes by an alphabet string instead of a label list.
        return spec["labels"]?.jsonArray?.map { it.jsonPrimitive.content } ?: spec["alphabet"]!!.jsonPrimitive.content.map(Char::toString)
    }

    @Test
    fun everyReaderLabelSignsBackAndIsInTheDictionary() {
        for ((b, labels) in listOf(bundle to readerLabels("sign_reader") + bundle.signWords.keys, asl to readerLabels("asl_reader") + asl.signWords.keys)) {
            val prefix = if (b === asl) "asl." else "isl."
            val ids = b.dictionary().map { it.gestureId }.toSet()
            for (label in labels) {
                val back = b.signBack(label)
                assertTrue("$prefix$label: signs back ${back?.id}", back?.id?.startsWith(prefix) == true)
                assertTrue("$prefix$label: not in dictionary", back!!.id in ids)
            }
        }
        assertTrue("ASL dictionary has ${asl.dictionary().size} signs", asl.dictionary().size >= 250)
        // Dictionary leaves letters and digits out; their motions are the fingerspelling keys.
        for (label in readerLabels("isl_spell")) {
            val c = label.lowercase().single()
            assertTrue("isl_spell: no motion for $label", bundle.gestures.containsKey(if (c.isDigit()) "digit.$c" else "letter.$c"))
        }
        for (label in readerLabels("asl_spell").filter { it.length == 1 && it[0].isLetter() }) {
            val c = label.lowercase()
            assertEquals("asl_spell: $label", "asl.letter.$c", asl.gestures["letter.$c"]?.id)
        }
    }

    @Test
    fun aSpelledReadingSignsBackAsLetters() {
        val spell = setOf(SignMode.SIGNS, SignMode.SPELL)
        assertEquals(listOf("letter.r"), bundle.planText("r", spell).motions)
        assertNotNull(bundle.signBack("r"))
        assertEquals(listOf("r", "a", "v", "i").map { "asl.letter.$it" }, asl.planText("ravi", spell).motions)
        assertNotNull(asl.signBack("ravi"))
    }

    @Test
    fun anAslSessionSignsAslAndNeverIsl() {
        assertEquals(SignLanguage.ASL, asl.language)
        assertEquals("I want water.", glossToText(listOf("me", "want", "water"), asl.words, asl.language).en)
        assertTrue(asl.planText("water please", signs).motions.let { m -> m.isNotEmpty() && m.all { it.startsWith("asl.") } })
        assertEquals(listOf("r", "a", "v", "i").map { "asl.letter.$it" }, asl.planText("ravi", setOf(SignMode.SPELL)).motions)
        assertEquals((9 downTo 0).map { "asl.digit.$it" }, asl.planText("9876543210", setOf(SignMode.SPELL)).motions)
        assertTrue(asl.dictionary().none { it.gestureId.startsWith("asl.digit.") })
        assertEquals("asl.water", asl.motionFor("water", asl.words["water"], signs)?.id)
        val signed = asl.questions.filter { it.signed && it.lexicon == null }
        assertTrue(signed.isNotEmpty())
        for (q in signed) {
            val plan = asl.planText(q.text["en"]!!, signs)
            assertTrue("${q.id}: ${plan.motions}", plan.motions.none { it.startsWith("sign.") || it.startsWith("isl.") })
            assertEquals(q.id, plan.gesture?.keys, asl.gestureFor(q)?.keys)
        }
        assertTrue(signed.any { q -> asl.planText(q.text["en"]!!, signs).motions.any { it.startsWith("asl.") } })
    }

    @Test
    fun movingAslSignsKeepTheirMovement() {
        val keys = json.decodeFromString<IslSigns>(File(content, "asl_signs.json").readText()).gestures.associate { it.id to it.keys.size }
        val moving = "finish fish scissors zipper balloon every into morning pen pencil puzzle farm bedroom have".split(" ")
        for (w in moving) assertTrue("asl.$w has ${keys["asl.$w"]} keys", (keys["asl.$w"] ?: 0) >= 2)
    }

    @Test
    fun lessonQuestionsSignInIslOrderWithoutSpelling() {
        fun motions(text: String) = bundle.planText(text, setOf(SignMode.SIGNS)).motions
        assertEquals(listOf("isl.you", "isl.name", "isl.what"), motions("What is your name?"))
        assertEquals(listOf("isl.you", "isl.name", "isl.what"), motions("आपका नाम क्या है?"))
        assertEquals(listOf("isl.you", "isl.family", "isl.how many"), motions("How many in your family?"))
        assertEquals("isl.where", motions("Where do you live?").last())
        val lesson = listOf(
            "name", "age", "mobile", "number", "father", "mother", "family", "brother", "sister", "live", "where", "village", "city",
            "district", "state", "near", "school", "class", "teacher", "like", "help", "water", "understand", "again", "thank you",
            "home", "go", "how", "you", "what", "how many", "namaste", "please",
        )
        for (w in lesson) assertTrue("$w is not signed", motions(w).isNotEmpty())
    }

    @Test
    fun aQuestionWithoutAGestureIsSignedThroughTheLexicon() {
        val q = Question("p1", "identity", mapOf("en" to "What is your name?"))
        assertEquals(bundle.planText("What is your name?", setOf(SignMode.SIGNS)).gesture, bundle.gestureFor(q))
        assertEquals("namaste", bundle.gestureFor(q.copy(gesture = "namaste"))?.id)
    }

    @Test
    fun everySignStaysInFrameAndAuthoredSignsKeepEachHandOnItsSide() {
        // Hugging yourself crosses the arms on purpose; recorded signers cross where the sign does.
        val crossing = setOf("cold.hug")
        // Signed questions (q.*) are chains of the signs checked here.
        for (g in bundle.gestures.values.filterNot { it.id.startsWith("q.") }) for (build in listOf(ChildBuild, AdultBuild)) {
            val timeline = Timeline(g, build)
            val used = { side: (Key) -> HandKey? -> g.keys.any { side(it)?.at?.let { at -> at != "rest" } == true } }
            val r = used { it.R }
            val l = used { it.L }
            var s = Timeline.LEAD_IN
            while (s <= Timeline.LEAD_IN + g.keys.maxOf { it.t } + 1e-4f) {
                val p = timeline.pose(s)
                if (r) assertTrue("${g.id} right hand leaves the frame at $s", p.r.x in 4f..196f && p.r.y in 4f..196f)
                if (l) assertTrue("${g.id} left hand leaves the frame at $s", p.l.x in 4f..196f && p.l.y in 4f..196f)
                if (r && l && !g.id.startsWith("isl.") && g.id !in crossing) assertTrue("${g.id} crosses at $s", p.r.x <= p.l.x + 4f)
                s += 0.05f
            }
        }
    }
}
