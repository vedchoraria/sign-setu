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
        val words = model(lang.reader, signWords)
        val spell = lang.spell?.let { base ->
            val spec = verified("$base.json")?.decodeToString()
            if (spec != null && Json.parseToJsonElement(spec).jsonObject["format"]?.jsonPrimitive?.content == "2") {
                runCatching { CtcSpell(CtcSpellReader(spec, verified("$base.bin")!!)) }.getOrNull()
            } else model(base, emptyMap())?.let(::SpellReader)
        }
        when {
            words != null && spell != null -> CombinedReader(words, spell)
            else -> words ?: spell
        }
    }.getOrNull()
}

/** Fingerspelling labels as single lower-case letters or digits, whatever the model calls them. */
private class SpellReader(private val inner: SignReader) : SignReader by inner {
    override val labels = inner.labels.map(::letterOf)
    override fun read(frames: List<LandmarkFrame>) = inner.read(frames).map { SignGuess(letterOf(it.word), it.score) }
}

/** The format 2 CTC reader as a [SignReader]: one guess, the letters read, scored by the mean peak probability. */
private class CtcSpell(private val ctc: CtcSpellReader) : SignReader {
    override val labels = ctc.alphabet.map { it.lowercaseChar().toString() }
    override fun read(frames: List<LandmarkFrame>): List<SignGuess> {
        val logits = ctc.logits(frames) ?: return emptyList()
        val text = ctc.decode(logits).lowercase()
        if (text.isEmpty()) return emptyList()
        val peaks = logits.map { row -> val m = row.max(); 1f / row.sumOf { exp((it - m).toDouble()) }.toFloat() }
        return listOf(SignGuess(text, peaks.average().toFloat()))
    }
}

internal fun letterOf(label: String) = label.lowercase().removePrefix("letter.").removePrefix("digit.")

/**
 * Word and fingerspelling readers over one segment, best first. A single letter or digit in the result is spelled.
 * ponytail: compares two softmaxes directly; a sign-or-spell gate if letters crowd out words in practice.
 */
private class CombinedReader(private val signs: SignReader, private val spell: SignReader) : SignReader {
    override val labels = signs.labels + spell.labels
    override val words = signs.words
    override fun read(frames: List<LandmarkFrame>) = (signs.read(frames) + spell.read(frames)).sortedByDescending { it.score }
    override fun close() { signs.close(); spell.close() }
}
