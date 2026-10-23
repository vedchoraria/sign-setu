package app.signsetu.data.content

/** How a Hindi draft was made, most trusted first; a draft of several sentences is as weak as its weakest. */
enum class Made { BANK, NEAR, TEMPLATE, WORDS }

/** A machine translation for the caption: the officer sees it marked as unchecked before Send. */
data class HindiDraft(val hi: String, val made: Made)

/** English to Hindi without a model: a reviewed sentence from the content first, then a sentence pattern, then
 * word by word in Hindi order. */
fun ContentBundle.toHindi(text: String): HindiDraft? {
    val bank = bank()
    bank.reviewed(text)?.let { return it }
    val parts = text.trim().split(Regex("(?<=[.?!।])\\s+")).filter { it.isNotBlank() }
    if (parts.size < 2) return bank.sentence(text)
    val drafts = parts.map { bank.reviewed(it) ?: bank.sentence(it) ?: return null }
    return HindiDraft(drafts.joinToString(" ") { it.hi }, drafts.maxOf { it.made })
}

private val CONTRACTIONS = mapOf(
    "don't" to "do not", "doesn't" to "does not", "didn't" to "did not", "can't" to "can not", "won't" to "will not",
    "isn't" to "is not", "aren't" to "are not", "i'm" to "i am", "you're" to "you are", "we're" to "we are",
    "they're" to "they are", "it's" to "it is", "that's" to "that is", "what's" to "what is", "where's" to "where is",
    "who's" to "who is", "i'll" to "i will", "we'll" to "we will", "you'll" to "you will", "let's" to "let us",
)
private val ENDS = charArrayOf('.', '?', '!', '।', ' ')
private val ARTICLES = setOf("a", "an", "the")

/** "Don't worry!" and "do not worry" are one key: lower case, contractions open, no articles or punctuation. */
internal fun normalise(text: String): String = text.lowercase().replace('’', '\'')
    .replace(Regex("\\p{L}+'\\p{L}+")) { CONTRACTIONS[it.value] ?: it.value }
    .replace(Regex("\\bcannot\\b"), "can not")
    .replace(Regex("[^\\p{L}\\p{M}\\p{N}']+"), " ")
    .split(' ').map { it.trim('\'') }.filter { it.isNotEmpty() && it !in ARTICLES }.joinToString(" ")

@Volatile private var cached: Pair<ContentBundle, Bank>? = null

private fun ContentBundle.bank(): Bank = cached?.takeIf { it.first === this }?.second ?: makeBank().also { cached = this to it }

private fun clean(s: String) = s.substringBefore(" (").substringBefore(" / ").trim()

private fun ContentBundle.makeBank(): Bank {
    val labels = cards.values.map { it.label } + lexicon.values.map { it.label } + categories.map { it.label }
    val reviewed = questions.map { it.text } + phrases.flatMap { p -> p.slots.map { it.text } + p.label } + subjectUi.values + labels + groups.map { it.label }
    val sentences = LinkedHashMap<String, Pair<String, String>>()
    for (l in reviewed) {
        val en = l["en"] ?: continue
        val hi = l["hi"] ?: continue
        if ('…' in en || '%' in en) continue
        normalise(clean(en)).takeIf { it.isNotEmpty() }?.let { sentences.putIfAbsent(it, en to clean(hi)) }
    }
    val nouns = HashMap(NOUNS)
    for (l in labels) {
        val en = normalise(clean(l["en"] ?: continue))
        if (en.isNotEmpty() && en !in STOP && en.count { it == ' ' } < 3) l["hi"]?.let { nouns.putIfAbsent(en, clean(it)) }
    }
    for ((form, e) in words) if (form !in STOP && form.all { it in 'a'..'z' || it == ' ' }) {
        (e.card?.let { cards[it]?.label?.get("hi") }?.let(::clean) ?: e.w.firstOrNull { it.isDevanagari() }?.let(::clean))?.let { nouns.putIfAbsent(form, it) }
    }
    return Bank(sentences, nouns)
}

/** [stem] is what comes before ना; [post] marks the object ("मदद कर" takes की: "आपकी मदद"). */
private class V(val stem: String, val post: String? = null)

