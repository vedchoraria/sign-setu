package app.signsetu.ui.session

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.SessionRepository
import app.signsetu.camera.LandmarkFrame
import app.signsetu.sign.SignCapture
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignConfirm
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignSegment
import app.signsetu.pair.AnswerBody
import app.signsetu.pair.reencodeRemotePng
import app.signsetu.sign.SignWindow
import app.signsetu.sign.glossOf
import app.signsetu.sign.glossTranscript
import app.signsetu.sign.inContext
import app.signsetu.sign.needsSlower
import app.signsetu.sign.signChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.signsetu.data.content.Given
import app.signsetu.data.content.Method
import app.signsetu.data.content.TEXT_MAX
import app.signsetu.data.content.answerText
import app.signsetu.data.content.reason
import app.signsetu.export.ReportRow
import app.signsetu.export.ReportSection
import app.signsetu.export.ReportText
import app.signsetu.export.SessionReport
import java.io.File

/** What the officer has prepared but not yet shown (FR-15). */
@Immutable
sealed interface Pending {
    data class Ask(val question: Question) : Pending
    /** [caption] is the assistant's translation the officer accepted (D-24); the character still signs [text]. */
    data class FreeText(
        val text: String,
        val cardIds: List<String>,
        val modes: Set<SignMode> = emptySet(),
        val caption: String? = null,
        /** Set when the officer says an answer line back to the subject to confirm its meaning. */
        val ref: Long? = null,
        /** The picture of the officer's reading in a say-back; the subject picks among [cardIds]. */
        val meant: String? = null,
    ) : Pending
    data class Cards(val cardIds: List<String>) : Pending
    /** [choices] are the pictures offered, the entry's among them; fewer than two is a plain Yes or No. */
    data class ShowBack(val entry: LexiconEntry, val choices: List<String> = emptyList()) : Pending
    data class PhraseAsk(val phrase: Phrase, val slot: PhraseSlot) : Pending
    data object LanguageFinder : Pending
    data object Drawing : Pending
    /** The opening: introduction, ground rules and a practice round, one step at a time (GroundRules.kt). */
    data class GroundRules(val step: Int = 0) : Pending
}

enum class Mode { OFFICER, SHOW }

/** A show-back or say-back that asks the subject to pick the meaning among pictures, not to say Yes to one. */
val Pending?.asksChoice: Boolean get() = when (this) {
    is Pending.ShowBack -> choices.size >= 2
    is Pending.FreeText -> meant != null && cardIds.size >= 2
    else -> false
}

/** Machine keys for SYSTEM events; the log shows them in the officer's language. */
const val SYSTEM_LANGUAGE_FINDER = "language_finder"
const val SYSTEM_DRAWING = "drawing"
const val SYSTEM_READ_SIGNS = "read_signs"
const val SYSTEM_BREAK_GIVEN = "break_given"
const val LINK_FALLBACK = "fallback"
const val VIA_STAGE = "stage"
const val VIA_STAGE_LATE = "stage_late"
const val SYSTEM_TELL_ME = "tell_me"
/** The officer was shown the Juvenile Justice notice for an accused person. */
const val SYSTEM_JJ_NOTICE = "jj_notice"
/** The officer marked, or unmarked, an accused person as possibly under 18. */
const val SYSTEM_JUVENILE = "juvenile"
const val SYSTEM_JUVENILE_OFF = "juvenile_off"
/** Rows a session may start with before anything is shown. */
val StartRows = setOf(SYSTEM_JJ_NOTICE, SYSTEM_JUVENILE)
/** Followed by "<shown>/<total>" when a question run ends before its last question. */
const val SYSTEM_RUN_ENDED = "run_ended:"
const val RUN_MAX = 5
private const val RUN_STEP_MS = 1_400L

/** A young child, or an accused the officer marked as possibly under 18: no adult arrest rights are offered. */
val SessionUi.juvenile: Boolean
    get() = session.profile == Profile.YOUNG_CHILD ||
        events.lastOrNull { it.type == EventType.SYSTEM && (it.text == SYSTEM_JUVENILE || it.text == SYSTEM_JUVENILE_OFF) }?.text == SYSTEM_JUVENILE

/** Followed by a [SignLanguage] name: the officer set the language the session is signed in. */
const val SYSTEM_SIGN_LANGUAGE = "sign_language:"

/** The session's sign language, ISL until the officer sets another; it selects the reader and the character's signs. */
val SessionUi.signLanguage: SignLanguage
    get() = events.lastOrNull { it.type == EventType.SYSTEM && it.text?.startsWith(SYSTEM_SIGN_LANGUAGE) == true }
        ?.text?.removePrefix(SYSTEM_SIGN_LANGUAGE)?.let { n -> SignLanguage.entries.firstOrNull { it.name == n } } ?: SignLanguage.ISL

