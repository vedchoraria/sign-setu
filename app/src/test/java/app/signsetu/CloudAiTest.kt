package app.signsetu

import app.signsetu.assistant.Assistant
import app.signsetu.assistant.CloudAi
import app.signsetu.assistant.Direct
import app.signsetu.assistant.ModelStore
import app.signsetu.assistant.Notice
import app.signsetu.assistant.OfflineReport
import app.signsetu.data.content.Language
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiTest {
    private class Vault(var token: String? = null, private val writable: Boolean = true) : TokenVault {
        override fun get() = token
        override fun put(token: String): Boolean {
            if (!writable) return false
            this.token = token
            return true
        }
        override fun clear() { token = null }
    }

    private class Server(val reply: (String, String?) -> Reply?) : Http {
        val calls = mutableListOf<Triple<String, String?, String?>>()
        val timeouts = mutableListOf<Int>()
        override fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply? {
            calls += Triple(url, body, bearer)
            timeouts += timeoutMs
            return reply(url, body)
        }
    }

    private fun ai(server: Server, vault: Vault = Vault("tok"), on: Boolean = true, online: Boolean = true, url: String = "https://gw.example") =
        CloudAi({ on }, url, server, vault) { online }

    private val text = Server { _, _ -> Reply(200, """{"text":"hello Ravi"}""") }

    @Test
    fun aTokenThatCannotBeStoredIsNotReportedAsEnrolled() = runBlocking {
        val notices = mutableListOf<Notice>()
        val vault = Vault(writable = false)
        val server = Server { _, _ -> Reply(200, """{"token":"tok"}""") }
        val c = ai(server, vault)
        c.onNotice = { notices += it }

        assertFalse(c.enroll("enrol-code"))
        assertNull(vault.get())
        assertEquals(listOf(Notice.ENROLL_FAILED), notices)
    }

    @Test
    fun offlineSwitchedOffOrUnenrolledNeverTouchesTheNetwork() = runBlocking {
        for (c in listOf(ai(text, online = false), ai(text, on = false), ai(text, Vault(null)), ai(text, url = ""), ai(text, url = "http://gw.example"))) {
            assertNull(c.ask("s", "u", emptyList()))
        }
        assertTrue(text.calls.isEmpty())
    }

    @Test
    fun namesAreMaskedOnTheWireAndRestoredOnReturn() = runBlocking {
        val s = Server { _, body -> Reply(200, """{"text":"seen ${if (body!!.contains("Asha")) "LEAK" else "Ravi"}"}""") }
        assertEquals("seen Asha", ai(s).ask("sys", "Asha spoke", listOf("Asha")))
        assertEquals("tok", s.calls.single().third)
    }

    @Test
    fun aRevokedDeviceClearsItsTokenAndFallsBack() = runBlocking {
        val v = Vault("tok")
        assertNull(ai(Server { _, _ -> Reply(401, "") }, v).ask("s", "u", emptyList()))
        assertNull(v.token)
    }

    @Test
    fun serverErrorOrUnreachableFallsBack() = runBlocking {
        assertNull(ai(Server { _, _ -> Reply(500, "") }).ask("s", "u", emptyList()))
        assertNull(ai(Server { _, _ -> null }).ask("s", "u", emptyList()))
    }

    @Test
    fun enrolmentStoresTheTokenOnlyOnSuccess() = runBlocking {
        val v = Vault()
        assertFalse(ai(Server { _, _ -> Reply(403, "") }, v).enroll("wrong"))
        assertNull(v.token)
        assertTrue(ai(Server { _, _ -> Reply(200, """{"device_id":"d","token":"abc"}""") }, v).enroll(" code "))
        assertEquals("abc", v.token)
    }

    @Test
    fun enrolmentGivesUpSoonerThanAChatDoes() = runBlocking {
        val enrol = Server { _, _ -> Reply(200, """{"device_id":"d","token":"abc"}""") }
        assertTrue(ai(enrol, Vault()).enroll("code"))
        assertEquals(listOf(15_000), enrol.timeouts)

        assertTrue(ai(text).ask("s", "u", emptyList()) != null)
        assertEquals(listOf(60_000), text.timeouts)
    }

    @Test
    fun aRateLimitedEnrolmentAsksTheOfficerToWait() = runBlocking {
        val notices = mutableListOf<Notice>()
        val c = ai(Server { _, _ -> Reply(429, "") }, Vault())
        c.onNotice = { notices += it }

        assertFalse(c.enroll("valid-code"))
        assertEquals(listOf(Notice.ENROLL_RATE_LIMITED), notices)
    }

    @Test
    fun theOfficerIsToldOncePerChange() = runBlocking {
        var online = false
        var fail = false
        val server = Server { _, _ -> if (fail) Reply(500, "") else Reply(200, """{"text":"ok"}""") }
        val told = mutableListOf<Notice>()
        val c = CloudAi({ true }, "https://gw.example", server, Vault("tok")) { online }.apply { onNotice = { told += it } }
        c.ask("s", "u", emptyList()); c.ask("s", "u", emptyList())
        online = true
        c.ask("s", "u", emptyList()); c.ask("s", "u", emptyList())
        fail = true
        c.ask("s", "u", emptyList())
        fail = false
        c.ask("s", "u", emptyList())
        assertEquals(listOf(Notice.FALLBACK_OFFLINE, Notice.BACK_ONLINE, Notice.FALLBACK_ERROR, Notice.BACK_ONLINE), told)
    }

    @Test
    fun anUnenrolledOrSwitchedOffAssistantStaysSilent() = runBlocking {
        val told = mutableListOf<Notice>()
        ai(text, Vault(null), online = false).apply { onNotice = { told += it } }.ask("s", "u", emptyList())
        ai(text, on = false, online = false).apply { onNotice = { told += it } }.ask("s", "u", emptyList())
        assertTrue(told.isEmpty())
    }

    @Test
    fun withoutAGatewayTheDirectServiceIsUsedInItsOwnFormat() = runBlocking {
        val s = Server { _, _ -> Reply(200, """{"choices":[{"message":{"role":"assistant","content":" hi Ravi "}}]}""") }
        val c = CloudAi({ true }, "", s, Vault(null), Direct("https://llm.example/v1/chat", "m", "k")) { true }
        assertTrue(c.available && c.enrolled && !c.viaGateway)
        assertEquals("hi Asha", c.ask("sys", "Asha here", listOf("Asha")))
        val (url, body, bearer) = s.calls.single()
        assertEquals("https://llm.example/v1/chat", url)
        assertEquals("k", bearer)
        assertTrue(body!!.contains("\"model\":\"m\"") && !body.contains("Asha"))
    }

    @Test
    fun aResponsesServiceIsAskedInItsOwnFormatAndAnsweredFromTheMessageOnly() = runBlocking {
        val answer = """{"output":[{"type":"reasoning","content":[]},{"type":"message","content":[{"type":"output_text","text":" hi Ravi"},{"type":"output_text","text":" there "}]}]}"""
        val s = Server { _, _ -> Reply(200, answer) }
        val c = CloudAi({ true }, "", s, Vault(null), Direct("https://llm.example/v1/responses", "m", "k")) { true }
        assertEquals("hi Asha there", c.ask("sys", "Asha here", listOf("Asha")))
        val (url, body, bearer) = s.calls.single()
        assertEquals("https://llm.example/v1/responses", url)
        assertEquals("k", bearer)
        assertTrue(body!!.contains("\"instructions\":\"sys\"") && body.contains("\"input\":\"") && body.contains("\"reasoning\":{\"effort\":\"low\"}"))
        assertFalse(body.contains("Asha"))
    }

    @Test
    fun aFailedBackTranslationShowsNoMeaningRatherThanTheOfficersOwnWords() = runBlocking {
        var n = 0
        val s = Server { _, _ -> if (n++ == 0) Reply(200, """{"text":"पानी चाहिए?"}""") else Reply(400, "") }
        val models = ModelStore(android.content.ContextWrapper(null))
        val suggestion = Assistant(models, ai(s)).suggest("Do you want water?", Language("hi", "हिन्दी", "Hindi", "नमस्ते"))!!
        assertEquals("पानी चाहिए?", suggestion.caption)
        assertNull(suggestion.meaning)
    }

    @Test
    fun aKeylessDirectServiceIsNotAvailable() {
        assertFalse(CloudAi({ true }, "", text, Vault(null), Direct("https://llm.example", "m", "")) { true }.available)
    }

    @Test
    fun aKeyTheOfficerTypesOverridesTheOneCompiledIntoTheApp() = runBlocking {
        val s = Server { _, _ -> Reply(200, """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""") }
        val c = CloudAi({ true }, "", s, Vault(null), Direct("https://llm.example/v1/chat", "m", "embedded"), Vault("typed")) { true }
        assertEquals("ok", c.ask("sys", "hi", emptyList()))
        assertEquals("typed", s.calls.single().third)
    }

    @Test
    fun aKeyTypedIntoADirectBuildIsStoredAndMakesItAvailable() {
        val own = Vault(null)
        val c = CloudAi({ true }, "", text, Vault(null), Direct("https://llm.example/v1/chat", "m", ""), own) { true }
        assertFalse(c.available)
        assertTrue(c.saveCustom("https://llm.example/v1", "m2", " typed "))
        assertTrue(c.available && c.enrolled)
        assertEquals("typed", own.token)
    }

    @Test
    fun offlineReportExtractsAnswersPainAgesCuesAndNotesFromTheLog() {
        val rawLog = """
            [QUESTION_SHOWN] [TEXT] text=Are you feeling pain anywhere? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=Pain in arm card=pain_arm lang=hi cue:nod]
            [QUESTION_SHOWN] [TEXT] text=How old are you? card=null lang=en
            [ANSWER_CARD] [SUBJECT] text=null card=n9 lang=en
            [QUESTION_SHOWN] [TEXT] text=What happened? card=null lang=en
            [ANSWER_SIGNED] [SUBJECT] text=attack home scared card=null lang=en
            [ANSWER_DRAWING] [SUBJECT] text=null card=null lang=null
            [NOTE] [OFFICER] text=Victim accompanied by aunt; appears shaken but communicative. card=null lang=en
        """.trimIndent()

        val report = OfflineReport.synthesizeOfflineForensicReport(rawLog, role = "VICTIM", regionalDialect = "Chhattisgarhi")

        assertTrue(report.startsWith("MACHINE DRAFT SUMMARY: NOT PART OF THE RECORD"))
        assertTrue(report.contains("Role: Victim or witness"))
        assertTrue(report.contains("Items shown: 3"))
        assertTrue(report.contains("Drawings: 1"))
        // An answer with no text is recorded by its card.
        assertTrue(report.contains("   • n9"))
        assertTrue(report.contains("Body areas the subject picked: arm\n"))
        assertTrue(report.contains("   • Victim accompanied by aunt; appears shaken but communicative."))
        // No legal, medical or reliability claim is made for the officer.
        listOf("Verified", "MLC", "Section", "tamper").forEach { assertTrue(it, it !in report) }
        assertTrue(OfflineReport.synthesizeOfflineForensicReport("", "ACCUSED", "hi").contains("Role: Accused or in custody"))
    }

    @Test
    fun followUpsFollowTheTopicOfTheLastAnswerAndWhetherTheSubjectIsAChild() {
        fun first(answer: String, child: Boolean) = OfflineReport.offlineTraumaFollowUps(answer, child).first()
        assertEquals("Would you like a glass of water?", first("pain in stomach", child = true))
        assertEquals("Do you need medical attention right away?", first("मुझे दर्द है", child = false))
        assertEquals("Do you feel safe in this room?", first("I am afraid", child = false))
        assertEquals("When did this happen?", first("they hit me", child = false))
        assertEquals("Can you tell me more about home?", first("at home", child = true))
        assertEquals("Do you feel safe at school?", first("teacher at school", child = true))
        assertEquals("Can you tell me more about that?", first("bad touch", child = false))
        assertEquals("Can you tell me more about that night?", first("it happened at night in the dark", child = true))
        assertEquals("Are you feeling comfortable?", first("blue", child = true))
        assertEquals("Are you comfortable continuing?", first("blue", child = false))
        for (child in listOf(true, false)) {
            assertTrue(OfflineReport.offlineTraumaFollowUps("pain", child).all { it.endsWith("?") })
        }
    }
}