private val VERBS = mapOf(
    "go" to V("जा"), "come" to V("आ"), "come back" to V("वापस आ"), "sit" to V("बैठ"), "sit down" to V("बैठ"),
    "stand" to V("खड़े हो"), "stand up" to V("खड़े हो"), "wait" to V("रुक"), "stay" to V("रुक"), "stop" to V("रुक"),
    "eat" to V("खा"), "drink" to V("पी"), "sleep" to V("सो"), "rest" to V("आराम कर"), "write" to V("लिख"),
    "draw" to V("बना"), "read" to V("पढ़"), "hear" to V("सुन"), "listen" to V("सुन"), "listen to" to V("सुन", "की बात"),
    "see" to V("देख"), "look" to V("देख"), "look at" to V("देख", "की ओर"), "watch" to V("देख"), "walk" to V("चल"),
    "speak" to V("बोल"), "talk" to V("बात कर"), "talk to" to V("बात कर", "से"), "tell" to V("बता", "को"),
    "show" to V("दिखा", "को"), "call" to V("फ़ोन कर", "को"), "help" to V("मदद कर", "की"), "point" to V("इशारा कर"),
    "point to" to V("इशारा कर", "की ओर"), "point at" to V("इशारा कर", "की ओर"), "understand" to V("समझ"),
    "open" to V("खोल"), "close" to V("बंद कर"), "wash" to V("धो"), "play" to V("खेल"), "run" to V("भाग"),
    "live" to V("रह"), "work" to V("काम कर"), "take" to V("ले"), "give" to V("दे", "को"), "bring" to V("ला"),
    "find" to V("ढूँढ"), "keep" to V("रख"), "put" to V("रख"), "touch" to V("छू"), "hold" to V("पकड़"),
    "worry" to V("चिंता कर"), "cry" to V("रो"), "breathe" to V("साँस ले"), "calm down" to V("शांत हो"),
    "move" to V("हिल"), "follow" to V("पीछे आ"), "sign" to V("इशारा कर"), "know" to V("जान"), "wake up" to V("उठ"),
    "get up" to V("उठ"), "lie down" to V("लेट"), "be quiet" to V("चुप रह"), "be careful" to V("सावधान रह"),
    "be afraid" to V("डर"), "be scared" to V("डर"), "fear" to V("डर"), "nod" to V("सिर हिला"), "smile" to V("मुस्कुरा"),
)

/** Said of the person as को: "आपको चोट लगी है". */
private val FEEL = mapOf(
    "hurt" to "चोट लगी है", "injured" to "चोट लगी है", "hungry" to "भूख लगी है", "thirsty" to "प्यास लगी है",
    "cold" to "ठंड लग रही है", "hot" to "गर्मी लग रही है", "scared" to "डर लग रहा है", "afraid" to "डर लग रहा है",
    "frightened" to "डर लग रहा है", "in pain" to "दर्द हो रहा है", "sleepy" to "नींद आ रही है",
)

/** Words that stay the same for every person and gender after है or हैं. */
private val BE = mapOf(
    "ok" to "ठीक", "okay" to "ठीक", "fine" to "ठीक", "alright" to "ठीक", "all right" to "ठीक", "safe" to "सुरक्षित",
    "sick" to "बीमार", "ill" to "बीमार", "unwell" to "बीमार", "sad" to "उदास", "angry" to "नाराज़", "happy" to "खुश",
    "alone" to "अकेले", "ready" to "तैयार", "tired" to "थके हुए", "married" to "शादीशुदा", "here" to "यहाँ",
    "there" to "वहाँ", "nearby" to "आस-पास", "near" to "पास", "at home" to "घर पर", "calm" to "शांत", "busy" to "व्यस्त",
    "safe now" to "अब सुरक्षित", "not alone" to "अकेले नहीं", "not safe" to "सुरक्षित नहीं",
)

