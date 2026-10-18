package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Group
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.session.DrawingEntity
import app.signsetu.data.session.EventEntity
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionDao
import app.signsetu.data.session.SessionEntity
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoleTest {

    private val groups: List<Group> = Json { ignoreUnknownKeys = true }.let { json ->
        val root = json.parseToJsonElement(File("src/main/assets/content/triage.json").readText()).jsonObject
        json.decodeFromJsonElement(ListSerializer(Group.serializer()), root.getValue("groups"))
    }

    private val bundle = ContentBundle(
        groups = groups, questions = emptyList(), categories = emptyList(), cards = emptyMap(),
        lexicon = emptyMap(), languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
    )

    private val questions: List<Question> = Json { ignoreUnknownKeys = true }.let { json ->
        val root = json.parseToJsonElement(File("src/main/assets/content/triage.json").readText()).jsonObject
        json.decodeFromJsonElement(ListSerializer(Question.serializer()), root.getValue("questions"))
    }

    @Test
    fun abuseQuestionsAreForVictimsAndRightsForTheAccused() {
        val victim = bundle.groupsFor(Role.VICTIM).map { it.id }
        val accused = bundle.groupsFor(Role.ACCUSED).map { it.id }
        assertTrue("sensitive" in victim && "pwd_rights" in victim && "rights" !in victim)
        assertTrue("rights" in accused && "rights_adult" in accused && "sensitive" !in accused && "pwd_rights" !in accused)
        assertEquals(victim - "sensitive" - "pwd_rights" - "recall", accused - "rights" - "rights_adult")
    }

    @Test
    fun anAccusedIsNeverAskedAboutEvents() {
        for (child in listOf(false, true)) {
            val groups = bundle.groupsFor(Role.ACCUSED, child).map { it.id }.toSet()
            val asked = questions.filter { it.group in groups }
            assertTrue(asked.none { it.phase == Phase.FREE_RECALL || it.phase == Phase.FOLLOW_UP })
        }
    }

    @Test
    fun aChildInConflictWithLawGetsJuvenileRightsNotAdultArrestRights() {
        val child = bundle.groupsFor(Role.ACCUSED, child = true).map { it.id }.toSet()
        val adult = bundle.groupsFor(Role.ACCUSED, child = false).map { it.id }.toSet()
        assertTrue("rights_child" in child && "rights_adult" !in child)
        assertTrue("rights_adult" in adult && "rights_child" !in adult)
        val childItems = questions.filter { it.group in child }.map { it.id }
        assertTrue(listOf("r2", "r3", "r6").none { it in childItems })
        assertTrue(listOf("j1", "j2", "j3", "j4").all { it in childItems })
    }

    @Test
    fun roleSurvivesTheDatabase() = runBlocking {
        val dao = FakeDao()
        val repo = SessionRepository(dao)
        val id = repo.start(Profile.YOUNG_CHILD, "hi", Role.ACCUSED)
        assertEquals(Role.ACCUSED, repo.openSession()?.role)
        repo.setRole(id, Role.VICTIM)
        assertEquals(Role.VICTIM, repo.openSession()?.role)
    }

    private class FakeDao : SessionDao {
        var row: SessionEntity? = null
        override suspend fun insertSession(session: SessionEntity) { row = session }
        override suspend fun openSession() = row
        override fun session(id: String): Flow<SessionEntity?> = emptyFlow()
        override suspend fun setSubjectLang(id: String, lang: String) = Unit
        override suspend fun setProfile(id: String, profile: String) = Unit
        override suspend fun setRole(id: String, role: String) { row = row?.copy(role = role) }
        override suspend fun insertEvent(event: EventEntity) = 0L
        override fun events(sessionId: String): Flow<List<EventEntity>> = emptyFlow()
        override suspend fun insertDrawing(drawing: DrawingEntity) = 0L
        override suspend fun drawing(id: Long): ByteArray? = null
        override suspend fun deleteSession(id: String) = Unit
        override suspend fun deleteStartedBefore(cutoff: Long) = 0
    }
}
