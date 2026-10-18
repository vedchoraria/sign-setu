package app.signsetu

import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Group
import app.signsetu.data.content.Kind
import app.signsetu.data.content.Phase
import app.signsetu.data.content.Question
import app.signsetu.data.session.EventSource
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.AUTO_PHASES
import app.signsetu.ui.session.AutoKey
import app.signsetu.ui.session.Reason
import app.signsetu.ui.session.SHOWN_OPENING
import app.signsetu.ui.session.SHOWN_RIGHTS
import app.signsetu.ui.session.ShownBy
import app.signsetu.ui.session.autoAdvanceAllowed
import app.signsetu.ui.session.candidates
import app.signsetu.ui.session.next
import app.signsetu.ui.session.notActedOn
import app.signsetu.ui.session.shownBy
import app.signsetu.ui.session.shownReason
import app.signsetu.ui.session.withQuestions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InterviewTest {

    @Serializable private data class Triage(val groups: List<Group>, val questions: List<Question>)
    @Serializable private data class Ids(val id: String)
    @Serializable private data class Cards(val cards: List<Ids>)
    @Serializable private data class Gestures(val gestures: List<Ids>)

    private val json = Json { ignoreUnknownKeys = true }
    private fun asset(name: String) = File("src/main/assets/content/$name").readText()
    private val triage = json.decodeFromString<Triage>(asset("triage.json"))
    private val bank = bundle(triage.groups, triage.questions)

    private fun bundle(groups: List<Group>, questions: List<Question>) = ContentBundle(
        groups = groups, questions = questions, categories = emptyList(), cards = emptyMap(),
        lexicon = emptyMap(), languages = emptyList(), subjectUi = emptyMap(), rejected = emptySet(),
    )

    /** A session log built by hand, the way the view model writes it. */
    private class Log {
        val events = mutableListOf<SessionEvent>()
        private var id = 0L
        fun add(type: EventType, text: String? = null, cardId: String? = null, itemId: String? = null, shownId: Long? = null, reason: String? = null, source: EventSource = EventSource.SUBJECT): Long {
            events += SessionEvent(++id, id, type, source, "en", text, cardId, null, null, itemId, reason, shownId)
            return id
        }
        fun show(item: String) = add(EventType.QUESTION_SHOWN, item, itemId = item, source = EventSource.PRESET)
        fun ask(item: String, card: String?) = show(item).also { s -> card?.let { add(EventType.ANSWER_CARD, it, cardId = it, shownId = s) } }
        fun system(key: String) = add(EventType.SYSTEM, key, source = EventSource.OFFICER)
    }

    private fun run(role: Role, log: Log, answer: String? = "yes"): List<String> {
        val asked = mutableListOf<String>()
        while (true) {
            val p = next(bank, role, log.events) ?: return asked
            asked += p.q.id
            log.ask(p.q.id, answer)
            check(asked.size < 100)
        }
    }

    @Test
    fun proposalsFollowThePhases() {
        val log = Log().apply { show("s1") }
        val expected = listOf("g1", "g2", "g3", "g4", "s2", "h1", "s3", "s4", "h2", "h3", "h4", "n1", "n2", "n3", "n4", "n5") +
            listOf("i1", "i2", "i3", "f1", "f2", "f3", "f4", "l1", "l3", "c1", "c2")
        assertEquals(expected, run(Role.VICTIM, log))
    }

    @Test
    fun theStageOpeningCoversTheGroundRules() {
        val log = Log().apply { add(EventType.QUESTION_SHOWN, "s1", itemId = "s1", reason = SHOWN_OPENING, source = EventSource.PRESET) }
        assertEquals("s2", next(bank, Role.VICTIM, log.events)?.q?.id)
        log.system("rules_skipped")
        assertEquals("an opening cut short leaves the rules to auto mode", "g1", next(bank, Role.VICTIM, log.events)?.q?.id)
    }

    @Test
    fun manualPicksDoNotMoveThePhase() {
        val log = Log().apply { show("s1"); ask("l1", "home"); ask("x1", "yes") }
        assertEquals("g1", next(bank, Role.VICTIM, log.events)?.q?.id)
        listOf("g1", "g2", "g3", "g4").forEach { log.ask(it, null) }
        assertEquals("s2", next(bank, Role.VICTIM, log.events)?.q?.id)
    }

    @Test
    fun autoNeverProposesRecallFollowUpRightsOrSensitiveItems() {
        val forbidden = triage.questions.filter { q -> q.phase !in AUTO_PHASES || triage.groups.first { it.id == q.group }.let { it.sensitive || it.rights } }.map { it.id }.toSet()
        assertTrue(forbidden.containsAll(listOf("l2", "x1", "x2", "x3", "r1", "r7", "pw1")))
        for (role in Role.entries) {
            for (answer in listOf("yes", "dont_know")) {
                val log = Log().apply { show("s1") }
                var steps = 0
                while (steps++ < 60) {
                    val all = candidates(bank, role, log.events)
                    assertTrue("$role $answer ${all.map { it.q.id }}", all.none { it.q.id in forbidden })
                    log.ask(all.firstOrNull()?.q?.id ?: break, answer)
                }
            }
        }
    }

    @Test
    fun anItemEndedUnsureOrStoppedIsNotOfferedAgain() {
        for (card in listOf("dont_know", "dont_understand", "stop", null)) {
            val log = Log().apply { show("s1"); ask("k1", card) }
            log.ask("s2", "dont_know"); log.ask("s3", "dont_know")
            val first = next(bank, Role.VICTIM, log.events)!!
            assertEquals("after $card", "k2", first.q.id)
        }
        val log = Log().apply { show("s1"); ask("s4", "dont_know") }
        assertFalse(candidates(bank, Role.VICTIM, log.events).any { it.q.id == "s4" })
    }

    @Test
    fun anUnsureOptionIsNotFollowedByAnotherOptionFromItsGroup() {
        val g = Group("g", mapOf("en" to "G"))
        fun q(id: String, kind: Kind) = Question(id, "g", mapOf("en" to "$id?", "hi" to "$id?"), phase = Phase.WELFARE, kind = kind)
        val small = bundle(listOf(g), listOf(q("a", Kind.OPTION), q("b", Kind.OPTION), q("c", Kind.DIRECTIVE)))
        val log = Log().apply { ask("a", "dont_know") }
        assertEquals("c", next(small, Role.VICTIM, log.events)?.q?.id)
        val sure = Log().apply { ask("a", "yes") }
        assertEquals("b", next(small, Role.VICTIM, sure.events)?.q?.id)
    }

    @Test
    fun twoUnsureAnswersOfferABreak() {
        val log = Log().apply { show("s1"); ask("s2", "dont_know"); ask("s3", null) }
        val p = next(bank, Role.VICTIM, log.events)!!
        assertEquals("k1" to Reason.BREAK_AFTER_UNSURE, p.q.id to p.reason)
        log.ask("k1", "no")
        assertTrue("one offer per run", candidates(bank, Role.VICTIM, log.events).none { it.reason == Reason.BREAK_AFTER_UNSURE })
    }

    @Test
    fun afterAResumeTheFirstQuestionIsWhetherToCarryOn() {
        val log = Log().apply { show("s1"); ask("s2", "stop"); system(AutoKey.PAUSED); system(AutoKey.RESUMED) }
        val p = next(bank, Role.VICTIM, log.events)!!
        assertEquals("k3" to Reason.CARRY_ON, p.q.id to p.reason)
        log.ask("k3", "yes")
        assertTrue(candidates(bank, Role.VICTIM, log.events).none { it.reason == Reason.CARRY_ON })
    }

    @Test
    fun aFollowUpNeedsItsAnswerUnchanged() {
        val yes = Log().apply { show("s1"); ask("s2", "yes") }
        val p = next(bank, Role.VICTIM, yes.events)!!
        assertEquals("h1" to Reason.FOLLOWS_ANSWER, p.q.id to p.reason)
        val no = Log().apply { show("s1"); ask("s2", "no") }
        assertTrue(candidates(bank, Role.VICTIM, no.events).none { it.q.id == "h1" })
        val changed = Log().apply { show("s1"); val s = show("s2"); add(EventType.ANSWER_CARD, cardId = "yes", shownId = s); add(EventType.ANSWER_CARD, cardId = "no", shownId = s) }
        assertTrue(candidates(bank, Role.VICTIM, changed.events).none { it.q.id == "h1" })
    }

    @Test
    fun wrapUpLeavesOnlyTheClosing() {
        val log = Log().apply { show("s1"); system(AutoKey.WRAP_UP) }
        assertEquals(listOf("c1", "c2"), candidates(bank, Role.VICTIM, log.events).map { it.q.id })
        log.ask("c1", null); log.ask("c2", "no")
        assertNull(next(bank, Role.VICTIM, log.events))
    }

    @Test
    fun onlyStatementsAdvanceByThemselves() {
        val q = triage.questions.associateBy { it.id }
        val yes = SessionEvent(1, 1, EventType.ANSWER_CARD, EventSource.SUBJECT, "en", null, "yes", null, null)
        assertTrue(autoAdvanceAllowed(q.getValue("g1"), yes, q.getValue("g2"), "hi", 0))
        assertTrue(autoAdvanceAllowed(q.getValue("g1"), null, q.getValue("g2"), "hi", 2))
        for (to in listOf("k1", "s4", "c2")) assertFalse(to, autoAdvanceAllowed(q.getValue("g1"), yes, q.getValue(to), "hi", 0))
        for (card in listOf("stop", "dont_know", "dont_understand")) {
            assertFalse(card, autoAdvanceAllowed(q.getValue("g1"), yes.copy(cardId = card), q.getValue("g2"), "hi", 0))
        }
        assertFalse(autoAdvanceAllowed(q.getValue("g1"), yes.copy(type = EventType.ANSWER_SIGNED, cardId = null), q.getValue("g2"), "hi", 0))
        assertFalse("no caption in Santali", autoAdvanceAllowed(q.getValue("g1"), yes, q.getValue("g2"), "sat", 0))
        assertFalse(autoAdvanceAllowed(q.getValue("g1"), yes, q.getValue("g2"), "hi", 3))
        assertFalse("a safety answer always reaches the officer", autoAdvanceAllowed(q.getValue("s3"), yes, q.getValue("g2"), "hi", 0))
    }

    @Test
    fun answersPairWithTheItemTheyName() {
        val log = Log().apply {
            val shown = show("s2")
            system(AutoKey.MODE_AUTO)
            add(EventType.ANSWER_CARD, "Yes", cardId = "yes", shownId = shown)
        }
        assertEquals("s2", log.events.withQuestions()[2].second)
        assertEquals("a SYSTEM row between does not orphan the answer", "h1", next(bank, Role.VICTIM, log.events)?.q?.id)
        val legacy = Log().apply { add(EventType.QUESTION_SHOWN, "Water?"); add(EventType.ANSWER_CARD, "Yes") }
        assertEquals("Water?", legacy.events.withQuestions()[1].second)
    }

    @Test
    fun theRecordSaysWhoChoseEachItem() {
        val log = Log().apply {
            system(AutoKey.MODE_AUTO)
            add(EventType.SUGGESTED, itemId = "g1", source = EventSource.RULE)
            add(EventType.APPROVED, itemId = "g1", source = EventSource.OFFICER)
            show("g1")
            add(EventType.SUGGESTED, itemId = "g2", source = EventSource.RULE)
            add(EventType.AUTO_ADVANCED, itemId = "g2", source = EventSource.RULE)
            show("g2")
            add(EventType.SUGGESTED, itemId = "g3", source = EventSource.RULE)
            show("n1")
        }
        val by = shownBy(log.events)
        assertEquals(listOf(ShownBy.APP, ShownBy.AUTO, ShownBy.OFFICER), log.events.filter { it.type == EventType.QUESTION_SHOWN }.map { by[it.id] })
        assertEquals(setOf(log.events.first { it.itemId == "g3" }.id), notActedOn(log.events))
        assertTrue("a manual session gets no tags", shownBy(Log().apply { show("s1") }.events).isEmpty())
    }

    @Test
    fun rightsAreRecordedAsShownNotUnderstood() {
        val q = triage.questions.associateBy { it.id }
        assertEquals(SHOWN_RIGHTS, shownReason(bank, q.getValue("r1"), "en"))
        assertEquals(SHOWN_RIGHTS, shownReason(bank, q.getValue("pw3"), "hi"))
        assertNull(shownReason(bank, q.getValue("s2"), "hi"))
        assertEquals("caption_missing", shownReason(bank, q.getValue("s2"), "sat"))
    }

    /** Fails the build when the bank breaks a rule auto mode or the stage relies on. */
    @Test
    fun theBankKeepsItsRules() {
        val groups = triage.groups.associateBy { it.id }
        val cards = json.decodeFromString<Cards>(asset("cards.json")).cards.map { it.id }.toSet()
        val gestures = (json.decodeFromString<Gestures>(asset("gestures.json")).gestures + json.decodeFromString<Gestures>(asset("signs.json")).gestures).map { it.id }.toSet()
        val ids = triage.questions.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        for (q in triage.questions) {
            val g = groups.getValue(q.group)
            assertTrue("${q.id} has a phase", q.phase != null)
            if (g.sensitive || g.rights) assertFalse("${q.id} is never proposed", q.phase in AUTO_PHASES)
            if (q.phase in AUTO_PHASES && !g.sensitive && !g.rights) {
                assertTrue("${q.id} asks one thing", q.text.getValue("en").count { it == '?' } <= 1)
            }
            for (lang in listOf("en", "hi", "hne", "gon", "hlb")) assertTrue("${q.id} has $lang", q.text[lang] != null)
            q.after?.let { a ->
                val (ref, want) = a.split(':')
                assertTrue("${q.id} follows a real item", ref in ids)
                assertTrue("${q.id} follows a real answer", want == "any" || want in cards)
            }
            q.cards.forEach { assertTrue("${q.id} card $it", it in cards) }
            (q.gesture ?: q.lexicon)?.let { assertTrue("${q.id} gesture $it", it in gestures) }
        }
        assertTrue(groups.getValue("rights").rights && groups.getValue("pwd_rights").rights)
        assertEquals(listOf(Role.VICTIM), groups.getValue("pwd_rights").roles)
        val reworded = triage.questions.filter { it.id in setOf("x2", "x3") }.map { it.text.getValue("en") }
        assertTrue("x2 and x3 presuppose nothing", reworded.none { "someone" in it || "hurt" in it })
    }

    @Test
    fun aQuestionWithoutAPhaseIsNeverProposed() {
        val g = Group("g", mapOf("en" to "G"))
        val small = bundle(listOf(g), listOf(Question("a", "g", mapOf("en" to "a?"))))
        assertNull(next(small, Role.VICTIM, emptyList()))
    }

    @Test
    fun aRoleNotYetKnownGetsOnlySharedGroups() {
        val ids = bank.groupsFor(Role.NOT_KNOWN).map { it.id }
        assertTrue(ids.none { it in setOf("sensitive", "rights", "pwd_rights") })
        assertTrue("talk" in ids && "safety" in ids)
    }
}