private val NOUNS = mapOf(
    "officer" to "अधिकारी", "police officer" to "पुलिस अधिकारी", "lawyer" to "वकील", "name" to "नाम", "number" to "नंबर",
    "phone number" to "फ़ोन नंबर", "mobile number" to "मोबाइल नंबर", "address" to "पता", "boy" to "लड़का", "girl" to "लड़की",
    "man" to "आदमी", "woman" to "महिला", "child" to "बच्चा", "children" to "बच्चे", "people" to "लोग", "hand" to "हाथ",
    "hands" to "हाथ", "finger" to "उँगली", "bag" to "बैग", "blanket" to "कंबल", "tea" to "चाय", "clothes" to "कपड़े",
    "bottle" to "बोतल", "toy" to "खिलौना", "book" to "किताब", "help" to "मदद", "age" to "उम्र", "parents" to "माता-पिता",
    "anything" to "कुछ", "something" to "कुछ", "everything" to "सब कुछ", "friend" to "दोस्त", "one" to "एक", "two" to "दो",
    "three" to "तीन", "four" to "चार", "five" to "पाँच", "six" to "छह", "seven" to "सात", "eight" to "आठ", "nine" to "नौ",
    "ten" to "दस", "chair" to "कुर्सी", "door" to "दरवाज़ा", "picture" to "तस्वीर", "card" to "कार्ड", "pen" to "पेन",
)

private val FEM = setOf(
    "माँ", "बहन", "आँख", "पीठ", "कमर", "टाँग", "दवा", "दवाई", "कार", "बस", "ट्रेन", "सड़क", "दुकान", "उम्र", "कक्षा",
    "किताब", "कमीज़", "पैंट", "फ्रॉक", "ड्रेस", "मदद", "जगह", "बात", "चोट", "भूख", "प्यास", "ठंड", "नींद", "रेल", "मस्जिद",
    "माता", "औरत", "महिला", "चाय", "रात", "सुबह", "शाम", "बाँह", "नाक", "गर्दन", "बोतल", "पुलिस", "सरकार", "तस्वीर", "जेब",
)
private val MASC_I = setOf("पानी", "आदमी", "भाई", "पति", "हाथी", "अधिकारी", "विद्यार्थी", "व्यक्ति", "साथी", "दही", "जी", "मोती")
/** Spoken of with respect: always हैं and आपके, never made plural. */
private val HONOR = setOf(
    "माँ", "पिता", "दादी", "नानी", "दादा", "नाना", "माता", "माता-पिता", "डॉक्टर", "शिक्षक", "अधिकारी", "पति", "पत्नी",
    "चाचा", "चाची", "मामा", "मौसी", "बुआ", "जज", "वकील",
)
private val PLURAL_EN = setOf("children", "people", "shoes", "friends", "parents", "clothes", "men", "women", "trousers", "pants")

private fun fem(hi: String) = hi.substringAfterLast(' ').let { w -> w in FEM || (w.endsWith('ी') || w.endsWith('ि')) && w !in MASC_I }

/** [pron] is the oblique stem of a pronoun, which joins its postposition: मुझे, आपकी, उन्होंने. */
private class Np(val direct: String, val oblique: String, val fem: Boolean, val many: Boolean, val pron: String? = null) {
    val be get() = if (many) "हैं" else "है"
    fun post(p: String): String = when {
        pron == null -> "$oblique $p"
        p == "को" -> DATIVE.getValue(pron)
        p == "ने" -> ERGATIVE.getValue(pron)
        p.startsWith("क") -> GEN.getValue(pron) + p.drop(1)
        p == "से" && pron != "किसी" -> pron + p
        else -> "$pron $p"
    }
}

private val DATIVE = mapOf("आप" to "आपको", "मुझ" to "मुझे", "हम" to "हमें", "उन" to "उन्हें", "इस" to "इसे", "उस" to "उसे", "किसी" to "किसी को")
private val ERGATIVE = mapOf("आप" to "आपने", "मुझ" to "मैंने", "हम" to "हमने", "उन" to "उन्होंने", "इस" to "इसने", "उस" to "उसने", "किसी" to "किसी ने")
private val GEN = mapOf("आप" to "आपक", "मुझ" to "मेर", "हम" to "हमार", "उन" to "उनक", "इस" to "इसक", "उस" to "उसक", "किसी" to "किसी क")

private val PRONOUNS = mapOf(
    "i" to Np("मैं", "मुझ", false, false, "मुझ"), "me" to Np("मैं", "मुझ", false, false, "मुझ"),
    "you" to Np("आप", "आप", false, true, "आप"), "we" to Np("हम", "हम", false, true, "हम"), "us" to Np("हम", "हम", false, true, "हम"),
    "it" to Np("यह", "इस", false, false, "इस"), "this" to Np("यह", "इस", false, false, "इस"), "that" to Np("वह", "उस", false, false, "उस"),
    "someone" to Np("कोई", "किसी", false, false, "किसी"), "anyone" to Np("कोई", "किसी", false, false, "किसी"),
    "somebody" to Np("कोई", "किसी", false, false, "किसी"), "anybody" to Np("कोई", "किसी", false, false, "किसी"),
) + listOf("he", "him", "she", "her", "they", "them").associateWith { Np("वे", "उन", false, true, "उन") }

