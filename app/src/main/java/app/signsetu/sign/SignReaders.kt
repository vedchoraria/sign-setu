package app.signsetu.sign

import android.content.Context
import androidx.compose.ui.geometry.Offset
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.SignLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.hypot

/** Creates the bundled sign reader for a language, or none when its models are missing or fail their check. */
object SignReaders {
    fun create(context: Context, lang: SignLanguage = SignLanguage.ISL): SignReader? =
        load(lang) { path -> runCatching { context.assets.open("content/$path").use { it.readBytes() } }.getOrNull() }

    /** [open] reads a file under assets/content/, null when it is missing. Words and fingerspelling read together. */
    fun load(lang: SignLanguage, open: (String) -> ByteArray?): SignReader? = runCatching {
        val files = Json.parseToJsonElement((open("manifest.json") ?: return null).decodeToString()).jsonObject.getValue("files").jsonObject
        fun verified(path: String): ByteArray? = open(path)?.takeIf { b ->
            MessageDigest.getInstance("SHA-256").digest(b).toHexString() == files[path]?.jsonPrimitive?.content
        }
        fun model(base: String, words: Map<String, String>, cards: Map<String, List<String>> = emptyMap()): SignReader? = runCatching {
            LandmarkSignReader(verified("$base.json")!!.decodeToString(), verified("$base.bin")!!, words, cards)
        }.getOrNull()
        // Without the word map the reader still works; it just cannot narrow guesses to the item on screen.
        val wordsJson = verified(lang.words)?.decodeToString()
        val signWords = wordsJson?.let { runCatching { parseSignWords(it) }.getOrNull() }.orEmpty()
        val signCards = wordsJson?.let { runCatching { parseSignCards(it) }.getOrNull() }.orEmpty()
        // Spelling alone is not the language's reader: no word reader, no reader.
        val words = model(lang.reader, signWords, signCards) ?: return null
        val spell = lang.spell?.let { base ->
            val spec = verified("$base.json")?.decodeToString()
            if (spec != null && Json.parseToJsonElement(spec).jsonObject["format"]?.jsonPrimitive?.content == "2") {
                runCatching { CtcSpell(CtcSpellReader(spec, verified("$base.bin")!!)) }.getOrNull()
            } else model(base, emptyMap())?.let(::SpellReader)
        }
        // The CTC reader gates itself and has no softmax over words to weigh against, so any read it gives wins.
        if (spell != null) CombinedReader(words, spell, if (spell is CtcSpell) 0f else SPELL_ODDS) else words
    }.getOrNull()
}

/** Fingerspelling labels as single lower-case letters or digits, whatever the model calls them. */
private class SpellReader(private val inner: SignReader) : SignReader by inner {
    override val labels = inner.labels.map(::letterOf)
    override fun read(frames: List<LandmarkFrame>) = inner.read(frames).map { SignGuess(letterOf(it.word), it.score) }
}

/** A fingerspelled read needs this many letters, each at least this sure at its best frame (tools/ml/eval_asl.py). */
const val SPELL_MIN_LETTERS = 3
const val SPELL_MIN_PEAK = 0.9f

/** With the real-trained letter reader, 2 lets through 0.24 correct spelled reads against 0.09 at 3, losing no
 * INCLUDE words. */
const val SPELL_ODDS = 2f

/** How far a wrist strays from its middle position, in shoulder widths, above which a segment is not read as a letter:
 *  95% of INCLUDE words reach past 1.06, real ISLRTC letters stay under 0.95 (90%). Unlike path length, jitter does not add up. */
const val SPELL_MAX_REACH = 1f

/** The larger wrist's farthest distance from its median position over [frames], in shoulder widths; null without shoulders. */
internal fun wristReach(frames: List<LandmarkFrame>): Float? {
    val p = frames.firstNotNullOfOrNull { it.pose?.takeIf { p -> p.size > 12 } } ?: return null
    val ky = 1f / frames.first().aspect
    val sw = hypot(p[11].x - p[12].x, (p[11].y - p[12].y) * ky).takeIf { it > 1e-6f } ?: return null
    fun reach(hand: (LandmarkFrame) -> List<Offset>?): Float {
        val w = frames.mapNotNull { hand(it)?.firstOrNull() }
        if (w.size < 3) return 0f
        val mx = w.map { it.x }.sorted()[w.size / 2]
        val my = w.map { it.y }.sorted()[w.size / 2]
        return w.maxOf { hypot(it.x - mx, (it.y - my) * ky) }
    }
    return maxOf(reach { it.leftHand }, reach { it.rightHand }) / sw
}

/** The format 2 CTC reader as a [SignReader]: one guess, the letters read, scored by its least sure letter. */
private class CtcSpell(private val ctc: CtcSpellReader) : SignReader {
    override val labels = ctc.alphabet.map { it.lowercaseChar().toString() }
    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val logits = ctc.logits(frames) ?: return emptyList()
        // Per letter, not per frame: blank frames alone score about 0.9 on any segment, word signs included.
        val peaks = ArrayList<Float>()
        var last = 0
        for (row in logits) {
            val best = argmax(row)
            val p = 1f / row.sumOf { exp((it - row[best]).toDouble()) }.toFloat()
            if (best != 0 && best != last) peaks += p else if (best != 0) peaks[peaks.lastIndex] = maxOf(peaks.last(), p)
            last = best
        }
        if (peaks.size < SPELL_MIN_LETTERS || peaks.min() < SPELL_MIN_PEAK) return emptyList()
        return listOf(SignGuess(ctc.decode(logits).lowercase(), peaks.min(), spelled = true))
    }
}

/** A guess as the gloss for [glossToText]: a spelled guess is its letters, so it renders as a name. */
fun glossOf(guess: SignGuess?, signWords: Map<String, String>): List<String> = when {
    guess == null -> listOf("?")
    guess.spelled -> guess.word.map { it.toString() }
    else -> listOf(signWords[guess.word] ?: guess.word)
}

internal fun letterOf(label: String) = label.lowercase().removePrefix("letter.").removePrefix("digit.")

/** Word and fingerspelling readers over one segment: the words' guesses, unless the spelling's top is more than
 * [odds] times the word top. */
internal class CombinedReader(private val signs: SignReader, private val spell: SignReader, private val odds: Float) : SignReader {
    override val labels = signs.labels + spell.labels
    override val words = signs.words
    override val cards = signs.cards
    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val w = signs.read(frames)
        val s = spell.read(frames)
        // ISL letters are held still; a segment whose wrist moves far is a word, whatever the letter reader thinks.
        val still = odds == 0f || (wristReach(frames) ?: 0f) < SPELL_MAX_REACH
        val spelled = still && s.isNotEmpty() && s[0].score > odds * (w.firstOrNull()?.score ?: 0f)
        return if (spelled) listOfNotNull(s[0], w.firstOrNull(), s.getOrNull(1)) else w
    }
    override fun close() { signs.close(); spell.close() }
}
