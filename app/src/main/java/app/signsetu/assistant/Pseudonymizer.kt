package app.signsetu.assistant

/** Swaps what the officer's phone knows to be personal for stand-ins before text leaves it, and swaps them back
 * in what returns. */
class Pseudonymizer(names: Collection<String>, details: Collection<String> = emptyList()) {
    private val known = names.map { it.trim() }.filter { it.length >= 2 }.distinct()
    private val extra = details.map { it.trim() }.filter { it.length >= 3 && it.any(Char::isLetter) && it !in known }.distinct()
    private var forward: Map<Regex, String>? = null
    private val back = LinkedHashMap<String, String>()

    // The stand-in is a name the text does not already hold, so restoring never turns a real "Ravi" into someone else.
    private fun build(text: String): Map<Regex, String> {
        val base = STAND_INS.firstOrNull { !word(it).containsMatchIn(text) } ?: STAND_INS.first()
        val standIn = HashMap<String, String>()
        for ((i, full) in known.withIndex()) {
            val tag = if (i == 0) base else "$base ${i + 1}"
            back[tag] = full
            for (p in listOf(full) + full.split(WHITESPACE)) if (p.length >= 2) standIn.putIfAbsent(p, tag)
        }
        for ((i, value) in extra.withIndex()) {
            val tag = "[detail ${i + 1}]"
            back[tag] = value
            standIn.putIfAbsent(value, tag)
        }
        // Longest first, so a full name is replaced before its parts.
        return standIn.entries.sortedByDescending { it.key.length }.associate { (part, tag) -> word(part, RegexOption.IGNORE_CASE) to tag }
    }

    fun mask(text: String): String {
        val map = forward ?: build(text).also { forward = it }
        var out = text
        for ((re, tag) in map) out = re.replace(out, tag)
        for ((re, label) in PATTERNS) {
            out = re.replace(out) { m ->
                if (label == "number" && m.value.count(Char::isDigit) < MIN_DIGITS) return@replace m.value
                val tag = "[$label ${back.size + 1}]"
                back[tag] = m.value
                tag
            }
        }
        return out
    }

    fun restore(text: String): String {
        var out = text
        // Longest tags first, so "Ravi 2" is not eaten by "Ravi"; whole words only, so "Ravindra" stays.
        for ((tag, real) in back.entries.sortedByDescending { it.key.length }) out = word(tag).replace(out, Regex.escapeReplacement(real))
        return out
    }

    private companion object {
        const val MIN_DIGITS = 6
        val WHITESPACE = Regex("""\s+""")
        val STAND_INS = listOf("Ravi", "Arun", "Kiran", "Gopal", "Mohan")
        fun word(text: String, vararg options: RegexOption) =
            Regex("""(?<![\p{L}\p{M}\p{N}])""" + Regex.escape(text) + """(?![\p{L}\p{M}\p{N}])""", options.toSet())
        // Order matters: an email or address is masked before the digit rule can cut into it. \p{Nd} covers Devanagari digits.
        val PATTERNS = listOf(
            Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}""") to "email",
            Regex("""(?i)\b(?:https?://|www\.)\S+""") to "link",
            Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""") to "id",
            Regex("""(?<![\p{L}\p{N}])[+(]?\p{Nd}(?:[\p{Nd} ()\-]{4,}\p{Nd})""") to "number",
        )
    }
}