private val OWNERS = mapOf("your" to "आप", "my" to "मुझ", "our" to "हम", "his" to "उन", "her" to "उन", "their" to "उन")
/** Direct singular, oblique singular, direct plural, oblique plural. */
private val POINTERS = mapOf(
    "this" to listOf("यह", "इस", "ये", "इन"), "these" to listOf("ये", "इन", "ये", "इन"),
    "that" to listOf("वह", "उस", "वे", "उन"), "those" to listOf("वे", "उन", "वे", "उन"),
    "any" to listOf("कोई", "किसी", "कुछ", "कुछ"), "some" to listOf("कोई", "किसी", "कुछ", "कुछ"),
)

private fun ending(fem: Boolean, many: Boolean, oblique: Boolean) = if (fem) "ी" else if (many || oblique) "े" else "ा"

/** The noun's form for its number and case: बच्चा, बच्चे को, बच्चे, बच्चों को; बहन, बहनें. */
private fun inflect(w: String, fem: Boolean, plural: Boolean, oblique: Boolean): String = when {
    !plural -> if (oblique && !fem && w.endsWith('ा') && w !in HONOR) w.dropLast(1) + 'े' else w
    oblique -> when {
        fem && w.endsWith('ी') -> w.dropLast(1) + "ियों"
        w.endsWith('ा') || w.endsWith('े') -> w.dropLast(1) + "ों"
        w.endsWith('ँ') || w.endsWith('ं') -> w
        else -> w + "ों"
    }
    fem -> when { w.endsWith('ी') -> w.dropLast(1) + "ियाँ"; w.endsWith('ा') -> w + "एँ"; w.endsWith('ँ') -> w; else -> w + "ें" }
    else -> if (w.endsWith('ा')) w.dropLast(1) + 'े' else w
}

private val ADV = mapOf(
    "here" to "यहाँ", "there" to "वहाँ", "now" to "अब", "today" to "आज", "tomorrow" to "कल", "yesterday" to "कल",
    "again" to "फिर से", "slowly" to "धीरे-धीरे", "quickly" to "जल्दी", "together" to "साथ में", "also" to "भी",
    "soon" to "जल्द ही", "please" to "कृपया", "later" to "बाद में", "first" to "पहले", "carefully" to "ध्यान से", "quietly" to "चुपचाप",
)
/** "" drops the word: "go to school" is स्कूल जाइए. */
private val PREPS = mapOf(
    "to" to "", "with" to "के साथ", "in" to "में", "at" to "पर", "on" to "पर", "from" to "से", "near" to "के पास",
    "for" to "के लिए", "about" to "के बारे में", "behind" to "के पीछे", "into" to "में",
)
private val WH = mapOf("what" to "क्या", "where" to "कहाँ", "when" to "कब", "how" to "कैसे", "why" to "क्यों", "who" to "कौन")
private val NOT = setOf("not", "no", "never")
private val GLUE = setOf("is", "are", "am", "do", "does", "did", "to", "will", "be", "can", "and")
/** Words that are never a noun, though words.json lists them for signing. */
private val STOP = VERBS.keys + PRONOUNS.keys + OWNERS.keys + POINTERS.keys + WH.keys + NOT + GLUE + ADV.keys + PREPS.keys +
    FEEL.keys + BE.keys + setOf("how many", "how much", "want", "need", "please")

/** Past acts done to the subject, with the doer in ने: "क्या किसी ने आपको मारा?". */
private val DONE = mapOf(
    "hurt" to "चोट पहुँचाई", "hit" to "मारा", "beat" to "मारा", "touch" to "छुआ", "threaten" to "धमकी दी",
    "push" to "धक्का दिया", "scold" to "डाँटा", "kick" to "लात मारी", "slap" to "थप्पड़ मारा",
)