@Immutable
data class SessionUi(
    val session: Session,
    val selectedGroup: String,
    val sensitiveUnlocked: Boolean,
    val pending: Pending?,
    val mode: Mode,
    val rotated: Boolean,
    /** Card or Yes/No the subject chose for the item currently on screen. */
    val answeredCard: String?,
    val answeredYes: Boolean?,
    val chosenLanguage: String?,
    val events: List<SessionEvent>,
    /** Signs read so far while the subject is signing an answer; null when not reading. */
    val reading: List<SignSegment>?,
    /** Landmark frames of each signed answer, by event id: memory only, never stored or exported (D-25). */
    val signed: Map<Long, List<SignSegment>>,
    /** What is on the stage now, set only after it is logged; [pending] is the officer's draft (D-26). */
    val onStage: Pending? = null,
    /** The logged row of [onStage]; a second screen's answers name it. */
    val itemId: Long? = null,
    /** The open sign window, if any: the camera is analysed only inside one (D-25). */
    val signWindow: SignWindow? = null,
    /** Alternatives the subject is choosing between for a signed answer; only their tap records a meaning. */
    val confirm: SignConfirm? = null,
    /** Questions still to come in a question run, and how many it started with; 0 when no run. */
    val runLeft: Int = 0,
    val runTotal: Int = 0,
)

private data class Local(
    val selectedGroup: String = "safety",
    val sensitiveUnlocked: Boolean = false,
    val pending: Pending? = null,
    val mode: Mode = Mode.OFFICER,
    val rotated: Boolean = false,
    val answeredCard: String? = null,
    val answeredYes: Boolean? = null,
    val chosenLanguage: String? = null,
    val reading: List<SignSegment>? = null,
    val signed: Map<Long, List<SignSegment>> = emptyMap(),
    val onStage: Pending? = null,
    /** The logged row on the stage, which every answer names. */
    val itemId: Long? = null,
    val signWindow: SignWindow? = null,
    val confirm: SignConfirm? = null,
    val queue: List<Pending.Ask> = emptyList(),
    val runTotal: Int = 0,
)

