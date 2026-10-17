package app.signsetu.data.content

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.ui.avatar.Gesture
import app.signsetu.ui.avatar.chain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

typealias Localized = Map<String, String>

fun Localized.pick(lang: String): String? = this[lang]

/**
 * [rights] items are shown, never taken as understood: each carries the interpreter notice. [child] true is only for a
 * person who may be under 18, false only for an adult, null for anyone: a child is not told adult arrest rights.
 */
@Immutable @Serializable
data class Group(
    val id: String,
    val label: Localized,
    val sensitive: Boolean = false,
    val roles: List<Role> = emptyList(),
    val rights: Boolean = false,
    val child: Boolean? = null,
)

/**
 * Where an item sits in a first-contact conversation; declaration order is the auto mode's proposal order. MANUAL items,
 * such as personal details and everyday phrases, are only ever picked by the officer.
 */
@Serializable
enum class Phase { RAPPORT, GROUND_RULES, WELFARE, PRACTICAL, CLOSING, BREAK, RIGHTS, FREE_RECALL, FOLLOW_UP, MANUAL }

@Serializable
enum class Kind { STATEMENT, INVITATION, DIRECTIVE, OPTION }

@Immutable @Serializable
data class Question(
    val id: String,
    val group: String,
    val text: Localized,
    val cards: List<String> = emptyList(),
    val lexicon: String? = null,
    /** Character animation; defaults to the lexicon entry's. */
    val gesture: String? = null,
    val phase: Phase? = null,
    val kind: Kind = Kind.DIRECTIVE,
    /** "s2:yes" or "s2:any": asked only after that answer to that item. */
    val after: String? = null,
    /** How the subject gives the answer on the stage (LessonInputs.kt); null keeps the usual answers. */
    val answer: AnswerType? = null,
    /** What the answer is, as a field of the report's personal details. */
    val field: Localized? = null,
    /** Roles this item is for, empty for everyone: welfare-only items leave out an accused. */
    val roles: List<Role> = emptyList(),
    /** Profiles never asked this item, such as a young child for a mobile number. */
    val hiddenFor: List<Profile> = emptyList(),
    /** The character signs the English text through the text-to-sign path, in draft ISL order. */
    val signed: Boolean = false,
)

@Serializable
enum class AnswerType { NAME, TEXT, AGE, COUNT, PHONE, SIBLINGS, CLASS, STATE, DISTRICT, PLACE, PICTURES }

@Immutable @Serializable
data class District(val id: String, val name: Localized)

/** A state or union territory; only some carry their districts. */
@Immutable @Serializable
data class State(val id: String, val name: Localized, val districts: List<District> = emptyList())

@Immutable @Serializable
data class Card(val id: String, val category: String, val label: Localized)

@Immutable @Serializable
data class CardCategory(val id: String, val label: Localized)

@Serializable
enum class BodyLocation { HEAD, FOREHEAD, EYES, NOSE, EAR, FACE, MOUTH, CHIN, CHEST, BELLY, ARM, HANDS, AWAY }

@Serializable
enum class HandAction { TOUCH, STROKE, POINT, CIRCLE, FLAT_HAND, FIST, BOTH_HANDS, MOVE, HEAD_MOVE }

@Immutable @Serializable
data class LexiconEntry(
    val id: String,
    val concept: String,
    val card: String,
    val locations: List<BodyLocation>,
    val actions: List<HandAction>,
    val label: Localized,
    val how: Localized,
)

@Immutable @Serializable
data class Language(val code: String, val name: String, val english: String, val greeting: String) {
    /** The salutation alone ("नमस्ते"), without the language finder's instruction that follows it. */
    val hello: String get() = greeting.split('।', '.', '۔').first().trim()
}

/** One slot filled: the picture, the sign for it, and the whole reviewed sentence, since agreement differs by language. */
@Immutable @Serializable
data class PhraseSlot(val card: String, val sign: String? = null, val text: Localized)

/** A phrase-bank sentence (FR-62): [signs] play in order, with [SLOT] standing for the chosen slot's sign. */
@Immutable @Serializable
data class Phrase(val id: String, val label: Localized, val signs: List<String>, val slots: List<PhraseSlot>) {
    companion object { const val SLOT = "$" }
}

@Serializable private data class PhrasesFile(val phrases: List<Phrase>)
@Serializable private data class TriageFile(val groups: List<Group>, val questions: List<Question>)
@Serializable private data class CardsFile(val categories: List<CardCategory>, val cards: List<Card>)
@Serializable private data class LexiconFile(val entries: List<LexiconEntry>)
@Serializable private data class LanguagesFile(val languages: List<Language>)
@Serializable private data class PlacesFile(val states: List<State>)
@Serializable private data class Manifest(val files: Map<String, String>)

