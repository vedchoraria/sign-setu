package app.signsetu.data.content

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.Key
import app.signsetu.ui.avatar.QuestionKind
import app.signsetu.ui.avatar.chain
import kotlinx.serialization.Serializable

/** What the character does with typed text; the officer turns each one on or off. TYPED_ORDER is set by Settings, not shown as a chip. */
enum class SignMode { SIGNS, GESTURES, SPELL, PICTURES, TYPED_ORDER }

/** One vocabulary entry from words.json: every written form of a word, and what the character does for it. */
@Immutable @Serializable
data class WordEntry(val w: List<String>, val sign: String? = null, val gesture: String? = null, val card: String? = null)

/** One typed word as played: [start] and [end] are seconds into the plan's gesture, before its lead-in. */
@Immutable
data class WordBeat(val range: IntRange, val card: String?, val letters: List<String>?, val start: Float, val end: Float)

@Immutable
data class TextPlan(val gesture: Gesture?, val beats: List<WordBeat>, val motions: List<String> = emptyList())

private const val GAP = 0.3f
/** One typed word, with a possessive or contraction ("Ravi's") kept on it. */
internal val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’]\\p{L}+)?")

/** The Unicode block Hindi is written in. */
val DEVANAGARI = 'ऀ'..'ॿ'

fun String.isDevanagari() = any { it in DEVANAGARI }

/** Grammar words carry no sign of their own; spelling them out would only slow the sentence down. */
private val FILLER = setOf(
    "a", "an", "the", "is", "are", "am", "was", "were", "be", "to", "of", "do", "does", "did", "for", "and",
    "है", "हैं", "था", "थी", "थे", "का", "की", "के", "को", "में", "से", "ने", "तो", "ही", "भी", "और",
    "hai", "hain", "tha", "thi", "the", "ka", "ki", "ke", "ko", "mein", "se", "ne", "to", "bhi", "aur",
)

/** Words ISL leaves unsigned, carried by placement or pointing instead; anywhere in a sentence, unlike [FILLER]. */
private val ISL_UNSIGNED = setOf("in", "at", "on", "into", "with", "about", "or", "it", "there")

/** A word with no entry or sign of its own, as its base form when that has one: "years" is year, "doing" do. */
private fun ContentBundle.base(word: String, modes: Set<SignMode>): String {
    if (word.length < 4 || word in FILLER || isPhrase(word, modes)) return word
    val stem = { suffix: String -> word.takeIf { it.endsWith(suffix) }?.dropLast(suffix.length) }
    val candidates = listOfNotNull(
        stem("ies")?.plus("y"), stem("es"), stem("s"),
        stem("ing"), stem("ing")?.plus("e"), stem("ing")?.dropLast(1), stem("ed"), stem("d"),
    ).filter { it.length >= 2 }
    return candidates.firstOrNull { it in FILLER || isPhrase(it, modes) } ?: word
}

private val TIME_SIGNS = setOf("sign.today", "sign.yesterday", "sign.tomorrow", "sign.now", "sign.morning", "sign.night", "sign.day", "sign.time")
private val WHEN = setOf("when", "kab", "कब")

/** ISL order: time first, then the rest as typed, then negation, then the question word. */
private val QUESTION_SIGNS = setOf(
    "ask", "sign.what", "sign.where", "sign.how", "sign.how_many", "isl.what", "isl.where", "isl.which", "isl.who", "isl.how many",
)

private fun islRank(word: String, entry: WordEntry?) = when {
    entry?.sign in QUESTION_SIGNS || word in WHEN -> 3
    entry?.sign == "no.shake" -> 2
    entry?.sign in TIME_SIGNS -> 0
    else -> 1
}

/** A still beat that keeps a picture on screen for a word with no sign. */
private val PAUSE = Gesture("pause", listOf(Key(0f), Key(0.6f)))

/**
 * Turns free text into one gesture, word by word: a sign or gesture where the vocabulary
 * has one, otherwise fingerspelling. Hindi is spelled akshara by akshara on the ISL two-handed
 * alphabet. A question mark adds the asking face.
 */