private val FIXED = mapOf(
    "it is ok" to "कोई बात नहीं।", "it is okay" to "कोई बात नहीं।", "that is ok" to "कोई बात नहीं।",
    "take your time" to "आराम से, कोई जल्दी नहीं।", "one moment" to "एक मिनट रुकिए।", "wait moment" to "एक मिनट रुकिए।",
    "sorry" to "माफ़ कीजिए।", "i am sorry" to "माफ़ कीजिए।", "show me where" to "मुझे दिखाइए कहाँ।",
    "what happened" to "क्या हुआ?", "good" to "अच्छा।", "well done" to "बहुत अच्छा।",
)

private fun lastWord(s: String) = s.substringAfterLast(' ')
private fun consonantEnd(w: String) = w.last().let { it in 'क'..'ह' || it in 'क़'..'य़' || it == '़' }
private fun swapLast(stem: String, f: (String) -> String) = stem.dropLast(lastWord(stem).length) + f(lastWord(stem))

private val IMP = mapOf("दे" to "दीजिए", "ले" to "लीजिए", "कर" to "कीजिए", "पी" to "पीजिए", "हो" to "होइए")
private fun imp(stem: String) = swapLast(stem) { w -> IMP[w] ?: (w + if (consonantEnd(w)) "िए" else "इए") }
private fun notImp(stem: String) = imp(stem).let { s -> swapLast(s) { "मत $it" } }
private fun fut(stem: String) = swapLast(stem) { w ->
    when { w == "हो" -> "होंगे"; w.endsWith('े') -> w + "ंगे"; consonantEnd(w) -> w + "ेंगे"; else -> w + "एँगे" }
}

/** Where the doer is आप, its own things are अपना, not आपका: "अपना नाम दिखाइए". */
private fun own(s: String) = s.replace("आपका", "अपना").replace("आपकी", "अपनी").replace("आपके", "अपने")

/** Everything before the verb, in Hindi order: adverbs, places, objects. */
private class Vp(val pre: String, val stem: String) {
    fun then(form: String) = if (pre.isEmpty()) form else "$pre $form"
}

private class Bank(val sentences: Map<String, Pair<String, String>>, val nouns: Map<String, String>) {

    fun reviewed(text: String): HindiDraft? {
        val (en, hi) = sentences[normalise(text)] ?: return null
        return HindiDraft(hi, if (en.trimEnd(*ENDS).equals(text.trim().trimEnd(*ENDS), ignoreCase = true)) Made.BANK else Made.NEAR)
    }

    /** The Hindi noun and whether the English was plural; "sisters" is बहन made plural, not a word of its own. */
    private fun noun(w: String): Pair<String, Boolean>? =
        listOf("ies" to "y", "es" to "", "s" to "").firstNotNullOfOrNull { (s, r) -> if (w.endsWith(s) && w.length > s.length + 2) nouns[w.dropLast(s.length) + r] else null }?.let { it to true }
            ?: nouns[w]?.let { it to (w in PLURAL_EN) }

    fun np(t: List<String>): Np? {
        if (t.isEmpty() || t.any { it.isEmpty() }) return null
        if (t.size == 1) PRONOUNS[t[0]]?.let { return it }
        val s = t.indexOfFirst { it.endsWith("'s") }
        if (s >= 0) {
            if (s == t.lastIndex) return null
            val owner = np(t.take(s) + t[s].dropLast(2)) ?: return null
            val head = np(t.drop(s + 1)) ?: return null
            return Np(owner.post(if (head.fem) "की" else if (head.many) "के" else "का") + " " + head.direct,
                owner.post(if (head.fem) "की" else "के") + " " + head.oblique, head.fem, head.many)
        }
        val owner = OWNERS[t[0]]
        val pointer = POINTERS[t[0]]
        val count = t[0].takeIf { t.size > 1 && it in setOf("two", "three", "four", "five", "six", "seven", "eight", "nine", "ten") }?.let { nouns[it] }
        val rest = if (owner != null || pointer != null || count != null) t.drop(1) else t
        if (rest.isEmpty()) return null
        val whole = if (rest.size < 2) null else nouns[rest.joinToString(" ")]?.let { it to (rest.last() in PLURAL_EN) }
        val (hi, plural0) = whole ?: noun(rest.last()) ?: return null
        val mods = if (whole != null) emptyList() else rest.dropLast(1).map { nouns[it] ?: return null }
        val f = fem(hi)
        val honor = lastWord(hi) in HONOR
        val plural = (plural0 || count != null || t[0] in setOf("these", "those")) && !honor
        val many = plural || honor
        fun form(oblique: Boolean): String {
            val head = swapLast(hi) { inflect(it, f, plural, oblique) }
            val adj = mods.map { m -> if (m.endsWith('ा')) m.dropLast(1) + ending(f, many, oblique) else m }
            val det = owner?.let { GEN.getValue(it) + ending(f, many, oblique) }
                ?: pointer?.get((if (plural) 2 else 0) + if (oblique) 1 else 0) ?: count
            return (listOfNotNull(det) + adj + head).joinToString(" ")
        }
        return Np(form(false), form(true), f, many)
    }

