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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias Localized = Map<String, String>

/** The text in [lang], else the English. */
fun Localized.or(lang: String): String? = this[lang] ?: this["en"]

/** [rights] items are shown, never taken as understood: each carries the interpreter notice. [child] true is
 * only for a person who may be under 18, false only for an adult. */
@Immutable @Serializable
data class Group(
    val id: String,
    val label: Localized,
    val sensitive: Boolean = false,
    val roles: List<Role> = emptyList(),
    val rights: Boolean = false,
    val child: Boolean? = null,
)

/** Where an item sits in a first-contact conversation; declaration order is the auto mode's proposal order.
 * MANUAL items, such as personal details and everyday phrases, are only ever picked by the officer. */
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

/** A phrase-bank sentence: [signs] play in order, with [SLOT] standing for the chosen slot's sign. */
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

/** Files hashed on every load: the JSON a subject's words come from. Pictures and recordings are hashed on first
 * use ([ContentRepository.intact]), and models by their loaders, so a cold start does not hash 25 MB. */
internal fun eagerlyChecked(path: String) = path.endsWith(".json")
@Serializable private data class GesturesFile(val gestures: List<Gesture>)
@Serializable private data class WordsFile(val words: List<WordEntry>)
@Serializable private data class SignWordsFile(val words: Map<String, String>)

/** Motion files: a failed one only loses the character's motions, never the words a subject is shown. */
private val MOTION_FILES = setOf("gestures.json", "isl_signs.json") + SignLanguage.entries.map { it.signs }

/** A failure here turns that sign language off (see [ContentBundle.aslDisabled]); the other language still starts. */
private val LANGUAGE_FILES = setOf(SignLanguage.ASL.words, SignLanguage.ASL.signs)

