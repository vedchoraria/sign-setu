package app.signsetu

import app.signsetu.assistant.CloudAi
import app.signsetu.assistant.Direct
import app.signsetu.assistant.Notice
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
    fun aKeylessDirectServiceIsNotAvailable() {
        assertFalse(CloudAi({ true }, "", text, Vault(null), Direct("https://llm.example", "m", "")) { true }.available)
    }
}