    private fun np(s: String) = np(s.split(' '))

    fun vp(s: String): Vp? {
        val t = s.split(' ')
        val n = (minOf(3, t.size) downTo 1).firstOrNull { t.take(it).joinToString(" ") in VERBS } ?: return null
        val v = VERBS.getValue(t.take(n).joinToString(" "))
        val adverbs = mutableListOf<String>()
        val places = mutableListOf<String>()
        val objects = mutableListOf<String>()
        var prep: String? = null
        val buf = mutableListOf<String>()
        fun flush(): Boolean {
            if (buf.isEmpty()) return true
            val p = prep
            if (p == null) {
                // "show me your name": the person in को, the thing as it is.
                val two = buf.size > 1 && buf[0] in PRONOUNS && v.post == "को"
                val obj = np(if (two) buf.drop(1) else buf) ?: return false
                if (two) objects += PRONOUNS.getValue(buf[0]).post("को")
                objects += when { two -> obj.direct; v.post != null -> obj.post(v.post); obj.pron != null -> obj.post("को"); else -> obj.direct }
            } else {
                val obj = np(buf) ?: return false
                places += if (p.isEmpty()) obj.direct else obj.post(p)
            }
            buf.clear()
            return true
        }
        for (w in t.drop(n)) when (w) {
            in ADV -> { if (!flush()) return null; adverbs += ADV.getValue(w) }
            in PREPS -> { if (!flush()) return null; prep = PREPS.getValue(w) }
            else -> buf += w
        }
        if (!flush()) return null
        return Vp((adverbs + places + objects).joinToString(" "), v.stem)
    }

    /** The subject's state: "चोट लगी है" in को, or a word after है. Returns the person's words, the state, and which kind. */
    private fun state(t: List<String>): Triple<List<String>, String, Boolean>? {
        for (k in minOf(2, t.size - 1) downTo 1) {
            val key = t.takeLast(k).joinToString(" ")
            FEEL[key]?.let { return Triple(t.dropLast(k), it, true) }
            BE[key]?.let { return Triple(t.dropLast(k), it, false) }
        }
        return null
    }

    private fun aboutYou(rest: String): String? =
        FEEL[rest]?.let { "आपको $it" } ?: BE[rest]?.let { "आप $it हैं" } ?: np(rest)?.let { "आप ${it.direct} हैं" }

    /** "The doctor is coming": रहा, रही or रहे after the verb, agreeing with the person. */
    private fun progressive(who: String, verb: String, rest: String): String? {
        val key = listOf(verb, verb + "e", verb.dropLast(1)).firstOrNull { it in VERBS } ?: return null
        val np = np(who) ?: return null
        val vp = vp("$key $rest".trim()) ?: return null
        val aux = if (np.fem) "रही" else if (np.many) "रहे" else "रहा"
        return "${np.direct} ${vp.then(vp.stem)} $aux ${np.be}।"
    }

