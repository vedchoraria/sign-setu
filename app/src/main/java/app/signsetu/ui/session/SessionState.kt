package app.signsetu.ui.session

import androidx.compose.runtime.Immutable
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.PhraseSlot
import app.signsetu.data.content.Question
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.sign.SignConfirm
import app.signsetu.sign.SignSegment
import app.signsetu.sign.SignWindow

/** What the officer has prepared but not yet shown. */
@Immutable
sealed interface Pending {
    data class Ask(val question: Question) : Pending
    /** [caption] is the assistant's translation the officer accepted; the character still signs [text]. */
    data class FreeText(
        val text: String,
        val cardIds: List<String>,
        val modes: Set<SignMode> = emptySet(),
        val caption: String? = null,
        /** Set when the officer says an answer line back to the subject to confirm its meaning. */
        val ref: Long? = null,
        /** The picture of the officer's reading in a say-back; the subject picks among [cardIds]. */
        val meant: String? = null,
        /** Written by the AI counsellor while the officer watched (Counsellor.kt). */
        val byAi: Boolean = false,
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
    /** Landmark frames of each signed answer, by event id: memory only, never stored or exported. */
    val signed: Map<Long, List<SignSegment>>,
    /** What is on the stage now, set only after it is logged; [pending] is the officer's draft. */
    val onStage: Pending? = null,
    /** The logged row of [onStage]; a second screen's answers name it. */
    val itemId: Long? = null,
    /** The open sign window, if any: the camera is analysed only inside one. */
    val signWindow: SignWindow? = null,
    /** Alternatives the subject is choosing between for a signed answer; only their tap records a meaning. */
    val confirm: SignConfirm? = null,
    /** How many questions the current question run started with; 0 when no run. */
    val runTotal: Int = 0,
    /** The officer's choice of the child character; null follows the profile. */
    val childAvatar: Boolean? = null,
)

/** A young child gets the child character unless the officer switched it; anyone else, the adult. */
val SessionUi.child: Boolean get() = childAvatar ?: (session.profile == Profile.YOUNG_CHILD)
