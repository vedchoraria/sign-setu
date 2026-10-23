package app.signsetu

import app.signsetu.data.session.DrawingEntity
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.SessionDao
import app.signsetu.data.session.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The session database in memory; [rows] are every event logged, ids counted from 1 as Room does. */
class MemoryDao : SessionDao {
    val sessions = MutableStateFlow<Map<String, SessionEntity>>(emptyMap())
    val rows = MutableStateFlow<List<EventEntity>>(emptyList())
    /** Writes fail from here on, as they do once the app locks and the file closes. */
    var closed = false
    private var next = 1L

    private fun edit(id: String, change: (SessionEntity) -> SessionEntity) =
        sessions.update { all -> all[id]?.let { all + (id to change(it)) } ?: all }

    override suspend fun insertSession(session: SessionEntity) = sessions.update { it + (session.id to session) }
    override suspend fun openSession() = sessions.value.values.filter { it.endedAt == null }.maxByOrNull { it.startedAt }
    override fun session(id: String): Flow<SessionEntity?> = sessions.map { it[id] }
    override suspend fun setSubjectLang(id: String, lang: String) = edit(id) { it.copy(subjectLang = lang) }
    override suspend fun setProfile(id: String, profile: String) = edit(id) { it.copy(profile = profile) }
    override suspend fun setRole(id: String, role: String) = edit(id) { it.copy(role = role) }
    override suspend fun insertEvent(event: EventEntity): Long = synchronized(this) {
        check(!closed) { "database closed" }
        val id = next++
        rows.update { it + event.copy(id = id) }
        id
    }
    override fun events(sessionId: String): Flow<List<EventEntity>> = rows
    private val drawings = mutableMapOf<Long, ByteArray>()
    override suspend fun insertDrawing(drawing: DrawingEntity) = synchronized(this) { next++.also { drawings[it] = drawing.png } }
    override suspend fun drawing(id: Long): ByteArray? = synchronized(this) { drawings[id] }
    override suspend fun deleteSession(id: String) = sessions.update { it - id }.also { rows.update { all -> all.filter { e -> e.sessionId != id } } }
    override suspend fun endSession(id: String, endedAt: Long, title: String) = edit(id) { it.copy(endedAt = endedAt, title = title) }
    override suspend fun reopen(id: String) = edit(id) { it.copy(endedAt = null) }
    override fun saved(): Flow<List<SessionEntity>> = sessions.map { all -> all.values.filter { it.endedAt != null }.sortedByDescending { it.endedAt } }
    override suspend fun lastHash(sessionId: String): String? = rows.value.lastOrNull { it.sessionId == sessionId && it.hash != null }?.hash
    override suspend fun startedAt(id: String): Long? = sessions.value[id]?.startedAt
    override suspend fun deleteIdleBefore(cutoff: Long): Int {
        val old = sessions.value.filterValues { s ->
            (s.endedAt ?: rows.value.filter { it.sessionId == s.id }.maxOfOrNull { it.ts } ?: s.startedAt) < cutoff
        }.keys
        old.forEach { deleteSession(it) }
        return old.size
    }
}