    private val templates: List<Pair<Regex, (List<String>) -> String?>> = listOf(
        Regex("(please )?do not (.+)") to { g -> vp(g[2])?.let { (if (g[1].isEmpty()) "" else "कृपया ") + own(it.then(notImp(it.stem))) + "।" } },
        Regex("(?:please )?(?:can|could) you (.+)") to { g -> vp(g[1])?.let { "क्या आप ${own(it.then(it.stem))} सकते हैं?" } },
        Regex("you can (.+)") to { g -> vp(g[1])?.let { "आप ${own(it.then(it.stem))} सकते हैं।" } },
        Regex("do you want to (.+)") to { g -> vp(g[1])?.let { "क्या आप ${own(it.then(it.stem + "ना"))} चाहते हैं?" } },
        Regex("do you need to (.+)") to { g -> vp(g[1])?.let { "क्या आपको ${own(it.then(it.stem + "ना"))} है?" } },
        Regex("do you (?:want|need) (.+)") to { g -> np(g[1])?.let { "क्या आपको ${own(it.direct)} चाहिए?" } },
        Regex("do you have (.+)") to { g -> np(g[1])?.let { "क्या आपके पास ${it.direct} ${it.be}?" } },
        Regex("what do you (?:want|need)") to { _ -> "आपको क्या चाहिए?" },
        Regex("(what|where|when|how|why) do you (.+)") to { g -> vp(g[2])?.let { "आप ${WH[g[1]]} ${own(it.then(it.stem + "ते"))} हैं?" } },
        Regex("do you (.+)") to { g -> vp(g[1])?.let { "क्या आप ${own(it.then(it.stem + "ते"))} हैं?" } },
        Regex("are you (.+)") to { g -> aboutYou(g[1])?.let { "क्या $it?" } },
        Regex("you are (.+)") to { g -> aboutYou(g[1])?.let { "$it।" } },
        Regex("i am (.+)") to { g -> FEEL[g[1]]?.let { "मुझे $it।" } ?: (BE[g[1]] ?: np(g[1])?.direct)?.let { "मैं $it हूँ।" } },
        Regex("where (is|are) (.+)") to { g -> np(g[2])?.let { "${it.direct} कहाँ ${if (g[1] == "are") "हैं" else it.be}?" } },
        Regex("who (?:is|are) with (.+)") to { g -> np(g[1])?.let { "${it.post("के साथ")} कौन है?" } },
        Regex("who (?:is|are) (.+)") to { g -> np(g[1])?.let { "${it.direct} कौन ${it.be}?" } },
        Regex("how old (?:is|are) (.+)") to { g -> np(g[1])?.let { "${it.post("की")} उम्र कितनी है?" } },
        Regex("what (?:is|are) (.+)") to { g -> np(g[1])?.let { "${it.direct} क्या ${it.be}?" } },
        Regex("how many (.+?) do you have") to { g -> np(g[1])?.let { "${if (it.fem) "आपकी कितनी" else "आपके कितने"} ${it.direct} हैं?" } },
        Regex("how many (.+?)( are there| is there| are| were there)?") to { g -> np(g[1])?.let { "${if (it.fem) "कितनी" else "कितने"} ${it.direct}${if (g[2].isEmpty()) "" else " हैं"}?" } },
        Regex("is (?:this|that|it) (.+)") to { g -> np(g[1])?.let { "क्या यह ${it.direct} ${it.be}?" } },
        Regex("(?:this|that|it) is (.+)") to { g -> np(g[1])?.let { "यह ${it.direct} ${it.be}।" } },
        Regex("did (.+) (${DONE.keys.joinToString("|")}) (.+)") to { g ->
            val who = np(g[1]); val whom = np(g[3])
            if (who == null || whom == null) null else "क्या ${who.post("ने")} ${whom.post("को")} ${DONE[g[2]]}?"
        },
        Regex("(when|where|how|why) did (?:it|this|that) happen") to { g -> "यह ${WH[g[1]]} हुआ?" },
        Regex("what happened to (.+)") to { g -> np(g[1])?.let { "${it.post("के साथ")} क्या हुआ?" } },
        Regex("(?:please )?(show|tell) me ((?:what|where|who|when|how|why) .+)") to { g ->
            sentence(g[2])?.hi?.trimEnd('?', '।')?.let { "मुझे ${if (g[1] == "show") "दिखाइए" else "बताइए"} कि $it।" }
        },
        Regex("(?:please )?show me (.+)") to { g -> np(g[1])?.let { "मुझे ${own(it.direct)} दिखाइए।" } },
        Regex("(?:i|we) will (.+)") to { g -> vp(g[1])?.let { "हम ${it.then(fut(it.stem))}।" } },
        Regex("will you (.+)") to { g -> vp(g[1])?.let { "क्या आप ${own(it.then(fut(it.stem)))}?" } },
        Regex("i want to (.+)") to { g -> vp(g[1])?.let { "मुझे ${it.then(it.stem + "ना")} है।" } },
        Regex("i (?:want|need) (.+)") to { g -> np(g[1])?.let { "मुझे ${it.direct} चाहिए।" } },
        Regex("(is|are) (.+)") to { g ->
            state(g[2].split(' '))?.let { (who, what, feel) -> np(who)?.let { if (feel) "क्या ${it.post("को")} $what?" else "क्या ${it.direct} $what ${it.be}?" } }
        },
        Regex("(.+) (?:is|are) (\\S+?)ing(?: (.+))?") to { g -> progressive(g[1], g[2], g[3]) },
        Regex("(.+) (?:is|are) (.+)") to { g ->
            state((g[1] + " " + g[2]).split(' '))?.let { (who, what, feel) -> np(who)?.let { if (feel) "${it.post("को")} $what।" else "${it.direct} $what ${it.be}।" } }
        },
    )