fun ContentBundle.planText(text: String, modes: Set<SignMode>): TextPlan {
    val parts = mutableListOf<Gesture>()
    val beats = mutableListOf<WordBeat>()
    var t = 0f
    fun add(g: Gesture) {
        parts += g
        t += g.keys.maxOf { it.t } + GAP
    }
    // "Ravi's" plays as Ravi: a possessive or contraction keeps only its stem.
    val tokens = phrases(TOKEN.findAll(text).map { it.range to it.value.lowercase().split('\'', '’').first() }.toList(), modes)
        .map { (range, w) -> range to base(w, modes) }
    val isl = language == SignLanguage.ISL && SignMode.SIGNS in modes
    // Hindi "do" (give) ends its sentence; anywhere else it is the English helper word.
    fun grammar(i: Int) = (tokens[i].second in FILLER && i != tokens.lastIndex) || (isl && tokens[i].second in ISL_UNSIGNED)
    fun entryOf(i: Int) = tokens[i].second.let { w -> words[w]?.takeIf { !grammar(i) } }
    val order = tokens.indices.let { all -> if (isl && SignMode.TYPED_ORDER !in modes) all.sortedBy { islRank(tokens[it].second, entryOf(it)) } else all }
    // A sentence break inside the text is a pause in the signing: without it two sentences read as one long run.
    val sentenceEnd = tokens.indices.filter { i ->
        i < tokens.lastIndex && text.substring(tokens[i].first.last + 1, tokens[i + 1].first.first).any { it in ".।!?" }
    }.toSet()
    for (i in order) {
        val (range, word) = tokens[i]
        val entry = entryOf(i)
        val start = t
        val card = if (SignMode.PICTURES in modes) entry?.card ?: word.toIntOrNull()?.takeIf { it in 1..10 }?.let { "n$it" } else null
        val motion = if (grammar(i)) null else motionFor(word, entry, modes)
        var letters: List<String>? = null
        when {
            motion != null -> add(motion)
            SignMode.SPELL in modes && word !in FILLER && !grammar(i) -> {
                val units = spelling(word).filter { gestures.containsKey(letterId(it)) }
                letters = units.takeIf { it.isNotEmpty() }
                units.forEach { add(gestures.getValue(letterId(it))) }
            }
            card != null -> add(PAUSE)
        }
        if (t > start) beats += WordBeat(range, card, letters, start, t)
        if (i in sentenceEnd) add(PAUSE)
    }
    if (text.trimEnd().endsWith("?") || text.trimEnd().endsWith("\u061F")) {
        if (parts.none { it.id in QUESTION_SIGNS }) gestures["ask"]?.let(::add)
    }
    return TextPlan(chain("text", parts, GAP).takeIf { it.keys.isNotEmpty() }, beats, parts.map { it.id })
}

/** Joins runs of up to three words that the lexicon knows as one ("thank you", "how many"), longest first. */
private fun ContentBundle.phrases(tokens: List<Pair<IntRange, String>>, modes: Set<SignMode>): List<Pair<IntRange, String>> {
    val out = mutableListOf<Pair<IntRange, String>>()
    var i = 0
    while (i < tokens.size) {
        val n = (minOf(3, tokens.size - i) downTo 2).firstOrNull { n -> isPhrase(tokens.subList(i, i + n).joinToString(" ") { it.second }, modes) } ?: 1
        out += (tokens[i].first.first..tokens[i + n - 1].first.last) to tokens.subList(i, i + n).joinToString(" ") { it.second }
        i += n
    }
    return out
}

@Immutable
data class SignedText(val en: String, val hi: String, val hne: String? = null, val gon: String? = null)

