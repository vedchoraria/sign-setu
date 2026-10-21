package app.signsetu.data.session

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class Profile { YOUNG_CHILD, ISL_USER, OTHER_LANGUAGE, NOT_SURE }

/**
 * Victim or witness, or accused or in custody (suspect, arrested or convicted): sets the question set and safeguards.
 * NOT_KNOWN until the officer sets it, and then it is left out of the export.
 */
enum class Role { NOT_KNOWN, VICTIM, ACCUSED }

enum class EventType {
    QUESTION_SHOWN, ANSWER_CARD, ANSWER_DRAWING, ANSWER_GESTURE, ANSWER_UNCLEAR, LANGUAGE_CHOSEN, NOTE, SYSTEM, ANSWER_TEXT, ANSWER_SIGNED,
    SUGGESTED, APPROVED, SKIPPED, AUTO_ADVANCED, LINK, PROMPT,
}

enum class EventSource { PRESET, TEXT, BOARD, SHOW_BACK, SUBJECT, OFFICER, PHRASE, RULE, MACHINE }

@Immutable
data class Session(
    val id: String,
    val startedAt: Long,
    val profile: Profile,
    val subjectLang: String,
    val role: Role = Role.NOT_KNOWN,
    val endedAt: Long? = null,
    /** The officer's name for a saved session. */
    val title: String? = null,
)

@Immutable
data class SessionEvent(
    val id: Long,
    val ts: Long,
    val type: EventType,
    val source: EventSource,
    val lang: String?,
    val text: String?,
    val cardId: String?,
    val lexiconId: String?,
    val drawingId: Long?,
    /** Bank item id of a shown or suggested item. */
    val itemId: String? = null,
    val reason: String? = null,
    /** For an answer, the QUESTION_SHOWN it answers. */
    val shownId: Long? = null,
    /** "stage" or "stage_late" for answers from the second screen; null on this phone. */
    val via: String? = null,
)

const val TITLE_MAX = 80

class SessionRepository(
    private val dao: SessionDao,
    private val onSessionStarted: suspend (Long) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Set when the app locks: the database closes under screens that may still be writing, and those writes are dropped. */
    @Volatile var closed = false

    private suspend fun <T> unlessClosed(none: T, block: suspend () -> T): T {
        if (closed) return none
        return try {
            block()
        } catch (e: IllegalStateException) {
            if (closed) none else throw e
        } catch (e: android.database.SQLException) {
            if (closed) none else throw e
        }
    }

    suspend fun openSession(): Session? = dao.openSession()?.toModel()

    fun session(id: String): Flow<Session?> = dao.session(id).map { it?.toModel() }

    suspend fun start(profile: Profile, subjectLang: String, role: Role = Role.NOT_KNOWN): String {
        val id = UUID.randomUUID().toString()
        val now = clock()
        dao.insertSession(SessionEntity(id, now, null, profile.name, subjectLang, role.name))
        onSessionStarted(now)
        return id
    }

    suspend fun setSubjectLang(id: String, lang: String) = unlessClosed(Unit) { dao.setSubjectLang(id, lang) }
    suspend fun setProfile(id: String, profile: Profile) = unlessClosed(Unit) { dao.setProfile(id, profile.name) }
    suspend fun setRole(id: String, role: Role) = unlessClosed(Unit) { dao.setRole(id, role.name) }

    fun events(sessionId: String): Flow<List<SessionEvent>> = dao.events(sessionId).map { list -> list.map { it.toModel() } }

    /** A fresh read, never a cached snapshot: decisions on what to ask next depend on it. */
    suspend fun eventsNow(sessionId: String): List<SessionEvent> = events(sessionId).first()

    /** Persisted before the interface reflects it; returns the event id. */
    suspend fun log(
        sessionId: String,
        type: EventType,
        source: EventSource,
        lang: String? = null,
        text: String? = null,
        cardId: String? = null,
        lexiconId: String? = null,
        drawingPng: ByteArray? = null,
        itemId: String? = null,
        reason: String? = null,
        shownId: Long? = null,
        via: String? = null,
    ): Long = unlessClosed(-1L) {
        val drawingId = drawingPng?.let { dao.insertDrawing(DrawingEntity(sessionId = sessionId, png = it)) }
        dao.insertEvent(EventEntity(0, sessionId, clock(), type.name, source.name, lang, text, cardId, lexiconId, drawingId, itemId, reason, shownId, via))
    }

    suspend fun drawing(id: Long): ByteArray? = dao.drawing(id)

    /** Ending a session purges it; there is no archive. */
    suspend fun purge(sessionId: String) = dao.deleteSession(sessionId)

    /** Zero hours keeps every session until the officer deletes it. */
    suspend fun purgeExpired(retentionHours: Int): Int = if (retentionHours <= 0) 0 else dao.deleteStartedBefore(clock() - retentionHours * 3_600_000L)

    /** Finished sessions, newest first, kept under the officer's name for them. */
    fun saved(): Flow<List<Session>> = dao.saved().map { list -> list.map { it.toModel() } }

    suspend fun end(id: String, title: String) = unlessClosed(Unit) { dao.endSession(id, clock(), title.trim().take(TITLE_MAX)) }

    suspend fun reopen(id: String) = unlessClosed(Unit) { dao.reopen(id) }

    private inline fun <reified T : Enum<T>> safeValueOf(value: String, default: T): T =
        try { enumValueOf<T>(value) } catch (_: IllegalArgumentException) { default }

    private fun SessionEntity.toModel() = Session(
        id = id,
        startedAt = startedAt,
        profile = safeValueOf(profile, Profile.NOT_SURE),
        subjectLang = subjectLang,
        role = safeValueOf(role, Role.NOT_KNOWN),
        endedAt = endedAt,
        title = title,
    )

    private fun EventEntity.toModel() = SessionEvent(
        id = id,
        ts = ts,
        type = safeValueOf(type, EventType.SYSTEM),
        source = safeValueOf(source, EventSource.MACHINE),
        lang = lang,
        text = text,
        cardId = cardId,
        lexiconId = lexiconId,
        drawingId = drawingId,
        itemId = itemId,
        reason = reason,
        shownId = shownId,
        via = via,
    )
}