    fun sentence(text: String): HindiDraft? {
        val s = normalise(text).ifEmpty { return null }
        val asked = text.trimEnd().endsWith("?")
        FIXED[s]?.let { return HindiDraft(it, Made.TEMPLATE) }
        for ((re, make) in templates) {
            val m = re.matchEntire(s) ?: continue
            make(m.groupValues)?.let { return HindiDraft(it, Made.TEMPLATE) }
        }
        if (!asked) {
            val please = s.startsWith("please ")
            vp(s.removePrefix("please "))?.let { return HindiDraft((if (please) "कृपया " else "") + own(it.then(imp(it.stem))) + "।", Made.TEMPLATE) }
        }
        np(s.split(' '))?.let { return HindiDraft(it.direct + if (asked) "?" else "", Made.TEMPLATE) }
        return words(s.split(' '), asked)?.let { HindiDraft(it, Made.WORDS) }
    }

    /** Word by word in Hindi order: doer, the rest as typed, question word, नहीं, verb. Null at the first unknown word. */
    private fun words(t: List<String>, asked: Boolean): String? {
        // No modal forms yet: "you can not go" must not become "you do not go".
        if ("can" in t || "could" in t) return null
        var doer: String? = null
        var verb: String? = null
        var want = false
        var neg = false
        var wh: String? = null
        val mid = mutableListOf<String>()
        val run = mutableListOf<String>()
        fun flush(): Boolean {
            if (run.isEmpty()) return true
            mid += (np(run) ?: return false).let { if (verb != null && it.pron != null) it.post("को") else it.direct }
            run.clear()
            return true
        }
        for ((i, w) in t.withIndex()) {
            val v = VERBS[w] ?: VERBS[w.removeSuffix("s")] ?: VERBS[w.removeSuffix("ing")] ?: VERBS[w.removeSuffix("ing") + "e"]
            when {
                w in GLUE -> if (!flush()) return null
                w in NOT -> { if (!flush()) return null; neg = true }
                w in WH -> { if (!flush()) return null; wh = WH[w] }
                w in ADV -> { if (!flush()) return null; mid += ADV.getValue(w) }
                w == "want" || w == "need" -> { if (!flush()) return null; want = true }
                doer == null && verb == null && mid.isEmpty() && run.isEmpty() && w in setOf("i", "you", "we") -> doer = w
                v != null && verb == null -> { if (!flush()) return null; verb = v.stem }
                else -> run += w
            }
            if (i == t.lastIndex && !flush()) return null
        }
        val no = if (neg) "नहीं" else null
        val hi = when {
            want -> listOfNotNull(doer?.let { PRONOUNS.getValue(it).post("को") }) + mid + listOfNotNull(wh, no, verb?.let { it + "ना" }, if (verb != null) "है" else "चाहिए")
            verb != null && doer != null -> listOf(if (doer == "you") "आप" else "हम") + mid + listOfNotNull(wh, no, verb + "ते", "हैं")
            verb != null -> mid + listOfNotNull(wh, if (neg) notImp(verb) else imp(verb))
            else -> listOfNotNull(doer?.let { PRONOUNS.getValue(it).direct }) + mid + listOfNotNull(wh, no)
        }
        if (hi.isEmpty()) return null
        val ask = asked && wh == null && verb != null
        return (if (ask) "क्या " else "") + hi.joinToString(" ") + if (asked) "?" else if (verb != null || want) "।" else ""
    }
}