/** Files hashed on every load. Models are large and their loaders check them against the manifest on first use. */
internal fun eagerlyChecked(path: String) = !path.startsWith("models/")
@Serializable private data class GesturesFile(val gestures: List<Gesture>)
@Serializable private data class WordsFile(val words: List<WordEntry>)
@Serializable private data class SignWordsFile(val words: Map<String, String>)

/** Motion files: a failed one only loses the character's motions, never the words a subject is shown. */
private val MOTION_FILES = setOf("gestures.json", "isl_signs.json") + SignLanguage.entries.map { it.signs }

@Immutable
data class ContentBundle(
    val groups: List<Group>,
    val questions: List<Question>,
    val categories: List<CardCategory>,
    val cards: Map<String, Card>,
    val lexicon: Map<String, LexiconEntry>,
    val languages: List<Language>,
    val subjectUi: Map<String, Localized>,
    /** Files whose hash did not match the manifest; never shown to a subject (SEC-54). */
    val rejected: Set<String>,
    /** Every verified file in the bundle, relative to `assets/content/`. */
    val files: Set<String> = emptySet(),
    val gestures: Map<String, Gesture> = emptyMap(),
    val phrases: List<Phrase> = emptyList(),
    /** Every written form of a vocabulary word, lower case, to its entry. */
    val words: Map<String, WordEntry> = emptyMap(),
    /** SHA-256 of manifest.json; a second screen must hold the same content (D-26). */
    val manifestHash: String = "",
    /** Start of the manifest's SHA-256: names the exact content a prompt was drawn from. */
    val version: String = "",
    /** Signs retargeted from recorded signers (isl_signs.json); their motions are also in [gestures]. */
    val isl: IslSigns = IslSigns(),
    /** Sign reader labels to a written form in [words] (sign_words.json). */
    val signWords: Map<String, String> = emptyMap(),
    /** States and union territories for the place picker, Chhattisgarh first. */
    val states: List<State> = emptyList(),
    /** Everyday gestures alone (gestures.json): what an ASL session keeps of ISL's motions ([forSignLanguage]). */
    val everyday: Map<String, Gesture> = emptyMap(),
    /** Signs retargeted from recorded ASL signers plus the one-hand alphabet (asl_signs.json). */
    val aslSigns: IslSigns = IslSigns(),
    /** ASL reader labels to a written form in [words] (asl_words.json). */
    val aslWords: Map<String, String> = emptyMap(),
) {
    /** A group with no roles listed is for everyone. */
    fun groupsFor(role: Role, child: Boolean = false): List<Group> =
        groups.filter { (it.roles.isEmpty() || role in it.roles) && (it.child == null || it.child == child) }

    fun questionsIn(group: String, role: Role, profile: Profile): List<Question> =
        questions.filter { it.group == group && askable(it, role, profile) }

    /** Every question the group carousel reaches for this person: each shown group's askable questions. */
    fun carouselQuestions(role: Role, child: Boolean, profile: Profile): List<Question> =
        groupsFor(role, child).flatMap { questionsIn(it.id, role, profile) }

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }
    fun question(id: String): Question? = questions.firstOrNull { it.id == id }
    fun isRights(q: Question) = group(q.group)?.rights == true

    /** A question with no gesture of its own is signed from its English wording through the lexicon. */
    fun gestureFor(q: Question): Gesture? =
        (q.gesture ?: q.lexicon)?.let { gestures[it] } ?: q.text["en"]?.let { planText(it, setOf(SignMode.SIGNS)).gesture }

    /** The phrase's signs as one gesture; a sign with no reviewed animation is left out. */
    fun gestureFor(phrase: Phrase, slot: PhraseSlot): Gesture? =
        chain("${phrase.id}:${slot.card}", phrase.signs.mapNotNull { (if (it == Phrase.SLOT) slot.sign else it)?.let(gestures::get) })
            .takeIf { it.keys.isNotEmpty() }

    private fun audio(path: String) = path.takeIf { it in files }
    fun questionAudio(questionId: String, lang: String) = audio("audio/$lang/$questionId.ogg")
    fun greetingAudio(lang: String) = audio("audio/greet/$lang.ogg")
    fun subjectAudio(key: String, lang: String) = audio("audio/$lang/$key.ogg")
    fun phraseAudio(phrase: Phrase, slot: PhraseSlot, lang: String) = audio("audio/$lang/phrase.${phrase.id}.${slot.card}.ogg")

    fun language(code: String): Language? = languages.firstOrNull { it.code == code }
    fun subjectText(key: String, lang: String): String? = subjectUi[key]?.pick(lang)
    fun hasCardImage(id: String) = "cards/$id.webp" in files

    /** A text file failed its check, so the subject could be shown wrong words: no session may start (SEC-54). */
    val damaged: Boolean get() = rejected.any { it.endsWith(".json") && it !in MOTION_FILES }

    /** Gesture lookup: both location and action match first, then location only, then action only. */
    fun lookup(location: BodyLocation?, action: HandAction?): List<LexiconEntry> {
        if (location == null && action == null) return emptyList()
        return lexicon.values
            .map { e ->
                val l = location != null && location in e.locations
                val a = action != null && action in e.actions
                e to (if (l) 2 else 0) + (if (a) 1 else 0)
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

class ContentRepository(private val assets: AssetManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private var bundle: ContentBundle? = null
    private val images = LruCache<String, ImageBitmap>(48)

    suspend fun load(): ContentBundle = bundle ?: withContext(Dispatchers.IO) {
        val manifestText = read("manifest.json")
        val manifest = json.decodeFromString<Manifest>(manifestText)
        val rejected = manifest.files.filter { (path, hash) -> eagerlyChecked(path) && sha256(path) != hash }.keys
        // A file that fails its check is never parsed: no content beats wrong content (SEC-54).
        val triage = verified("triage.json", rejected) ?: TriageFile(emptyList(), emptyList())
        val cards = verified("cards.json", rejected) ?: CardsFile(emptyList(), emptyList())
        val lexicon = verified("lexicon.json", rejected) ?: LexiconFile(emptyList())
        val languages = verified("languages.json", rejected) ?: LanguagesFile(emptyList())
        val subjectUi = verified<Map<String, Localized>>("subject_ui.json", rejected).orEmpty()
        val isl = verified<IslSigns>("isl_signs.json", rejected) ?: IslSigns()
        val everyday = verified<GesturesFile>("gestures.json", rejected)?.gestures.orEmpty()
        val gestures = everyday + verified<GesturesFile>("signs.json", rejected)?.gestures.orEmpty() + isl.gestures.map { it.gesture }
        val asl = SignLanguage.ASL.signs.takeIf { it in manifest.files }?.let { verified<IslSigns>(it, rejected) } ?: IslSigns()
        val aslWords = SignLanguage.ASL.words.takeIf { it in manifest.files }?.let { verified<SignWordsFile>(it, rejected) }?.words.orEmpty()
        val words = verified<WordsFile>("words.json", rejected)?.words.orEmpty()
        val phrases = verified<PhrasesFile>("phrases.json", rejected)?.phrases.orEmpty()
        val states = verified<PlacesFile>("places.json", rejected)?.states.orEmpty()
        ContentBundle(
            groups = triage.groups,
            questions = triage.questions,
            categories = cards.categories,
            cards = cards.cards.associateBy { it.id },
            lexicon = lexicon.entries.associateBy { it.id },
            languages = languages.languages,
            subjectUi = subjectUi,
            rejected = rejected,
            files = manifest.files.keys - rejected,
            gestures = gestures.associateBy { it.id },
            everyday = everyday.associateBy { it.id },
            aslSigns = asl,
            aslWords = aslWords,
            phrases = phrases,
            words = words.flatMap { e -> e.w.map { it to e } }.toMap(),
            manifestHash = sha256("manifest.json").orEmpty(),
            isl = isl,
            signWords = verified<SignWordsFile>("sign_words.json", rejected)?.words.orEmpty(),
            version = MessageDigest.getInstance("SHA-256").digest(manifestText.toByteArray()).take(6).joinToString("") { "%02x".format(it) },
            states = states,
        ).withSignedQuestions().also { bundle = it }
    }

    suspend fun cardImage(id: String): ImageBitmap? {
        images.get(id)?.let { return it }
        if (bundle?.hasCardImage(id) == false) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                assets.open("content/cards/$id.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }.getOrNull()?.also { images.put(id, it) }
        }
    }

    private inline fun <reified T> verified(name: String, rejected: Set<String>): T? =
        if (name in rejected) null else json.decodeFromString<T>(read(name))

    private fun read(name: String): String = assets.open("content/$name").bufferedReader().use { it.readText() }

    private fun sha256(path: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.open("content/$path").use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