/** Question and time words share an entry in words.json, so their Hindi cannot come from it. */
private val HINDI = mapOf(
    "what" to "क्या", "where" to "कहाँ", "who" to "कौन", "why" to "क्यों", "how" to "कैसे", "which" to "कौन सा", "when" to "कब",
    "yesterday" to "कल", "tomorrow" to "कल", "today" to "आज", "now" to "अब", "morning" to "सुबह", "night" to "रात",
)

/**
 * A signed verb as Hindi says it with the doer in को, which needs no gender: "मुझे पानी चाहिए", not "मैं पानी चाहना".
 * Keyed by the word's first written form in words.json.
 */
private val HINDI_VERB = mapOf(
    "want" to "चाहिए", "go" to "जाना है", "come" to "आना है", "eat" to "खाना है", "sleep" to "सोना है", "sit" to "बैठना है",
    "help" to "मदद चाहिए", "pain" to "दर्द है", "know" to "पता है", "understand" to "समझ आया", "like" to "पसंद है",
    "remember" to "याद है", "hungry" to "भूख लगी है", "talk" to "बात करनी है", "speak" to "बोलना है", "play" to "खेलना है",
    "wash" to "धोना है", "write" to "लिखना है", "scared" to "डर लग रहा है", "cold" to "ठंड लग रही है",
)
private val DOER = mapOf("i" to "मुझे", "you" to "आपको")

/**
 * The inverse of [planText]'s ISL order. [gloss] is the written word of each sign, in signed order; a run of single
 * letters or digits is one fingerspelled word. [lang] ASL keeps the gloss order. [browsUp]: raised brows, the non-manual
 * mark of a yes/no question in ISL and ASL, make it a question without a question sign. ISL signs time first, the verb last and negation and question after it;
 * English moves the verb after the subject, Hindi keeps it last with the question word and नहीं just before it.
 */
