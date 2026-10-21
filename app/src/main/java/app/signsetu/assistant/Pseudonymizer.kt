package app.signsetu.assistant

/**
 * Swaps what the officer's phone knows to be personal for stand-ins before text leaves it, and swaps them back in what
 * returns. This is pattern matching, not anonymisation: a name, place or number the phone was not told about, a
 * misspelling, or a detail described in words ("the red house behind the school") passes through unchanged.
 *
 * Known names become "Ravi", "Ravi 2", and so on; other details the subject gave, such as a village or school, become
 * "[detail 1]". Emails, web addresses, PAN-style ids and runs of six or more digits are masked wherever they appear.
 */
class Pseudonymizer(names: Collection<String>, details: Collection<String> = emptyList()) {
    private val forward = LinkedHashMap<Regex, String>()
    private val back = LinkedHashMap<String, String>()

    init {
        val standIn = HashMap<String, String>()
        val known = names.map { it.trim() }.filter { it.length >= 2 }.distinct()
        for ((i, full) in known.withIndex()) {
            val tag = if (i == 0) "Ravi" else "Ravi ${i + 1}"
            back[tag] = full
            for (p in listOf(full) + full.split(WHITESPACE)) if (p.length >= 2) standIn.putIfAbsent(p, tag)
        }
        val extra = details.map { it.trim() }.filter { it.length >= 3 && it.any(Char::isLetter) && it !in known }.distinct()
        for ((i, value) in extra.withIndex()) {
            val tag = "[detail ${i + 1}]"
            back[tag] = value
            standIn.putIfAbsent(value, tag)
        }
        // Longest first, so a full name is replaced before its parts.
        for ((part, tag) in standIn.entries.sortedByDescending { it.key.length }) {
            forward[Regex("""(?<![\p{L}\p{M}\p{N}])""" + Regex.escape(part) + """(?![\p{L}\p{M}\p{N}])""", RegexOption.IGNORE_CASE)] = tag
        }
    }

    fun mask(text: String): String {
        var out = text
        for ((re, tag) in forward) out = re.replace(out, tag)
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
        // Longest tags first, so "Ravi 2" is not eaten by "Ravi".
        for ((tag, real) in back.entries.sortedByDescending { it.key.length }) out = out.replace(tag, real)
        return out
    }

    private companion object {
        const val MIN_DIGITS = 6
        val WHITESPACE = Regex("""\s+""")
        // Order matters: an email or address is masked before the digit rule can cut into it. \p{Nd} covers Devanagari digits.
        val PATTERNS = listOf(
            Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.\p{L}{2,}""") to "email",
            Regex("""(?i)\b(?:https?://|www\.)\S+""") to "link",
            Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""") to "id",
            Regex("""(?<![\p{L}\p{N}])[+(]?\p{Nd}(?:[\p{Nd} ()\-]{4,}\p{Nd})""") to "number",
        )
    }
}
