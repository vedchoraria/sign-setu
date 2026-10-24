package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Gesture
import app.signsetu.data.content.HandKey
import app.signsetu.data.content.Key
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.QuestionKind
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.chain
import app.signsetu.data.content.planText
import app.signsetu.data.content.questionKind
import app.signsetu.data.content.spelling
import app.signsetu.ui.avatar.AdultBuild
import app.signsetu.ui.avatar.AvatarView
import app.signsetu.ui.avatar.ChildBuild
import app.signsetu.ui.avatar.HandPose
import app.signsetu.ui.avatar.HandShape
import app.signsetu.ui.avatar.Side
import app.signsetu.ui.avatar.Timeline
import app.signsetu.ui.avatar.clampReach
import app.signsetu.ui.avatar.elbowAt
import app.signsetu.ui.avatar.point
import app.signsetu.ui.avatar.solveElbow
import app.signsetu.ui.session.spelledIndex
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {

    @Serializable private data class GesturesFile(val gestures: List<Gesture>)

    private val content = ASSETS
    private val json = Json { ignoreUnknownKeys = true }
    private val gestures = listOf("gestures.json", "signs.json").flatMap { json.decodeFromString<GesturesFile>(File(content, it).readText()).gestures }
    private val recorded = json.decodeFromString<GesturesFile>(File(content, "isl_signs.json").readText()).gestures

    @Serializable private data class PhrasesFile(val phrases: List<Phrase>)

    @Test
    fun everyPhraseSignsWithReviewedGesturesInOrder() {
        val byId = gestures.associateBy { it.id }
        val phrases = json.decodeFromString<PhrasesFile>(File(content, "phrases.json").readText()).phrases
        for (p in phrases) for (slot in p.slots) {
            val ids = p.signs.map { if (it == Phrase.SLOT) slot.sign else it }
            ids.forEach { assertNotNull("${p.id}/${slot.card}: no gesture $it", byId[it]) }
            val chained = chain(p.id, ids.map { byId.getValue(it!!) })
            assertTrue("${p.id}/${slot.card}: keys out of order", chained.keys.zipWithNext().all { (a, b) -> a.t < b.t })
            assertEquals(ids.sumOf { byId.getValue(it!!).keys.size }, chained.keys.size)
            // Phrases are voiced per language or not at all; English and Hindi always are.
            val voiced = slot.text.keys.filter { it in setOf("en", "hi") || File(content, "audio/$it").list()?.any { f -> f.startsWith("phrase.") } == true }
            for (code in voiced) {
                assertTrue("${p.id}/${slot.card}: no $code audio", File(content, "audio/$code/phrase.${p.id}.${slot.card}.ogg").exists())
            }
        }
    }

    @Test
    fun aPictureBeatHoldsThePreviousPoseInsteadOfDroppingToRest() {
        val sign = gestures.first { it.keys.last().R != null }
        val pause = Gesture("pause", listOf(Key(0f), Key(0.6f)))
        val chained = chain("text", listOf(sign, pause))
        val held = chained.keys.takeLast(2)
        held.forEach { assertEquals(sign.keys.maxBy { it.t }.R, it.R) }
    }

    @Test
    fun everyGestureUsesKnownAnchorsAndShapes() {
        for (g in gestures) {
            assertTrue("${g.id}: keys out of order", g.keys.zipWithNext().all { (a, b) -> a.t < b.t })
            for (k in g.keys) for (h in listOfNotNull(k.R, k.L)) {
                assertTrue("${g.id}: unknown shape ${h.shape}", h.shape in HandShape.ALL)
                assertTrue("${g.id}: unknown touch ${h.touch}", h.touch in setOf("wrist", "palm", "tip", "thumb"))
                if (h.at != "rest" && h.at !in Timeline.OTHER_HAND) {
                    for (build in listOf(ChildBuild, AdultBuild)) {
                        assertNotNull("${g.id}: unknown anchor ${h.at}", build.anchor(h.at, Side.R))
                    }
                }
            }
        }
    }

    @Test
    fun everyLexiconEntryAndQuestionHasAGesture() {
        val ids = gestures.map { it.id }.toSet()
        val lexicon = json.parseToJsonElement(File(content, "lexicon.json").readText()).jsonObject["entries"]!!.jsonArray
        for (e in lexicon) {
            val id = e.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue("lexicon $id has no gesture", id in ids)
        }
        val questions = json.parseToJsonElement(File(content, "triage.json").readText()).jsonObject["questions"]!!.jsonArray
        for (q in questions) {
            val o = q.jsonObject
            val ref = (o["gesture"] ?: o["lexicon"])?.jsonPrimitive?.content ?: continue
            assertTrue("question ${o["id"]} refers to missing gesture $ref", ref in ids)
        }
    }

    @Serializable private data class WordsFile(val words: List<WordEntry>)

    @Test
    fun everyVocabularyWordPointsAtRealSignsAndPictures() {
        // words.json links both authored signs (signs.json) and recorded ISLRTC signs (isl_signs.json).
        val ids = (gestures + recorded).map { it.id }.toSet()
        val cards = json.parseToJsonElement(File(content, "cards.json").readText()).jsonObject["cards"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        val words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words
        val forms = words.flatMap { it.w }
        assertEquals("a written form is listed twice", forms.size, forms.toSet().size)
        for (e in words) {
            assertTrue("${e.w.first()}: forms must be lower case", e.w.all { it == it.lowercase() })
            listOfNotNull(e.sign, e.gesture).forEach { assertTrue("${e.w.first()}: no gesture $it", it in ids) }
            e.card?.let { assertTrue("${e.w.first()}: no card $it", it in cards) }
        }
        for (c in 'a'..'z') assertTrue("no letter $c", "letter.$c" in ids)
        for (d in '0'..'9') assertTrue("no digit $d", "digit.$d" in ids)
        for (ak in listOf("अ", "क", "प", "आ", "न", "क्ष", "त्र", "ज्ञ", "अं", "अः")) assertTrue("no letter.hi.$ak", "letter.hi.$ak" in ids)
    }

    @Test
    fun hindiIsSpelledAsAkshara() {
        assertEquals(listOf("प", "आ", "न", "ई"), spelling("पानी"))
        assertEquals(listOf("घ", "र"), spelling("घर"))
        assertEquals(listOf("म", "द", "द"), spelling("मदद"))
        assertEquals(listOf("ब", "च", "च", "आ"), spelling("बच्चा"))
        assertEquals(listOf("र", "न", "ग"), spelling("रंग"))
        assertEquals(listOf("ज", "म", "ई", "न"), spelling("ज़मीन"))
        assertEquals(listOf("2", "0", "2", "6"), spelling("२०२६"))
        assertEquals(listOf("r", "a", "m"), spelling("Ram!"))
        assertEquals(listOf("श", "इ", "क्ष", "क"), spelling("शिक्षक"))
        assertEquals(listOf("अं"), spelling("अं"))
        assertEquals(listOf("letter.hi.प", "letter.hi.आ", "letter.hi.न", "letter.hi.ई"), bundle.planText("पानी", setOf(SignMode.SPELL)).motions)
        assertEquals(listOf("letter.hi.श", "letter.hi.इ", "letter.hi.क्ष", "letter.hi.क"), bundle.planText("शिक्षक", setOf(SignMode.SPELL)).motions)
        assertEquals(listOf("श", "इ", "क्ष", "क"), bundle.planText("शिक्षक", setOf(SignMode.SPELL)).beats.single().letters)
    }

    @Test
    fun theLetterTileLightsOneAksharaAtATime() {
        // पानी is four signs; शिक्षक is श, इ, क्ष, क because the conjunct counts as one akshara.
        for ((word, units) in listOf("पानी" to listOf("प", "आ", "न", "ई"), "शिक्षक" to listOf("श", "इ", "क्ष", "क"))) {
            val beat = bundle.planText(word, setOf(SignMode.SPELL)).beats.single()
            assertEquals(units, beat.letters)
            // A sample in the middle of each unit lights that unit: none skipped, none out of order.
            val lit = units.indices.map { i ->
                spelledIndex(beat, beat.start + (i + 0.5f) / units.size * (beat.end - beat.start))
            }
            assertEquals(units.indices.toList(), lit)
            assertEquals(0, spelledIndex(beat, beat.start))
            assertEquals(units.lastIndex, spelledIndex(beat, beat.end - 1e-4f))
        }
        // A beat that only holds a picture has no tile to advance.
        val picture = bundle.planText("brother", setOf(SignMode.PICTURES)).beats.single()
        assertEquals(null, picture.letters)
        assertEquals(-1, spelledIndex(picture, picture.start))
    }

    private val bundle by lazy {
        ContentBundle(
            gestures = gestures.associateBy { it.id },
            words = json.decodeFromString<WordsFile>(File(content, "words.json").readText()).words.flatMap { e -> e.w.map { it to e } }.toMap(),
        )
    }

    private fun signed(text: String, modes: Set<SignMode> = setOf(SignMode.SIGNS)) =
        bundle.planText(text, modes).beats.map { text.substring(it.range).lowercase() }

    @Test
    fun signsFollowIslOrder() {
        assertEquals(listOf("your", "mother", "where"), signed("Where is your mother?"))
        assertEquals(listOf("i", "hungry", "not"), signed("I am not hungry"))
        assertEquals("yesterday", signed("Did he hit you yesterday?", setOf(SignMode.SIGNS, SignMode.SPELL)).first())
        assertEquals(listOf("kal", "tum", "kahan"), signed("tum kal kahan the?"))
        // Gestures alone keep the typed order, and Hindi "do" gives only at the end of a sentence.
        assertEquals(listOf("where", "your", "mother"), signed("Where is your mother?", setOf(SignMode.GESTURES)))
        assertEquals(listOf("paani", "do"), signed("paani do"))
        assertEquals(listOf("you", "know"), signed("do you know"))
        // Each beat still lights its own typed word, so highlighting follows the reordered signs.
        val text = "where is your mother"
        val beats = bundle.planText(text, setOf(SignMode.SIGNS)).beats
        assertEquals(text.indexOf("where"), beats.last().range.first)
        assertTrue(beats.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-4f })
    }

    @Test
    fun typedTextIsSignedWordByWord() {
        val text = "Is Ravi your brother?"
        val plan = bundle.planText(text, setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        // "Is" is a grammar word, "Ravi" is spelled, "your" and "brother" have gestures, and "?" adds the asking face.
        assertEquals(listOf("Ravi", "your", "brother"), plan.beats.map { text.substring(it.range) })
        assertEquals(listOf("r", "a", "v", "i"), plan.beats[0].letters)
        assertEquals("brother", plan.beats[2].card)
        assertTrue(plan.beats.zipWithNext().all { (a, b) -> a.end <= b.start + 1e-4f })
        val statement = bundle.planText(text.removeSuffix("?"), setOf(SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES))
        assertEquals(bundle.gestures.getValue("ask").keys.size, plan.gesture!!.keys.size - statement.gesture!!.keys.size)
        // With everything off but pictures, a word with a picture still holds the stage for a moment.
        val still = bundle.planText("water", setOf(SignMode.PICTURES))
        assertEquals("water", still.beats.single().card)
        assertTrue(still.beats.single().end > 0f)
        assertEquals(null, bundle.planText("Ravi", setOf(SignMode.GESTURES)).gesture)
        assertEquals(listOf(listOf("r", "a", "v", "i")), bundle.planText("Ravi's", setOf(SignMode.SPELL)).beats.map { it.letters })
        // With Signs on, a word that has only an everyday gesture plays the gesture rather than being spelled.
        assertEquals(listOf(null), bundle.planText("hungry", setOf(SignMode.SIGNS, SignMode.SPELL)).beats.map { it.letters })
    }

    @Test
    fun aHandCanBePlacedOnTheOtherHandsFingertip() {
        val letter = gestures.single { it.id == "letter.e" }
        for (build in listOf(ChildBuild, AdultBuild)) {
            val pose = Timeline(letter, build).pose(Timeline.LEAD_IN + 0.2f)
            val target = pose.l.point("index", Side.L, build.handScale)
            val tip = pose.r.point("index", Side.R, build.handScale)
            assertEquals("right index tip meets left index tip", 0f, hypot(target.first - tip.first, target.second - tip.second), 3f)
        }
    }

    @Test
    fun elbowKeepsBothBonesAtLength() {
        val (ex, ey) = solveElbow(61f, 140f, 90f, 80f, 38f, 35f, Side.R, 1f)
        assertEquals(38f, hypot(ex - 61f, ey - 140f), 0.05f)
        assertEquals(35f, hypot(90f - ex, 80f - ey), 0.05f)
        // "Out" bends the right elbow away from the body's midline (the viewer's left).
        assertTrue(ex < 75f)
    }

    /** Wrist and every fingertip as drawn: a hand that follows its forearm points along it. */
    private fun handPoints(h: HandPose, side: Side, build: app.signsetu.ui.avatar.Build): List<Pair<Float, Float>> {
        val (sx, sy) = build.shoulder(side)
        val (ex, ey) = h.elbowAt(sx, sy, build, side)
        val drawn = if (h.angle.isNaN()) h.copy(angle = Math.toDegrees(kotlin.math.atan2((h.x - ex).toDouble(), (ey - h.y).toDouble())).toFloat()) else h
        return listOf("wrist", "thumb", "index", "middle", "ring", "pinky").map { drawn.point(it, side, build.handScale) }
    }

    @Test
    fun aHeldLetterLandsEarlyAndThenHoldsStill() {
        for (id in listOf("letter.a", "letter.e", "letter.hi.क", "letter.hi.प")) {
            val g = gestures.first { it.id == id }
            val hold = g.keys.last().t
            assertTrue("$id: held too briefly to read", hold >= 0.3f)
            // A held shape is the same pose at both keys: the move happens on the way in, not during the hold.
            assertEquals("$id: the hold drifts", g.keys.first().R, g.keys.last().R)
            val t = Timeline(g, AdultBuild)
            val settled = t.pose(Timeline.LEAD_IN + hold * 0.75f).r
            for (step in 0..5) {
                val p = t.pose(Timeline.LEAD_IN + hold * (0.75f + 0.05f * step)).r
                assertEquals("$id: drifts inside the hold", settled.x, p.x, 1e-3f)
                assertEquals("$id: drifts inside the hold", settled.y, p.y, 1e-3f)
            }
        }
    }

    @Test
    fun bothHandsStayInsideTheBodyView() {
        val v = AvatarView.Body
        // Keeping 2 units clear of the edge is a comfort margin, not the view itself. Recorded signs round dx/dy to
        // whole rig units (retarget.py), so half a unit of that is rounding rather than a clipped hand.
        val edge = 1.5f
        val outside = mutableListOf<String>()
        for ((name, build) in listOf("child" to ChildBuild, "adult" to AdultBuild)) {
            val rest = Timeline.restPose(build)
            for ((h, side) in listOf(rest.r to Side.R, rest.l to Side.L)) for ((_, y) in handPoints(h, side, build)) {
                assertTrue("$name rest fingertip at $y is under 4 units from the bottom", y <= v.top + v.height - 4f)
            }
            for (g in gestures + recorded) {
                val t = Timeline(g, build)
                var s = 0f
                while (s <= t.duration) {
                    val pose = t.pose(s)
                    for ((h, side) in listOf(pose.r to Side.R, pose.l to Side.L)) for ((x, y) in handPoints(h, side, build)) {
                        val inside = x >= v.left + edge && x <= v.left + v.width - edge && y >= v.top + edge && y <= v.top + v.height - edge
                        if (!inside) outside += "${g.id} $name ${s}s $side (${x.toInt()}, ${y.toInt()})"
                    }
                    s += 0.05f
                }
            }
        }
        assertTrue("hand points leave the view: " + outside.distinctBy { it.split(' ').take(2) }.joinToString("; "), outside.isEmpty())
    }

    @Test
    fun recordedSignsPassThroughCloseKeysWithoutStopping() {
        var moving = 0
        for (g in recorded) {
            val t = Timeline(g, AdultBuild)
            val keys = g.keys.sortedBy { it.t }
            for (k in 1 until keys.lastIndex) {
                if (keys[k].t - keys[k - 1].t >= 0.35f || keys[k + 1].t - keys[k].t >= 0.35f) continue
                val at = Timeline.LEAD_IN + keys[k].t
                fun velocity(s0: Float) = t.pose(s0 + 5e-4f).r.let { b -> t.pose(s0).r.let { a -> (b.x - a.x) / 5e-4f to (b.y - a.y) / 5e-4f } }
                val before = velocity(at - 5e-4f)
                val after = velocity(at)
                val speed = maxOf(hypot(before.first, before.second), hypot(after.first, after.second))
                assertTrue("${g.id} jerks at ${keys[k].t}s: $before then $after", hypot(before.first - after.first, before.second - after.second) < 0.15f * speed + 4f)
                if (speed > 20f) moving++
            }
        }
        assertTrue("no recorded key is passed at speed", moving > 50)
    }

    @Test
    fun questionsGetTheirFace() {
        assertEquals(QuestionKind.YES_NO, questionKind("Are you hurt?"))
        assertEquals(QuestionKind.WH, questionKind("Where is your mother?"))
        assertEquals(QuestionKind.YES_NO, questionKind("क्या आपको चोट लगी है?"))
        assertEquals(QuestionKind.WH, questionKind("आप कहाँ रहते हैं?"))
        assertEquals(QuestionKind.WH, questionKind("kya tum kahan rehte ho?"))
        assertEquals(null, questionKind("Ravi"))
    }

    @Test
    fun gesturesStartAndEndAtRest() {
        val rest = Timeline.restPose(ChildBuild)
        for (g in gestures) {
            val t = Timeline(g, ChildBuild)
            for (pose in listOf(t.pose(0f), t.pose(t.duration))) {
                assertEquals("${g.id} right hand x", rest.r.x, pose.r.x, 0.01f)
                assertEquals("${g.id} right hand y", rest.r.y, pose.r.y, 0.01f)
                assertEquals("${g.id} left hand x", rest.l.x, pose.l.x, 0.01f)
            }
        }
    }

    private val upper = AdultBuild.upperArm
    private val fore = AdultBuild.foreArm
    private val shoulder = 100f to 130f

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)

    @Test
    fun aWristInsideTheArmsReachIsLeftWhereItIs() {
        val target = shoulder.first + 20f to shoulder.second + 15f
        assertEquals(target, clampReach(shoulder.first, shoulder.second, target, upper, fore))
    }

    @Test
    fun aWristBeyondTheArmsReachIsPulledBackToArmLength() {
        val far = shoulder.first + 400f to shoulder.second - 300f
        val (x, y) = clampReach(shoulder.first, shoulder.second, far, upper, fore)
        val d = hypot(x - shoulder.first, y - shoulder.second)
        assertEquals(upper + fore - 0.01f, d, 0.02f)
        // Still on the same line of sight: the hand points at where it was asked to go.
        val angle = kotlin.math.atan2((y - shoulder.second).toDouble(), (x - shoulder.first).toDouble())
        val wanted = kotlin.math.atan2((far.second - shoulder.second).toDouble(), (far.first - shoulder.first).toDouble())
        assertEquals(wanted, angle, 1e-4)
    }

    @Test
    fun theElbowSitsOneUpperArmFromTheShoulder() {
        for (target in listOf(40f to 60f, 160f to 60f, 100f to 190f, 250f to 210f)) {
            val elbow = solveElbow(shoulder.first, shoulder.second, target.first, target.second, upper, fore, Side.R, 1f)
            assertEquals("elbow for $target", upper, distance(shoulder, elbow), 0.05f)
        }
    }

    @Test
    fun theElbowKeepsItsBendWhenTheWristCrossesTheShoulderLine() {
        // A hand drawn to the other side of the body passes through the singular point where the two solutions
        // are mirror images. With the previous elbow as the hint the arm turns smoothly; without it, it snaps.
        val targets = (0..24).map { i -> shoulder.first - 60f + i * 5f to shoulder.second + 40f }
        fun elbowDistance(hint: Boolean): Float {
            var previous: Pair<Float, Float>? = null
            var worst = 0f
            for ((tx, ty) in targets) {
                val e = solveElbow(shoulder.first, shoulder.second, tx, ty, upper, fore, Side.R, 1f, if (hint) previous else null)
                if (previous != null) worst = maxOf(worst, distance(previous!!, e))
                previous = e
            }
            return worst
        }
        val smooth = elbowDistance(hint = true)
        val snapping = elbowDistance(hint = false)
        assertTrue("elbow with the hint jumped $smooth", smooth < upper)
        assertTrue("hint should not jump more than the unhinted solve ($smooth vs $snapping)", smooth <= snapping + 0.01f)
    }

    @Test
    fun theElbowNeverSnapsRightAroundTheShoulder() {
        // A hand circling the shoulder, as it does reaching to the far side of the body. The two mirror solves are
        // 50 to 70 units apart here, so a single frame that switches between them is the broken-elbow glitch.
        for (elbowOut in listOf(1f, 0.5f, 0f)) for (radius in listOf(30f, 60f, 90f)) {
            var previous: Pair<Float, Float>? = null
            var worst = 0f
            for (i in 0..720) {
                val a = Math.toRadians(i / 2.0)
                val target = (shoulder.first + radius * cos(a).toFloat()) to (shoulder.second + radius * sin(a).toFloat())
                val e = solveElbow(shoulder.first, shoulder.second, target.first, target.second, upper, fore, Side.R, elbowOut, previous)
                if (previous != null) worst = maxOf(worst, distance(previous!!, e))
                previous = e
            }
            assertTrue("elbowOut=$elbowOut radius=$radius jumped $worst", worst < 5f)
        }
    }

    @Test
    fun aFollowingWordStartsFromTheHandTheLastOneEndedOn() {
        val first = Gesture("one", listOf(Key(0f, R = HandKey(at = "chest", shape = "FLAT")), Key(0.4f, R = HandKey(at = "chest", shape = "FLAT"))))
        val second = Gesture("two", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front", shape = "POINT"))))
        val chained = chain("both", listOf(first, second))
        val end = chained.keys.first { it.t > 0.4f }
        // The second word opens on the first word's hand, not on the rest pose.
        assertEquals(first.keys.last().R, end.R)
        assertNotEquals(HandKey(), end.R)
    }

    @Test
    fun theFirstWordOfASentenceStillStartsFromRest() {
        val only = Gesture("one", listOf(Key(0f), Key(0.4f, R = HandKey(at = "front"))))
        val chained = chain("single", listOf(only))
        assertEquals(HandKey(), chained.keys.first().R)
    }
}
