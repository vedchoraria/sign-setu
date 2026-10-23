package app.signsetu.data.content

import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role

/** Whether this person may be asked [q]: welfare-only items leave out an accused, some items a young child. */
fun askable(q: Question, role: Role, profile: Profile) = (q.roles.isEmpty() || role in q.roles) && profile !in q.hiddenFor

/** Questions marked signed get one motion from the shared text-to-sign path, in draft ISL order. */
fun ContentBundle.withSignedQuestions(): ContentBundle {
    // A q.* gesture was planned in another sign language; it is planned again here.
    val made = questions.filter { it.signed && (it.gesture == null || it.gesture == "q.${it.id}") && it.lexicon == null }.mapNotNull { q ->
        q.text["en"]?.let { planText(it, setOf(SignMode.SIGNS)).gesture }?.let { q.id to it.copy(id = "q.${q.id}") }
    }.toMap()
    if (made.isEmpty()) return this
    return copy(
        questions = questions.map { q -> made[q.id]?.let { q.copy(gesture = it.id) } ?: q },
        gestures = gestures + made.values.associateBy { it.id },
    )
}

/** How the subject gave an answer, kept with it so the record says how it was given. */
enum class Method { WRITTEN, PAD, PICTURE, PICKER, SIGN, BOARD }

/** One answer given through a lesson-sheet input or the phrase board. [value] is what was written or typed on a
 * pad; [picks] are card ids, or a state id then a district id. */
data class Given(
    val question: String,
    val method: Method,
    val confirmed: Boolean,
    val value: String = "",
    val picks: List<String> = emptyList(),
)

private const val GIVEN_PREFIX = "given:"

/** Stored in the answer row's reason column; names no subject data. */
fun Given.reason() = "$GIVEN_PREFIX${method.name}:${if (confirmed) 1 else 0}"

fun givenOf(reason: String?): Pair<Method, Boolean>? {
    val parts = reason?.takeIf { it.startsWith(GIVEN_PREFIX) }?.removePrefix(GIVEN_PREFIX)?.split(':') ?: return null
    val method = Method.entries.firstOrNull { it.name == parts.firstOrNull() } ?: return null
    return method to (parts.getOrNull(1) == "1")
}

const val NAME_MAX = 60
const val TEXT_MAX = 80
const val PHONE_DIGITS = 10
val AGES = 0..120
val COUNTS = 0..50
val CLASSES = (1..12).map(Int::toString) + listOf("college", "not_in_school")

/** Digits a pad holds for each answer; zero means the answer is not typed on a pad. */
fun padLength(type: AnswerType) = when (type) {
    AnswerType.AGE -> 3
    AnswerType.COUNT, AnswerType.SIBLINGS -> 2
    AnswerType.PHONE -> PHONE_DIGITS
    else -> 0
}

/** The pad's digits after one more key; past the answer's length the key does nothing. */
fun padPress(type: AnswerType, digits: String, key: Char): String = when {
    !key.isDigit() -> digits
    digits.length >= padLength(type) -> digits
    else -> digits + key
}

/** Indian mobile numbers have ten digits and start with 6 to 9. */
fun phoneOk(s: String) = s.length == PHONE_DIGITS && s.all { it in '0'..'9' } && s[0] in '6'..'9'

/** Written answers need a letter; a name has no digits, so a number typed by mistake is caught. */
fun valid(type: AnswerType, value: String): Boolean {
    val v = value.trim()
    return when (type) {
        AnswerType.NAME -> v.length in 1..NAME_MAX && v.any(Char::isLetter) && v.none(Char::isDigit)
        AnswerType.TEXT -> v.length in 1..TEXT_MAX && v.any(Char::isLetter)
        AnswerType.AGE -> v.length <= 3 && v.toIntOrNull()?.let { it in AGES } == true
        AnswerType.COUNT -> v.length <= 2 && v.toIntOrNull()?.let { it in COUNTS } == true
        AnswerType.PHONE -> phoneOk(v)
        AnswerType.SIBLINGS -> v.split('/').let { p -> p.size == 2 && p.all { n -> n.length <= 2 && n.toIntOrNull()?.let { it in COUNTS } == true } }
        AnswerType.CLASS -> v in CLASSES
        AnswerType.STATE, AnswerType.DISTRICT, AnswerType.PLACE, AnswerType.PICTURES -> true
    }
}

/** Ten digits read back in two groups of five, as people say a mobile number. */
fun groupPhone(digits: String) = if (digits.length > 5) digits.take(5) + " " + digits.drop(5) else digits

private fun Localized.or(lang: String) = this[lang] ?: this["en"]

fun ContentBundle.state(id: String?) = states.firstOrNull { it.id == id }

/** A place as an address line: anything written, the district if picked, then the state, in [lang] or else English. */
fun ContentBundle.placeText(picks: List<String>, written: String, lang: String): String {
    val state = state(picks.firstOrNull())
    val district = state?.districts?.firstOrNull { it.id == picks.getOrNull(1) }
    return listOfNotNull(written.trim().ifEmpty { null }, district?.name?.or(lang), state?.name?.or(lang)).joinToString(", ")
}

/** The answer as one readable line in [lang], for the read-back, the transcript and the report. */
fun ContentBundle.answerText(type: AnswerType?, g: Given, lang: String): String = when (type) {
    AnswerType.PICTURES -> g.picks.joinToString(", ") { cards[it]?.label?.or(lang) ?: it }
    AnswerType.STATE, AnswerType.DISTRICT, AnswerType.PLACE -> placeText(g.picks, g.value, lang)
    AnswerType.PHONE -> groupPhone(g.value)
    AnswerType.CLASS -> g.value.toIntOrNull()?.let { n -> subjectUi["class_n"]?.or(lang)?.format(n) } ?: subjectUi[g.value]?.or(lang) ?: g.value
    AnswerType.SIBLINGS -> g.value.split('/').let { p -> "${subjectUi["brothers"]?.or(lang)} ${p.getOrNull(0)}, ${subjectUi["sisters"]?.or(lang)} ${p.getOrNull(1)}" }
    null -> question(g.question)?.text?.or(lang) ?: g.value
    else -> g.value.trim()
}