@Immutable
data class ContentBundle(
    val groups: List<Group> = emptyList(),
    val questions: List<Question> = emptyList(),
    val categories: List<CardCategory> = emptyList(),
    val cards: Map<String, Card> = emptyMap(),
    val lexicon: Map<String, LexiconEntry> = emptyMap(),
    val languages: List<Language> = emptyList(),
    val subjectUi: Map<String, Localized> = emptyMap(),
    /** Files whose hash did not match the manifest; never shown to a subject. */
    val rejected: Set<String> = emptySet(),
    /** Every verified file in the bundle, relative to `assets/content/`. */
    val files: Set<String> = emptySet(),
    val gestures: Map<String, Gesture> = emptyMap(),
    val phrases: List<Phrase> = emptyList(),
    /** Every written form of a vocabulary word, lower case, to its entry. */
    val words: Map<String, WordEntry> = emptyMap(),
    /** SHA-256 of manifest.json; a second screen must hold the same content. */
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
    /** The language this bundle's character signs in ([forSignLanguage]); ASL text keeps typed order. */
    val language: SignLanguage = SignLanguage.ISL,
    /** ASL reader labels to a written form in [words] (asl_words.json). */
    val aslWords: Map<String, String> = emptyMap(),
) {
    /** A group with no roles listed is for everyone. */
    fun groupsFor(role: Role, child: Boolean = false): List<Group> =
        groups.filter { (it.roles.isEmpty() || role in it.roles) && (it.child == null || it.child == child) }

    fun questionsIn(group: String, role: Role, profile: Profile): List<Question> =
        questions.filter { it.group == group && askable(it, role, profile) }

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
    fun subjectText(key: String, lang: String): String? = subjectUi[key]?.get(lang)
    fun hasCardImage(id: String) = "cards/$id.webp" in files

    /** A text file failed its check, so the subject could be shown wrong words: no session may start. */
    val damaged: Boolean get() = rejected.any { it.endsWith(".json") && it !in MOTION_FILES && it !in LANGUAGE_FILES }

    /** ASL's files failed their check, so ASL is off: its signs and words are dropped, ISL still starts. */
    val aslDisabled: Boolean get() = rejected.any { it in LANGUAGE_FILES }

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

/** Loads the bundle from [open], which opens a file by its path under `assets/content/`. */
class ContentRepository(private val open: (String) -> InputStream) {
    constructor(assets: AssetManager) : this({ assets.open("content/$it") })

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var bundle: ContentBundle? = null
    private val images = LruCache<String, ImageBitmap>(160)
    private val loading = Mutex()
    @Volatile private var hashes: Map<String, String> = emptyMap()
    private val checked = ConcurrentHashMap<String, Boolean>()

    /** A picture or recording matches the manifest; hashed once, on first use. One that fails is never shown or played. */
    fun intact(path: String): Boolean = checked.getOrPut(path) { hashes[path]?.let { sha256(path) == it } == true }

    suspend fun load(): ContentBundle = bundle ?: loading.withLock { bundle ?: loadOnce() }

    private suspend fun loadOnce(): ContentBundle = withContext(Dispatchers.IO) {
        // Without a readable manifest no file can be trusted. Return the damaged state instead of crashing before
        // the first-run screen can explain that this copy must be replaced.
        val manifestBytes = runCatching { open("manifest.json").use { it.readBytes() } }.getOrNull()
            ?: return@withContext ContentBundle(rejected = setOf("manifest.json")).also { bundle = it }
        val manifestDigest = MessageDigest.getInstance("SHA-256").digest(manifestBytes)
        val manifest = runCatching { json.decodeFromString<Manifest>(manifestBytes.decodeToString()) }.getOrNull()
            ?: return@withContext ContentBundle(rejected = setOf("manifest.json")).also { bundle = it }
        hashes = manifest.files
        val rejected = manifest.files.filter { (path, hash) -> eagerlyChecked(path) && sha256(path) != hash }.keys.toMutableSet()
        // A file that fails its check is never parsed: no content beats wrong content.
        val triage = verified("triage.json", rejected) ?: TriageFile(emptyList(), emptyList())
        val cards = verified("cards.json", rejected) ?: CardsFile(emptyList(), emptyList())
        val lexicon = verified("lexicon.json", rejected) ?: LexiconFile(emptyList())
        val languages = verified("languages.json", rejected) ?: LanguagesFile(emptyList())
        val subjectUi = verified<Map<String, Localized>>("subject_ui.json", rejected).orEmpty()
        val isl = verified<IslSigns>("isl_signs.json", rejected) ?: IslSigns()
        val everyday = verified<GesturesFile>("gestures.json", rejected)?.gestures.orEmpty()
        val gestures = everyday + verified<GesturesFile>("signs.json", rejected)?.gestures.orEmpty() + isl.gestures.map { it.gesture }
        val aslOff = rejected.any { it in LANGUAGE_FILES }
        val asl = SignLanguage.ASL.signs.takeIf { it in manifest.files && !aslOff }?.let { verified<IslSigns>(it, rejected) } ?: IslSigns()
        val aslWords = SignLanguage.ASL.words.takeIf { it in manifest.files && !aslOff }?.let { verified<SignWordsFile>(it, rejected) }?.words.orEmpty()
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
            rejected = rejected.toSet(),
            files = manifest.files.keys - rejected,
            gestures = gestures.associateBy { it.id },
            everyday = everyday.associateBy { it.id },
            aslSigns = asl,
            aslWords = aslWords,
            phrases = phrases,
            words = words.flatMap { e -> e.w.map { it to e } }.toMap(),
            manifestHash = manifestDigest.toHexString(),
            isl = isl,
            signWords = verified<SignWordsFile>("sign_words.json", rejected)?.words.orEmpty(),
            version = manifestDigest.copyOf(6).toHexString(),
            states = states,
        ).withSignedQuestions().also {
            bundle = it
            // The answers every session offers paint on their first frame.
            for (id in PRELOADED_CARDS) cardImage(id)
        }
    }

    /** Memory-only peek, so a cached card paints on the first frame with no placeholder. */
    fun cachedCardImage(id: String): ImageBitmap? = images.get(id)

    suspend fun cardImage(id: String): ImageBitmap? {
        images.get(id)?.let { return it }
        if (bundle?.hasCardImage(id) == false) return null
        return withContext(Dispatchers.IO) {
            if (!intact("cards/$id.webp")) return@withContext null
            runCatching {
                open("cards/$id.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }.getOrNull()?.also { images.put(id, it) }
        }
    }

    /** A file that matches its hash can still be unreadable or malformed; mark it rejected rather than crashing the app. */
    private inline fun <reified T> verified(name: String, rejected: MutableSet<String>): T? {
        if (name in rejected) return null
        return try {
            json.decodeFromString<T>(open(name).bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            rejected += name
            null
        }
    }

    private fun sha256(path: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        open(path).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().toHexString()
    }.getOrNull()
}

private val PRELOADED_CARDS = listOf(
    "yes", "no", "stop", "dont_know", "unclear", "wrong", "water", "food", "help", "pain_arm", "pain_head", "pain_chest",
    "pain_stomach", "safe", "hurt",
)
