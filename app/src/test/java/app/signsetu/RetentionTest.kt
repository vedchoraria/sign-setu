package app.signsetu

import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionRepository
import app.signsetu.data.settings.AppSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Retention as it acts on stored sessions, not as a setting. */
class RetentionTest {
    private val hour = 3_600_000L
    private var now = 0L
    private val dao = MemoryDao()
    private val repo = SessionRepository(dao, clock = { now })

    private suspend fun session(startedHoursAgo: Long, finished: Boolean = false): String {
        now -= startedHoursAgo * hour
        val id = repo.start(Profile.ISL_USER, "hi")
        repo.log(id, EventType.ANSWER_TEXT, EventSource.SUBJECT, text = "private answer")
        if (finished) repo.end(id, "FIR 9")
        now += startedHoursAgo * hour
        return id
    }

    @Test
    fun theDefaultIsTwentyFourHoursAndKeepForeverIsAnExplicitChoice() {
        assertEquals(24, AppSettings().retentionHours)
        assertEquals(24, AppSettings.DEFAULT_RETENTION_HOURS)
        assertTrue(0 in AppSettings.RETENTION_CHOICES)
        assertTrue(AppSettings(retentionHours = 0).retentionHours == 0)
    }

    @Test
    fun anUnfinishedSessionPastTheDefaultIsDeletedWithItsAnswersAndCannotBeReopenedOrResumed() = runBlocking {
        now = 1_000 * hour
        val stale = session(startedHoursAgo = 25)
        val fresh = session(startedHoursAgo = 23)
        assertEquals(1, repo.purgeExpired(AppSettings.DEFAULT_RETENTION_HOURS))
        assertEquals(setOf(fresh), dao.sessions.value.keys)
        assertTrue("answers of the expired session are gone", dao.rows.value.none { it.sessionId == stale })
        repo.reopen(stale)
        assertNull("expired data is not restored by reopening", dao.sessions.value[stale])
        assertEquals("only the newer session is offered for resuming", fresh, repo.openSession()?.id)
    }

    @Test
    fun afterAPurgeNothingExpiredIsOfferedToResumeOrListedAsSaved() = runBlocking {
        now = 1_000 * hour
        session(startedHoursAgo = 30)
        session(startedHoursAgo = 26, finished = true)
        repo.purgeExpired(24)
        assertNull(repo.openSession())
        assertTrue(repo.saved().first().isEmpty())
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun keepUntilDeletedKeepsEveryAgeAndStillAllowsReopeningUntilTheOfficerDeletes() = runBlocking {
        now = 5_000 * hour
        val unfinished = session(startedHoursAgo = 24 * 400)
        val saved = session(startedHoursAgo = 24 * 300, finished = true)
        assertEquals(0, repo.purgeExpired(0))
        assertEquals(setOf(unfinished, saved), dao.sessions.value.keys)
        repo.reopen(saved)
        assertNotNull(repo.openSession())
        repo.purge(saved)
        repo.purge(unfinished)
        assertTrue("deleting by hand still deletes", dao.sessions.value.isEmpty() && dao.rows.value.isEmpty())
    }

    @Test
    fun theBackgroundJobDestroysEverythingOnlyWhenEvenTheNewestSessionIsExpiredAndNeverWhenKeepingForever() {
        assertTrue(allSessionsExpired(nowMs = 100 * hour, newestSessionAt = 70 * hour, retentionHours = 24))
        assertFalse(allSessionsExpired(nowMs = 100 * hour, newestSessionAt = 80 * hour, retentionHours = 24))
        assertFalse(allSessionsExpired(nowMs = 100_000 * hour, newestSessionAt = 1 * hour, retentionHours = 0))
    }

    @Test
    fun theStoredSchemaDeletesEventsAndDrawingsWithTheirSession() {
        val schema = Json.parseToJsonElement(File("schemas/app.signsetu.data.session.SessionDatabase/4.json").readText())
        val entities = schema.jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        val children = entities.filter { it["tableName"]!!.jsonPrimitive.content in setOf("event", "drawing") }
        assertEquals(2, children.size)
        children.forEach { table ->
            val keys = table["foreignKeys"]!!.jsonArray.map { it.jsonObject }
            assertTrue(keys.any { it["table"]!!.jsonPrimitive.content == "session" && it["onDelete"]!!.jsonPrimitive.content == "CASCADE" })
        }
    }
}