fun glossToText(gloss: List<String>, words: Map<String, WordEntry>, lang: SignLanguage = SignLanguage.ISL, browsUp: Boolean = false): SignedText {
    // A reader may hand back a gesture id (no.shake), a Hindi headword (पानी) or the English word itself.
    // Everything is normalised to the English headword here, so reordering, negation and the Hindi draft
    // all look the same sign up the same way.
    fun headword(g: String): String {
        val entry = words[g]
        if (entry != null && !g.isDevanagari()) return g
        return entry?.w?.firstOrNull { w -> w.any { it in 'a'..'z' } }
            ?: words.values.firstOrNull { it.sign == g }?.w?.firstOrNull { w -> w.any { it in 'a'..'z' } }
            ?: g
    }
    val tokens = mutableListOf<Pair<String, Boolean>>()
    for (g in gloss.map { it.lowercase() }.filter { it.isNotBlank() && it != "?" }) {
        val letter = g.length == 1 && g[0].isLetterOrDigit()
        val last = tokens.lastOrNull()
        if (letter && last?.second == true) tokens[tokens.lastIndex] = last.first + g to true else tokens += headword(g) to letter
    }
    fun rank(w: String, spelled: Boolean) = if (spelled) 1 else islRank(w, words[w])
    // ASL is already subject-verb-object as signed, so nothing moves: time words stay where they were signed.
    val asl = lang == SignLanguage.ASL
    val time = tokens.filter { !asl && rank(it.first, it.second) == 0 }
    val rest = tokens.filter { rank(it.first, it.second) == 1 || (asl && rank(it.first, it.second) == 0) }
    val negated = tokens.any { rank(it.first, it.second) == 2 }
    val ask = tokens.filter { rank(it.first, it.second) == 3 }.map { it.first }
    val question = ask.isNotEmpty() || browsUp

    // Nothing readable came back: no sentence, not a bare full stop.
    if (tokens.isEmpty()) return SignedText("", "")

    // The word as read is already English ("your" stays "your"); only a Hindi headword needs translating.
    fun en(t: Pair<String, Boolean>) = if (t.second) t.first.replaceFirstChar { it.uppercase() } else t.first
    // HINDI_VERB and DOER are keyed by the entry's first written form: "me" is the entry that starts at "i".
    fun key(t: Pair<String, Boolean>) = if (t.second) t.first
        else words[t.first]?.w?.firstOrNull { w -> w.any { c -> c in 'a'..'z' } } ?: t.first
    val verbLast = rest.map(::en).toMutableList()
    if (!asl && verbLast.size >= 3) verbLast.add(1, verbLast.removeAt(verbLast.lastIndex))
    val svo = verbLast.mapIndexed { i, w -> if (i == 0 && w in setOf("i", "me")) "I" else if (w == "i") "me" else w }.toMutableList()
    // A shake on its own is the answer "no", not a sentence with a dangling auxiliary: "Do not." reads as nonsense.
    if (negated && rest.isEmpty()) return SignedText("No.", "नहीं।", hne = "नइ।")
    val aux = if (time.any { it.first == "yesterday" }) "did" else "do"
    if (negated) {
        // "I am not hungry" but "I do not want": a state takes the copula, anything else the auxiliary.
        val predicate = svo.getOrNull(1)?.lowercase()
        val copula = when (svo.firstOrNull()) { "I" -> "am not"; "you", "we", "they" -> "are not"; else -> "is not" }
        svo.add(minOf(1, svo.size), if (predicate != null && predicate in STATE) copula else "$aux not")
    }
    // The auxiliary only makes sense before a verb: "What do you want?", never "What do?" or "Where do your mother?".
    val hasVerb = rest.any { !it.second && HINDI_VERB[key(it)] != null }
    val enWords = buildList {
        addAll(ask)
        // A wh-sign carries the question; raised brows on a bare statement only need the mark, not a "do" the signs never had.
        if (!asl && question && !negated && ask.isNotEmpty() && svo.isNotEmpty() && hasVerb) add(aux)
        if (!question) addAll(time.map(::en))
        addAll(svo)
        if (question) addAll(time.map(::en))
    }
    val english = enWords.joinToString(" ").replaceFirstChar { it.uppercase() } + if (question) "?" else "."

    fun hi(t: Pair<String, Boolean>) = if (t.second) t.first else HINDI[t.first]
        ?: words[t.first]?.w?.firstOrNull { w -> w.isDevanagari() } ?: t.first
    val hiRest = rest.map(::hi).toMutableList()
    // A past sign keeps the bare verb: the present forms below would change the tense.
    val verb = rest.lastOrNull()?.takeIf { !it.second && time.none { t -> t.first == "yesterday" } }?.let { HINDI_VERB[key(it)] }
    if (verb != null) {
        hiRest[hiRest.lastIndex] = verb
        if (rest.size > 1) rest.first().takeIf { !it.second }?.let { DOER[key(it)] }?.let { hiRest[0] = it }
    }
    val hindi = (time.map(::hi) + hiRest.dropLast(1) + ask.map { HINDI[it] ?: it } + listOfNotNull("नहीं".takeIf { negated }) + hiRest.takeLast(1))
        .joinToString(" ") + if (question) "?" else "।"
    // Chhattisgarhi as word swaps on the Hindi: "मोला पानी चाही".
    val chhattisgarhi = hindi
        .replace("मुझे", "मोला")
        .replace("आपको", "तोला")
        .replace("नहीं", "नइ")
        .replace("चाहिए", "चाही")
        .replace("कहाँ", "कहाँ/कती")
        .replace("क्या", "का")
    return SignedText(english, hindi, hne = chhattisgarhi)
}

/** Words that describe a state, so English negates them with a copula: "I am not hungry", not "I do not hungry". */
private val STATE = setOf(
    "hungry", "thirsty", "scared", "afraid", "cold", "hot", "tired", "sleepy", "sick", "ill", "fine", "ok", "okay",
    "ready", "sad", "happy", "angry", "upset", "worried", "busy", "late", "alone",
)

