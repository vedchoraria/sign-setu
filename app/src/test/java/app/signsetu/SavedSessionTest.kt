package app.signsetu

import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedSessionTest {
    @Test
    fun aFinishedSessionIsKeptUnderItsNameAndRetentionZeroPurgesNothing() = runBlocking {
        var now = 1_000L
        val dao = MemoryDao()
        val repo = SessionRepository(dao, clock = { now })
        val id = repo.start(Profile.ISL_USER, "hi")
        now = 5_000L
        repo.end(id, "  FIR 214/2026 Raipur  ")
        assertEquals(listOf("FIR 214/2026 Raipur"), repo.saved().first().map { it.title })
        assertEquals(5_000L, repo.saved().first().single().endedAt)
        now = 100L * 24 * 3_600_000L
        assertEquals(0, repo.purgeExpired(0))
        repo.reopen(id)
        assertEquals(emptyList<String>(), repo.saved().first().map { it.title })
    }

    @Test
    fun sessionsPastTheRetentionPeriodAreDeletedSavedOnesIncluded() = runBlocking {
        val hour = 3_600_000L
        var now = 0L
        val dao = MemoryDao()
        val repo = SessionRepository(dao, clock = { now })
        val old = repo.start(Profile.ISL_USER, "hi")
        repo.end(old, "FIR 1")
        now = 30 * hour
        val recent = repo.start(Profile.ISL_USER, "hi")
        repo.end(recent, "FIR 2")
        now = 40 * hour
        assertEquals(1, repo.purgeExpired(24))
        assertEquals(listOf("FIR 2"), repo.saved().first().map { it.title })
        assertEquals(setOf(recent), dao.sessions.value.keys)
    }
}
