package app.signsetu.sign

import android.content.Context
import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.SignLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.exp
import java.security.MessageDigest

/** Creates the bundled sign reader for a language, or none when its models are missing or fail their check (D-25). */
object SignReaders {
    fun create(context: Context, lang: SignLanguage = SignLanguage.ISL): SignReader? =
        load(lang) { path -> runCatching { context.assets.open("content/$path").use { it.readBytes() } }.getOrNull() }

    /** [open] reads a file under assets/content/, null when it is missing. Words and fingerspelling read together. */
    fun load(lang: SignLanguage, open: (String) -> ByteArray?): SignReader? = runCatching {
        val files = Json.parseToJsonElement((open("manifest.json") ?: return null).decodeToString()).jsonObject.getValue("files").jsonObject
        fun verified(path: String): ByteArray? = open(path)?.takeIf { b ->
            MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) } == files[path]?.jsonPrimitive?.content
        }
        fun model(base: String, words: Map<String, String>): SignReader? = runCatching {
            LandmarkSignReader(verified("$base.json")!!.decodeToString(), verified("$base.bin")!!, words)
        }.getOrNull()
        // Without the word map the reader still works; it just cannot narrow guesses to the item on screen.
        val signWords = verified(lang.words)?.let { runCatching { parseSignWords(it.decodeToString()) }.getOrNull() }.orEmpty()
        // Spelling alone is not the language's reader: no word reader, no reader.
        val words = model(lang.reader, signWords) ?: return null
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

/** A letter or digit reader's top guess beats the word reader's only when this many times surer (tools/ml/eval_isl.py). */
const val SPELL_ODDS = 2f

/**
 * The format 2 CTC reader as a [SignReader]: one guess, the letters read, scored by its least sure letter. Reads with
 * fewer or weaker letters are left to the word reader, which also lets a failed read ask for slower signing.
 */
private class CtcSpell(private val ctc: CtcSpellReader) : SignReader {
    override val labels = ctc.alphabet.map { it.lowercaseChar().toString() }
    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val logits = ctc.logits(frames) ?: return emptyList()
        // Per letter, not per frame: blank frames alone score about 0.9 on any segment, word signs included.
        val peaks = ArrayList<Float>()
        var last = 0
        for (row in logits) {
            var best = 0
            for (c in 1 until row.size) if (row[c] > row[best]) best = c
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

/**
 * Word and fingerspelling readers over one segment: the spelling's guesses when its top is more than [odds] times the
 * word top, else the words'. Only one side is returned, since callers re-sort by score and the scales differ.
 */
internal class CombinedReader(private val signs: SignReader, private val spell: SignReader, private val odds: Float) : SignReader {
    override val labels = signs.labels + spell.labels
    override val words = signs.words
    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val w = signs.read(frames)
        val s = spell.read(frames)
        return if (s.isNotEmpty() && s[0].score > odds * (w.firstOrNull()?.score ?: 0f)) s else w
    }
    override fun close() { signs.close(); spell.close() }
}