/** "café" is spelled c-a-f-e: accents fold to the plain letter instead of dropping out of the word. */
private fun foldAccents(word: String): String =
    Normalizer.normalize(word, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** The letters to fingerspell: Latin as typed, Hindi as written akshara (matras become independent swar). */
fun spelling(word: String): List<String> =
    if (word.isDevanagari()) spellingAkshara(word)
    else foldAccents(word).lowercase().filter { it in 'a'..'z' || it.isDigit() }.map { it.toString() }

private val WH_WORDS = setOf(
    "what", "where", "who", "whom", "whose", "when", "why", "how", "which",
    "kya", "kahan", "kaun", "kab", "kyon", "kyun", "kaise", "kitna", "kitne", "kitni",
    "क्या", "कहाँ", "कहां", "कौन", "कब", "क्यों", "कैसे", "कितना", "कितने", "कितनी", "किसका", "किसने",
)

/** The question face for typed text: none unless it ends in a question mark, then wh- or yes-no. */
fun questionKind(text: String): QuestionKind? {
    val t = text.trimEnd()
    if (!t.endsWith("?") && !t.endsWith("؟")) return null
    val words = TOKEN.findAll(t.lowercase()).map { it.value }.toList()
    // A leading "kya" is the Hindi yes-no particle, not "what".
    val rest = if (words.firstOrNull() in setOf("kya", "क्या")) words.drop(1) else words
    return if (rest.any { it in WH_WORDS }) QuestionKind.WH else QuestionKind.YES_NO
}

private fun letterId(g: String) = when {
    g.length == 1 && g[0].isDigit() -> "digit.${g[0]}"
    g.any { it in DEVANAGARI } -> "letter.hi.$g"
    else -> "letter.$g"
}

private val CONSONANTS = setOf(
    'क', 'ख', 'ग', 'घ', 'ङ', 'च', 'छ', 'ज', 'झ', 'ञ', 'ट', 'ठ', 'ड', 'ढ', 'ण',
    'त', 'थ', 'द', 'ध', 'न', 'प', 'फ', 'ब', 'भ', 'म', 'य', 'र', 'ल', 'व', 'श', 'ष', 'स', 'ह',
)
private val VOWELS = setOf('अ', 'आ', 'इ', 'ई', 'उ', 'ऊ', 'ए', 'ऐ', 'ओ', 'औ', 'ऋ')
private val MATRA = mapOf(
    'ा' to 'आ', 'ि' to 'इ', 'ी' to 'ई', 'ु' to 'उ', 'ू' to 'ऊ',
    'े' to 'ए', 'ै' to 'ऐ', 'ो' to 'ओ', 'ौ' to 'औ', 'ृ' to 'ऋ',
)
private const val VIRAMA = '्'
private const val NUKTA_SIGN = '़'
private val CONJUNCT = listOf("क्ष", "त्र", "ज्ञ")
private val SWAR_MARK = listOf("अं", "अः")

/** Written Hindi as one sign per akshara: virama and nukta drop, matras become स्वर, anusvara is न, digits become ASCII. */
internal fun spellingAkshara(word: String): List<String> = buildList {
    var i = 0
    while (i < word.length) {
        val rest = word.substring(i)
        val cluster = CONJUNCT.firstOrNull { rest.startsWith(it) } ?: SWAR_MARK.firstOrNull { rest.startsWith(it) }
        if (cluster != null) {
            add(cluster)
            i += cluster.length
            continue
        }
        val c = word[i]
        when {
            c == VIRAMA || c == NUKTA_SIGN -> Unit
            c == 'ं' || c == 'ँ' -> add("न")
            c == 'ः' -> add("ह")
            c in MATRA -> add(MATRA.getValue(c).toString())
            c in CONSONANTS || c in VOWELS -> add(c.toString())
            // A digit inside a Hindi word (कोविड19) is still a digit: keep it, and ASCII already is what letterId wants.
            c in '0'..'9' -> add(c.toString())
            // letterId sends a digit to digit.n, so a Devanagari numeral must become its ASCII value first.
            c in '०'..'९' -> add(('0' + (c - '०')).toString())
        }
        i++
    }
}
