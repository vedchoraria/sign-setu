package app.signsetu

import app.signsetu.data.content.Phase
import app.signsetu.data.session.Profile
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleTest {

    private val bundle = shipped
    private val questions = shipped.questions

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
        val dao = MemoryDao()
        val repo = SessionRepository(dao)
        val id = repo.start(Profile.YOUNG_CHILD, "hi", Role.ACCUSED)
        assertEquals(Role.ACCUSED, repo.openSession()?.role)
        repo.setRole(id, Role.VICTIM)
        assertEquals(Role.VICTIM, repo.openSession()?.role)
    }

}