class SessionViewModel(
    private val sessionId: String,
    private val repo: SessionRepository,
    val content: ContentBundle,
    private val officerLang: String,
    private val report: SessionReport,
    reader: SignReader? = null,
) : ViewModel() {

    /** Set once the reader is built off the main thread; reads before then get no machine guesses. */
    @Volatile var reader: SignReader? = reader

    private val local = MutableStateFlow(Local())
    @Volatile private var capture: SignCapture? = null
    // Cards that answer the item shown when the window opened: the reader's guesses that match them come first.
    @Volatile private var context: Set<String> = emptySet()
    private val reads = mutableListOf<Job>()
    // One read at a time: a model need not be safe to call from several threads.
    private val readDispatcher = Dispatchers.Default.limitedParallelism(1)

    val auto = AutoPilot(
        sessionId, repo, content, officerLang, viewModelScope,
        showing = { local.value.mode == Mode.SHOW },
        present = { q, lang -> present(Pending.Ask(q), lang) },
    )

    val ui: StateFlow<SessionUi?> = combine(repo.session(sessionId).filterNotNull(), repo.events(sessionId), local) { s, events, l ->
        SessionUi(s, l.selectedGroup, l.sensitiveUnlocked, l.pending, l.mode, l.rotated, l.answeredCard, l.answeredYes, l.chosenLanguage, events, l.reading, l.signed, l.onStage, l.itemId, l.signWindow, l.confirm, l.queue.size, l.runTotal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A second screen shows the stage; this phone stays with the officer (D-26). */
    @Volatile var paired = false
    private val shown = mutableMapOf<Long, Pending>()
    private val remoteLogged = mutableSetOf<Pair<Long, Int>>()
    private var remoteFrom: Any? = null
    internal var reencodePng: (ByteArray) -> ByteArray? = ::reencodeRemotePng

    init {
        viewModelScope.launch {
            val session = repo.session(sessionId).first() ?: return@launch
            // Starting a session is the officer's confirmation of its opening: the stage greets the person first,
            // or, for someone who speaks another language, lets them find it (FR-63).
            if (repo.events(sessionId).first().all { it.type == EventType.SYSTEM && it.text in StartRows }) {
                val opening = if (session.profile == Profile.OTHER_LANGUAGE) Pending.LanguageFinder
                else Pending.GroundRules().takeIf { content.question(RuleSteps.first().key) != null }
                opening?.let { present(it, session.subjectLang) }
            }
        }
    }

    fun selectGroup(id: String) = local.update { it.copy(selectedGroup = id) }
    fun unlockSensitive() = local.update { it.copy(sensitiveUnlocked = true) }
    fun prepare(pending: Pending?) = local.update { it.copy(pending = pending) }
    fun toggleRotation() = local.update { it.copy(rotated = !it.rotated) }

    fun setJuvenile(on: Boolean) = viewModelScope.launch {
        local.update { it.copy(selectedGroup = Local().selectedGroup) }
        repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = if (on) SYSTEM_JUVENILE else SYSTEM_JUVENILE_OFF)
    }

    fun setProfile(profile: Profile) = viewModelScope.launch { repo.setProfile(sessionId, profile) }
    fun setSubjectLang(lang: String) = viewModelScope.launch { repo.setSubjectLang(sessionId, lang) }
    fun setSignLanguage(lang: SignLanguage) = viewModelScope.launch {
        if (ui.value?.signLanguage != lang) repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_SIGN_LANGUAGE + lang.name)
    }

    // The open group may not exist for the new role (sensitive is victim-only), so fall back to the shared first one.
    fun setRole(role: Role) = viewModelScope.launch {
        local.update { it.copy(selectedGroup = Local().selectedGroup) }
        // The change sheet shows the Juvenile Justice notice with the accused role.
        if (role == Role.ACCUSED && ui.value?.session?.role != Role.ACCUSED) repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_JJ_NOTICE)
        repo.setRole(sessionId, role)
        auto.onRoleChanged()
    }

    /** Officer confirms: the item is logged, then the subject panel takes the screen. */
    fun show() {
        val state = ui.value ?: return
        val pending = local.value.pending ?: return
        ShowTiming.start()
        viewModelScope.launch {
            // Only a paired officer can show while something is on stage; replacing it is the officer's act.
            if (local.value.mode == Mode.SHOW) finishShown(EventSource.OFFICER)
            auto.onManualShow(pending)
            present(pending, state.session.subjectLang)
        }
    }

    /** Shows what is prepared, if anything, and reads the subject's signed answer, or their account for NARRATIVE (D-25). */
    fun readSigns(window: SignWindow = SignWindow.ANSWER) {
        val state = ui.value ?: return
        val pending = local.value.pending
        ShowTiming.start()
        viewModelScope.launch {
            if (pending != null) {
                auto.onManualShow(pending)
                present(pending, state.session.subjectLang)
            } else {
                repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = if (window == SignWindow.NARRATIVE) SYSTEM_TELL_ME else SYSTEM_READ_SIGNS)
                local.update { it.copy(mode = Mode.SHOW, answeredCard = null, answeredYes = null, chosenLanguage = null) }
            }
            startReading(window)
        }
    }

    fun startReading(window: SignWindow = SignWindow.ANSWER) {
        if (capture != null) return
        context = local.value.pending.answerCards()
        capture = SignCapture(window)
        local.update { it.copy(reading = emptyList(), signWindow = window, confirm = null) }
    }

    /** Every tracked camera frame, on the analysis thread. */
    fun onSignFrame(frame: LandmarkFrame) {
        val c = capture ?: return
        val reachedCap = synchronized(c) {
            val wasFull = c.full
            c.add(frame).forEach(::readSegment)
            !wasFull && c.full
        }
        if (reachedCap) viewModelScope.launch { finishReading() }
    }

    private fun readSegment(frames: List<LandmarkFrame>) {
        val cards = context
        val segment = SignSegment(frames, context = cards)
        local.update { it.copy(reading = it.reading?.plus(segment)) }
        reads += viewModelScope.launch(readDispatcher) {
            val all = reader?.let { r -> runCatching { r.read(frames) }.getOrNull() }.orEmpty().sortedByDescending { it.score }
            val guesses = inContext(all, cards, reader?.words.orEmpty(), content.words).take(3)
            val read = SignSegment(frames, guesses, needsSlower(frames, all), cards)
            local.update { l -> l.copy(reading = l.reading?.map { if (it === segment) read else it }) }
        }
    }

    /** The subject is done signing: the words read are logged as the app's reading, never as confirmed (FR-66). */
    fun finishReading() {
        val c = capture ?: return
        capture = null
        val pending = synchronized(c) {
            c.finish().forEach(::readSegment)
            reads.toList().also { reads.clear() }
        }
        viewModelScope.launch {
            pending.joinAll()
            val segments = local.value.reading.orEmpty()
            val narrative = local.value.signWindow == SignWindow.NARRATIVE
            local.update { it.copy(reading = null, signWindow = null) }
            if (segments.isEmpty()) return@launch
            logSigned(segments, segments.map { it.word ?: "?" }, EventSource.MACHINE, "en")
            val signWords = reader?.words.orEmpty()
            val gloss = segments.flatMap { s -> glossOf(s.guesses?.firstOrNull(), signWords) }
            val draft = glossToText(gloss, content.words, ui.value?.signLanguage ?: SignLanguage.ISL).takeIf { gloss.any { it != "?" } }?.let { "${it.en} / ${it.hi}" }
            val note = listOfNotNull(glossTranscript(segments).takeIf { narrative }, draft).joinToString("\n")
            if (note.isNotEmpty()) repo.log(sessionId, EventType.NOTE, EventSource.MACHINE, "en", text = note)
        }
    }

    /** The officer's reading of a signed answer, logged beside the app's so the record shows both. */
    fun acceptSigned(eventId: Long, words: List<String>) {
        val segments = local.value.signed[eventId] ?: return
        val shownId = ui.value?.events?.firstOrNull { it.id == eventId }?.shownId
        viewModelScope.launch { logSigned(segments, words, EventSource.OFFICER, officerLang, shownId) }
    }

    private suspend fun logSigned(segments: List<SignSegment>, words: List<String>, source: EventSource, lang: String, shownId: Long? = local.value.itemId) {
        val id = repo.log(sessionId, EventType.ANSWER_SIGNED, source, lang, text = words.joinToString(" "), shownId = shownId)
        local.update { it.copy(signed = it.signed + (id to segments)) }
    }

    /**
     * The subject chooses what each sign meant, from the officer's reading and the reader's guesses (FR-66). Each set of
     * choices is logged before it is shown.
     */
    fun askSubject(eventId: Long, words: List<String>) {
        val asked = choiceQueue(eventId, words)
        if (asked.isEmpty()) return
        val queue = asked.map { it.first }
        viewModelScope.launch {
            logChoices(queue.first())
            local.update { it.copy(pending = null, mode = Mode.SHOW, answeredCard = null, answeredYes = null, confirm = SignConfirm(eventId, queue, asked.map { it.second })) }
        }
    }

    /** False when no sign has two meanings to choose between, so asking would show nothing. */
    fun canAskSubject(eventId: Long, words: List<String>) = choiceQueue(eventId, words).isNotEmpty()

    // Shuffled so the officer's reading does not always take the first, most tapped place.
    private fun choiceQueue(eventId: Long, words: List<String>): List<Pair<List<SignChoice>, String?>> =
        local.value.signed[eventId].orEmpty()
            .mapIndexed { i, s -> signChoices(words.getOrNull(i), s, reader?.words.orEmpty(), content.words).shuffled(random) to s.guesses?.firstOrNull()?.word }
            .filter { it.first.isNotEmpty() }

    internal var random: Random = Random.Default

    /** The subject's tap: a choice, or null for "none of these". Only this records a signed meaning as theirs. */
    fun chooseSign(choice: SignChoice?) {
        val confirm = local.value.confirm ?: return
        val shown = confirm.queue.first()
        val rest = confirm.queue.drop(1)
        local.update { it.copy(confirm = null) }
        viewModelScope.launch {
            if (choice != null) {
                repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = label(choice), cardId = choice.card)
            } else {
                repo.log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, officerLang, text = shown.joinToString(" / ") { label(it) })
            }
            if (rest.isNotEmpty()) {
                logChoices(rest.first())
                local.update { it.copy(confirm = SignConfirm(confirm.eventId, rest, confirm.read.drop(1))) }
            }
        }
    }

    private fun label(c: SignChoice) = c.card?.let { content.cards[it]?.label?.pick(officerLang) } ?: c.text

    private suspend fun logChoices(choices: List<SignChoice>) {
        val prompt = content.subjectText("is_this_it", officerLang)
        val lang = ui.value?.session?.subjectLang
        repo.log(sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang, text = "$prompt ${choices.joinToString(" / ") { label(it) }}")
        // The order shown, with "none of these" last, so the report gives each pick its place (choicePositions).
        val shown = PromptRecord(lang?.let { content.subjectText("is_this_it", it) }, listOf("ask"), choices.map { it.card ?: it.text } + SOMETHING_ELSE, content.version)
        repo.log(sessionId, EventType.PROMPT, EventSource.OFFICER, lang, text = shown.encode())
    }

    /** Landmarks are biometric: they go when the session ends, the app locks or this model is cleared (D-25). */
    fun wipeSigns() {
        capture = null
        reads.forEach { it.cancel() }
        reads.clear()
        local.update { it.copy(reading = null, signed = emptyMap(), signWindow = null, confirm = null) }
    }

    override fun onCleared() = wipeSigns()

    private suspend fun present(prepared: Pending, lang: String) {
        flushGiven()
        val pending = when (prepared) {
            // A lesson-sheet input offers every picture it lists, so the subject can pick several.
            is Pending.Ask if prepared.question.answer != null -> prepared
            is Pending.Ask -> prepared.copy(question = prepared.question.copy(cards = offered(prepared.question.cards, Random.Default)))
            is Pending.Cards -> prepared.copy(cardIds = offered(prepared.cardIds, Random.Default))
            is Pending.ShowBack -> if (prepared.asksChoice) prepared.copy(choices = offered(prepared.choices, Random.Default)) else prepared
            is Pending.FreeText -> if (prepared.asksChoice) prepared.copy(cardIds = offered(prepared.cardIds, Random.Default)) else prepared
            else -> prepared
        }
        if (!paired) local.update { it.copy(pending = pending) }
        val id = when (pending) {
            is Pending.FreeText if pending.ref != null -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang,
                text = pending.text, cardId = pending.cardIds.firstOrNull(), lexiconId = REF_PREFIX + pending.ref,
            )
            is Pending.Ask -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.PRESET, lang,
                text = pending.question.text.pick(officerLang), lexiconId = pending.question.lexicon,
                itemId = pending.question.id, reason = shownReason(content, pending.question, lang),
            )
            // A machine-made caption is logged next to the officer's own words, so the record shows both.
            is Pending.FreeText -> repo.log(sessionId, EventType.QUESTION_SHOWN, EventSource.TEXT, lang, text = listOfNotNull(pending.text, pending.caption).joinToString("\n"))
            is Pending.Cards -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.BOARD, lang,
                text = pending.cardIds.joinToString { id -> content.cards[id]?.label?.pick(officerLang) ?: id },
            )
            is Pending.ShowBack -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.SHOW_BACK, lang,
                text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id,
            )
            is Pending.PhraseAsk -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.PHRASE, lang,
                text = pending.slot.text.pick(officerLang), cardId = pending.slot.card,
            )
            Pending.LanguageFinder -> repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_LANGUAGE_FINDER)
            Pending.Drawing -> repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_DRAWING)
            is Pending.GroundRules -> repo.log(
                sessionId, EventType.QUESTION_SHOWN, EventSource.PRESET, lang,
                text = ruleStep(pending)?.let { ruleText(content, it.key, officerLang) },
                // The introduction is a bank question; naming it lets auto mode see it was asked.
                itemId = ruleStep(pending)?.key?.takeIf { content.question(it) != null }, reason = SHOWN_OPENING,
            )
        }
        repo.log(sessionId, EventType.PROMPT, EventSource.OFFICER, lang, text = content.promptFor(pending, lang).encode())
        shown[id] = pending
        local.update {
            it.copy(
                mode = Mode.SHOW, answeredCard = null, answeredYes = null, chosenLanguage = null, onStage = pending, itemId = id,
                // Once sent to a second screen the draft is spent; the officer prepares the next one.
                pending = if (paired && it.pending == prepared) null else it.pending,
            )
        }
    }

    /**
     * The single entry for answers from a second screen; returns after the row is committed so the link can ack.
     * Nothing is dropped: an answer to an earlier item is logged against that item and marked late.
     */
    suspend fun logRemote(itemId: Long, n: Int, body: AnswerBody, late: Boolean = false, from: Any? = null): Boolean {
        // A new link numbers its answers from 0 again, so only a repeat from the same link is a resend.
        if (from !== remoteFrom) { remoteLogged.clear(); remoteFrom = from }
        if (itemId to n in remoteLogged) return false
        val current = itemId == local.value.itemId
        val via = if (late || !current) VIA_STAGE_LATE else VIA_STAGE
        val q = itemId.takeIf { it in shown }
        val lang = officerLang
        when (body) {
            is AnswerBody.Card -> {
                if (!logChecked(shown[itemId], body.id, null, q, via)) {
                    repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, lang, text = content.cards[body.id]?.label?.pick(lang), cardId = body.id, shownId = q, via = via)
                }
                if (current) local.update { it.copy(answeredCard = body.id, answeredYes = null) }
            }
            is AnswerBody.YesNo -> {
                if (!logChecked(shown[itemId], null, body.yes, q, via)) {
                    val id = if (body.yes) "yes" else "no"
                    repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, lang, text = content.cards[id]?.label?.pick(lang), cardId = id, shownId = q, via = via)
                }
                if (current) local.update { it.copy(answeredYes = body.yes, answeredCard = null) }
            }
            is AnswerBody.Language -> {
                repo.log(sessionId, EventType.LANGUAGE_CHOSEN, EventSource.SUBJECT, body.code, text = content.language(body.code)?.english, shownId = q, via = via)
                if (current) local.update { it.copy(chosenLanguage = body.code) }
            }
            AnswerBody.Next -> if (current) nextRule()
            is AnswerBody.Given -> {
                val method = Method.entries.firstOrNull { it.name == body.method }
                if (method != null) {
                    val g = Given(body.question, method, body.confirmed, body.value.take(TEXT_MAX), body.picks)
                    if (!current || !hold(g, via)) logGiven(g, q, via)
                    if (current && g.confirmed) advanceRun()
                }
            }
            AnswerBody.Change -> if (current) hold(null, via)
            is AnswerBody.Text -> repo.log(sessionId, EventType.ANSWER_TEXT, EventSource.SUBJECT, ui.value?.session?.subjectLang, text = body.text.trim(), shownId = q, via = via)
            is AnswerBody.Drawing -> {
                // Only a bitmap this phone re-encoded itself reaches the database and the report.
                val png = runCatching { java.util.Base64.getDecoder().decode(body.png) }.getOrNull()?.let(reencodePng)
                if (png != null) {
                    repo.log(sessionId, EventType.ANSWER_DRAWING, EventSource.SUBJECT, drawingPng = png, shownId = q, via = via)
                } else {
                    repo.log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, shownId = q, via = via)
                }
                if (current) local.update { it.copy(onStage = null, itemId = null) }
            }
        }
        remoteLogged += itemId to n
        return true
    }

    /** Lifecycle of the second screen, in the encrypted log; [key] is a machine key shown in the officer's language. */
    fun logLink(key: String, detail: String? = null) = viewModelScope.launch {
        repo.log(sessionId, EventType.LINK, EventSource.OFFICER, text = listOfNotNull(key, detail).joinToString(" "))
    }

    fun breakGiven() = viewModelScope.launch { repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_BREAK_GIVEN) }

    /** The link failed: what was on the second screen goes to this phone's stage; the draft is dropped. */
    fun fallbackToLocal() {
        paired = false
        local.update { it.copy(pending = it.onStage) }
        viewModelScope.launch { repo.log(sessionId, EventType.LINK, EventSource.OFFICER, text = LINK_FALLBACK) }
    }

    /** The second screen went away without a fallback: this phone stays the officer's, never a stage with their draft. */
    fun leavePaired() {
        paired = false
        local.update { it.copy(mode = Mode.OFFICER, onStage = null, itemId = null) }
    }

    fun answerCard(cardId: String) {
        local.update { it.copy(answeredCard = cardId, answeredYes = null) }
        val item = local.value.pending
        viewModelScope.launch {
            if (!logChecked(item, cardId, null, local.value.itemId)) {
                repo.log(
                    sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang,
                    text = content.cards[cardId]?.label?.pick(officerLang), cardId = cardId, shownId = local.value.itemId,
                )
            }
            when (cardId) {
                "stop" -> endRun()
                // The pictures did not hold the answer: the subject gets the drawing pad instead of a forced choice.
                SOMETHING_ELSE -> { endRun(); ui.value?.let { present(Pending.Drawing, it.session.subjectLang) } }
                else -> advanceRun()
            }
        }
    }

    fun answerYesNo(yes: Boolean) {
        val pending = local.value.pending
        local.update { it.copy(answeredYes = yes, answeredCard = null) }
        viewModelScope.launch {
            if (!logChecked(pending, null, yes, local.value.itemId)) {
                val id = if (yes) "yes" else "no"
                repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards[id]?.label?.pick(officerLang), cardId = id, shownId = local.value.itemId)
            }
            if (advancesRun(pending, yes)) advanceRun()
        }
    }

    /**
     * Answers to a show-back or say-back. A meaning becomes the subject's only when they pick it among two or more
     * pictures; a Yes to a single reading is logged as their Yes, and the reading stays the officer's (FR-66).
     * Returns false for an answer that is not about the reading, such as Stop, which is logged as usual.
     */
    private suspend fun logChecked(item: Pending?, card: String?, yes: Boolean?, shownId: Long?, via: String? = null): Boolean {
        val meant: String?
        val text: String?
        val lexiconId: String?
        when (item) {
            is Pending.ShowBack -> { meant = item.entry.card; text = item.entry.label.pick(officerLang); lexiconId = item.entry.id }
            is Pending.FreeText if item.ref != null -> { meant = item.meant; text = item.text; lexiconId = REF_PREFIX + item.ref }
            else -> return false
        }
        val choices = if (item is Pending.ShowBack) item.choices else (item as Pending.FreeText).cardIds
        val picked = item.asksChoice && card != null && card in choices
        when {
            yes == true -> repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards["yes"]?.label?.pick(officerLang), cardId = "yes", lexiconId = lexiconId, shownId = shownId, via = via)
            yes == false || (picked && card == SOMETHING_ELSE) ->
                repo.log(sessionId, EventType.ANSWER_UNCLEAR, EventSource.SUBJECT, officerLang, text = text, cardId = "no", lexiconId = lexiconId, shownId = shownId, via = via)
            picked && card == meant -> repo.log(sessionId, EventType.ANSWER_GESTURE, EventSource.SUBJECT, officerLang, text = text, cardId = card, lexiconId = lexiconId, shownId = shownId, via = via)
            picked -> repo.log(sessionId, EventType.ANSWER_CARD, EventSource.SUBJECT, officerLang, text = content.cards[card]?.label?.pick(officerLang), cardId = card, lexiconId = lexiconId, shownId = shownId, via = via)
            else -> return false
        }
        return true
    }

    /** The subject moves the opening on; after the practice round the stage waits for the officer. */
    fun nextRule() {
        val step = ((if (paired) local.value.onStage else local.value.pending) as? Pending.GroundRules)?.step ?: return
        val lang = ui.value?.session?.subjectLang ?: return
        viewModelScope.launch {
            if (step < RuleSteps.lastIndex) present(Pending.GroundRules(step + 1), lang)
            else if (paired) local.update { it.copy(onStage = null, itemId = null) }
            else local.update { it.copy(pending = null, answeredCard = null, answeredYes = null) }
        }
    }

    /** The subject ended their own pause; its length is logged from their Stop or Break. */
    fun endPause() {
        val seconds = pauseSeconds(ui.value?.events.orEmpty(), System.currentTimeMillis())
        local.update { it.copy(answeredCard = null) }
        if (seconds != null) viewModelScope.launch { repo.log(sessionId, EventType.SYSTEM, EventSource.SUBJECT, text = "$SYSTEM_PAUSE_END$seconds") }
    }

    fun chooseLanguage(code: String) {
        local.update { it.copy(chosenLanguage = code) }
        viewModelScope.launch {
            repo.log(sessionId, EventType.LANGUAGE_CHOSEN, EventSource.SUBJECT, code, text = content.language(code)?.english, shownId = local.value.itemId)
        }
    }

    /** The subject wrote their own answer on the stage; it is logged as typed, never interpreted. */
    fun answerText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.log(sessionId, EventType.ANSWER_TEXT, EventSource.SUBJECT, ui.value?.session?.subjectLang, text = text.trim(), shownId = local.value.itemId)
            advanceRun()
        }
    }

    // An answer the subject entered but has not yet confirmed on its read-back.
    private var unconfirmed: Given? = null
    private var unconfirmedVia: String? = null

    /** A lesson-sheet answer: a confirmed one is logged now; an unconfirmed one is logged as such if the item closes first. */
    fun give(g: Given?) {
        if (hold(g, null)) return
        viewModelScope.launch { logGiven(g!!); advanceRun() }
    }

    /** Keeps [g] for the item's close unless it is confirmed; true when there is nothing to log now. */
    private fun hold(g: Given?, via: String?): Boolean {
        val held = g == null || !g.confirmed
        unconfirmed = g.takeIf { held }
        unconfirmedVia = via
        return held
    }

    private suspend fun flushGiven() {
        unconfirmed?.let { logGiven(it, via = unconfirmedVia) }
        unconfirmed = null
    }

    private suspend fun logGiven(g: Given, shownId: Long? = local.value.itemId, via: String? = null) {
        val text = content.answerText(content.question(g.question)?.answer, g, officerLang)
        repo.log(sessionId, EventType.ANSWER_TEXT, EventSource.SUBJECT, officerLang, text = text, itemId = g.question, reason = g.reason(), shownId = shownId, via = via)
    }

    fun submitDrawing(png: ByteArray) {
        viewModelScope.launch {
            repo.log(sessionId, EventType.ANSWER_DRAWING, EventSource.SUBJECT, drawingPng = png, shownId = local.value.itemId)
            // The subject still holds the phone; only the officer handle, with re-authentication, leaves Show (FR-61).
            local.update { it.copy(pending = null) }
        }
    }

    /** Called after the officer re-authenticates (FR-61). */
    fun returnToOfficer() {
        finishReading()
        local.update { it.copy(confirm = null) }
        viewModelScope.launch { finishShown(if (paired) EventSource.OFFICER else EventSource.SUBJECT) }
    }

    private suspend fun finishShown(source: EventSource) {
        flushGiven()
        val l = local.value
        val pending = if (paired) l.onStage else l.pending
        if (pending is Pending.ShowBack && l.answeredYes == null && l.answeredCard == null) {
            repo.log(sessionId, EventType.ANSWER_UNCLEAR, source, officerLang, text = pending.entry.label.pick(officerLang), cardId = pending.entry.card, lexiconId = pending.entry.id, shownId = l.itemId)
        }
        if (pending is Pending.GroundRules && !isPractice(pending)) repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = SYSTEM_RULES_SKIPPED)
        if (pending is Pending.FreeText && pending.ref != null && l.answeredYes == null && l.answeredCard == null) {
            repo.log(sessionId, EventType.ANSWER_UNCLEAR, source, officerLang, text = pending.text, lexiconId = REF_PREFIX + pending.ref, shownId = l.itemId)
        }
        endRun()
        l.chosenLanguage?.let { repo.setSubjectLang(sessionId, it) }
        local.update { it.copy(mode = Mode.OFFICER, pending = if (paired) it.pending else null, onStage = null, itemId = null) }
        auto.onReturn()
    }

    // Question runs and say-back (main page).

    private var step: Job? = null

    /** Up to five prepared questions on one handover; sensitive questions are always asked one at a time. */
    fun showRun(items: List<Pending.Ask>) {
        val state = ui.value ?: return
        val sensitive = content.groups.filter { it.sensitive }.map { it.id }.toSet()
        val run = items.filterNot { it.question.group in sensitive }.take(RUN_MAX)
        val first = run.firstOrNull() ?: return
        local.update { it.copy(queue = run.drop(1), runTotal = run.size) }
        ShowTiming.start()
        viewModelScope.launch { present(first, state.session.subjectLang) }
    }

    // The pause lets the stage acknowledge the answer and lets the subject change it before the next question.
    private fun advanceRun() {
        if (local.value.queue.isEmpty()) return
        step?.cancel()
        step = viewModelScope.launch {
            delay(RUN_STEP_MS)
            val next = local.value.queue.firstOrNull() ?: return@launch
            val lang = ui.value?.session?.subjectLang ?: return@launch
            local.update { it.copy(queue = it.queue.drop(1)) }
            present(next, lang)
        }
    }

    private suspend fun endRun() {
        step?.cancel()
        val l = local.value
        if (l.queue.isNotEmpty()) {
            repo.log(sessionId, EventType.SYSTEM, EventSource.OFFICER, text = "$SYSTEM_RUN_ENDED${l.runTotal - l.queue.size}/${l.runTotal}")
        }
        local.update { it.copy(queue = emptyList(), runTotal = 0) }
    }

    /**
     * Shows the officer's reading of an answer line back to the subject among the item's other pictures, so they pick
     * the meaning rather than agree to one. With no other picture it is shown alone, and a Yes leaves it the officer's.
     */
    fun sayBack(e: SessionEvent, words: String, modes: Set<SignMode>) {
        val text = words.trim().takeIf { it.isNotEmpty() } ?: return
        val card = e.cardId?.takeIf { it !in setOf("yes", "no", "dont_know", "stop") }
            ?: Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(text).firstNotNullOfOrNull { content.words[it.value.lowercase()]?.card }
        val events = ui.value?.events.orEmpty()
        val item = events.firstOrNull { it.id == e.shownId }?.itemId?.let(content::question)?.cards.orEmpty()
        val choices = checkChoices(card, item.filter { it in content.cards })
        val ask = choices.size >= 2
        val caption = if (ask) ui.value?.session?.subjectLang?.let { content.subjectText("is_this_it", it) } else null
        local.update { it.copy(pending = Pending.FreeText(text, choices, modes, caption, ref = e.id, meant = card.takeIf { ask })) }
        show()
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.log(sessionId, EventType.NOTE, EventSource.OFFICER, officerLang, text = text.trim()) }
    }

    suspend fun drawing(id: Long): ByteArray? = repo.drawing(id)

    /** Builds the encrypted report from everything logged so far; returns the file and its one-time passphrase. */
    suspend fun exportReport(text: ReportText, detail: (SessionEvent) -> String, section: ReportSection? = null): Pair<File, String> {
        val events = ui.value?.events ?: emptyList()
        val grades = grades(events)
        val rows = events.withQuestions().map { (e, q) -> ReportRow(e, q, detail(e), reading = grades[e.id]?.isReading == true) }
        return report.export(rows, text, { repo.drawing(it) }, section)
    }

    fun endSession(onDone: () -> Unit) {
        wipeSigns()
        viewModelScope.launch {
            repo.purge(sessionId)
            onDone()
        }
    }
}
