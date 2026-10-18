package app.signsetu.data.content

/** What the character will do with a typed word, so the officer can see which words get fingerspelled. */
enum class Signability { SIGN, GESTURE, PICTURE, SPELL }

data class Completion(val form: String, val kind: Signability)

private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’]\\p{L}+)?")

fun ContentBundle.signability(word: String): Signability {
    val w = word.lowercase().split('\'', '’').first()
    val entry = words[w]
    val motion = motionFor(w, entry, setOf(SignMode.SIGNS))
    return when {
        motion != null && motion.id == entry?.gesture && motion.id != entry.sign -> Signability.GESTURE
        motion != null -> Signability.SIGN
        entry?.card != null -> Signability.PICTURE
        else -> Signability.SPELL
    }
}

/** Vocabulary forms starting with [prefix]: one per entry, signed first, then shorter. */
fun ContentBundle.complete(prefix: String): List<Completion> {
    val p = prefix.lowercase()
    val min = if (p.any { it in 'ऀ'..'ॿ' }) 1 else 2
    if (p.length < min) return emptyList()
    return words.entries
        .filter { (form, _) -> form.startsWith(p) && form != p }
        .groupBy({ it.value }, { it.key })
        .map { (_, forms) -> forms.minWith(compareBy({ it.length }, { it })) }
        .map { Completion(it, signability(it)) }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.form.length }, { it.form }))
        .take(6)
}

/** Each typed word's range and what will happen to it, with the tokenising TextSigning uses. */
fun ContentBundle.signabilitySpans(text: String): List<Pair<IntRange, Signability>> =
    TOKEN.findAll(text).map { it.range to signability(it.value) }.toList()

/** Reviewed phrase-bank sentences about the word's picture, offered as one-tap templates. */
fun ContentBundle.templatesFor(word: String): List<Pair<Phrase, PhraseSlot>> {
    val card = words[word.lowercase()]?.card ?: return emptyList()
    return phrases.flatMap { p -> p.slots.filter { it.card == card }.map { p to it } }.take(3)
}
