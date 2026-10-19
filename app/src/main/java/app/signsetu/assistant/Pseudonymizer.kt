package app.signsetu.assistant

/**
 * Swaps real names (and long digit runs such as phone numbers) for stand-ins before text leaves the phone, and swaps
 * them back in what returns. The first name becomes "Ravi", the next "Ravi 2", and so on.
 */
class Pseudonymizer(names: Collection<String>) {
    private val forward = LinkedHashMap<Regex, String>()
    private val back = LinkedHashMap<String, String>()

    init {
        val standIn = HashMap<String, String>()
        for ((i, full) in names.map { it.trim() }.filter { it.length >= 2 }.distinct().withIndex()) {
            val tag = if (i == 0) "Ravi" else "Ravi ${i + 1}"
            back[tag] = full
            for (p in listOf(full) + full.split(' ')) if (p.length >= 2) standIn.putIfAbsent(p, tag)
        }
        // Longest first, so a full name is replaced before its parts.
        for ((part, tag) in standIn.entries.sortedByDescending { it.key.length }) {
            forward[Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(part) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)] = tag
        }
    }

    fun mask(text: String): String {
        var out = text
        for ((re, tag) in forward) out = re.replace(out, tag)
        return NUMBER.replace(out) { m ->
            val tag = "[number ${back.size + 1}]"
            back[tag] = m.value
            tag
        }
    }

    fun restore(text: String): String {
        var out = text
        // Longest tags first, so "Ravi 2" is not eaten by "Ravi".
        for ((tag, real) in back.entries.sortedByDescending { it.key.length }) out = out.replace(tag, real)
        return out
    }

    private companion object {
        val NUMBER = Regex("\\d[\\d ]{5,}\\d")
    }
}
