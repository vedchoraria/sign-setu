package app.signsetu

import app.signsetu.assistant.AiEndpoint
import app.signsetu.assistant.AiProbe
import app.signsetu.assistant.CloudAi
import app.signsetu.assistant.Direct
import app.signsetu.net.Http
import app.signsetu.net.Reply
import app.signsetu.net.TokenVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProbeTest {
    private class Server(val reply: (String, String, String?) -> Reply?) : Http {
        val calls = mutableListOf<List<String?>>()
        override fun call(method: String, url: String, body: String?, bearer: String?, timeoutMs: Int): Reply? {
            calls += listOf(method, url, body, bearer)
            return reply(method, url, body)
        }
    }

    @Test
    fun aBaseAddressBecomesTheChatAndModelsAddresses() {
        for (base in listOf("https://api.openai.com/v1", "https://api.openai.com/v1/", " https://api.openai.com/v1/chat/completions ")) {
            assertEquals("https://api.openai.com/v1/chat/completions", AiEndpoint.chatUrl(base))
            assertEquals("https://api.openai.com/v1/models", AiEndpoint.modelsUrl(base))
        }
        // A bare host gets the usual /v1; a responses address is kept as given.
        assertEquals("https://llm.example/v1/chat/completions", AiEndpoint.chatUrl("https://llm.example"))
        assertEquals("https://llm.example/v1/responses", AiEndpoint.chatUrl("https://llm.example/v1/responses"))
        assertEquals("https://llm.example/v1/models", AiEndpoint.modelsUrl("https://llm.example/v1/responses"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", AiEndpoint.chatUrl("https://openrouter.ai/api/v1"))
    }

    @Test
    fun modelsAreReadFromTheCommonListShapes() {
        assertEquals(listOf("a", "b"), AiEndpoint.parseModels("""{"data":[{"id":"b"},{"id":"a"},{"id":"a"}]}"""))
        assertEquals(listOf("models/x"), AiEndpoint.parseModels("""{"models":[{"name":"models/x"}]}"""))
        assertEquals(emptyList<String>(), AiEndpoint.parseModels("not json"))
    }

    @Test
    fun discoveryAsksTheModelsAddressWithTheKey() = runBlocking {
        val s = Server { _, _, _ -> Reply(200, """{"data":[{"id":"m1"},{"id":"m2"}]}""") }
        assertEquals(listOf("m1", "m2"), AiProbe(s).models("https://llm.example/v1", "k"))
        assertEquals(listOf<String?>("GET", "https://llm.example/v1/models", null, "k"), s.calls.single())
        assertNull(AiProbe(Server { _, _, _ -> Reply(401, "no") }).models("https://llm.example/v1", "bad"))
        assertNull(AiProbe(Server { _, _, _ -> null }).models("https://llm.example/v1", "k"))
        assertNull("cleartext is never tried", AiProbe(s).models("http://llm.example/v1", "k").also { assertEquals(1, s.calls.size) })
    }

    @Test
    fun aPingReportsTheServiceAnswerOrWhatWentWrong() = runBlocking {
        val good = Server { _, _, _ -> Reply(200, """{"choices":[{"message":{"role":"assistant","content":"pong"}}]}""") }
        val ok = AiProbe(good).ping("https://llm.example/v1", "m", "k")
        assertTrue(ok.ok)
        val (method, url, body, bearer) = good.calls.single()
        assertEquals("POST", method)
        assertEquals("https://llm.example/v1/chat/completions", url)
        assertEquals("k", bearer)
        assertTrue(body!!.contains("\"model\":\"m\"") && !body.contains("reasoning"))

        val refused = AiProbe(Server { _, _, _ -> Reply(401, """{"error":{"message":"bad key"}}""") }).ping("https://llm.example/v1", "m", "k")
        assertFalse(refused.ok)
        assertEquals(401, refused.code)
        assertTrue(refused.detail, refused.detail.contains("bad key"))

        val silent = AiProbe(Server { _, _, _ -> null }).ping("https://llm.example/v1", "m", "k")
        assertFalse(silent.ok)
        assertNull(silent.code)

        assertFalse(AiProbe(good).ping("http://llm.example/v1", "m", "k").ok)
        assertFalse(AiProbe(good).ping("https://llm.example/v1", "", "k").ok)
    }

    @Test
    fun aSavedServiceNeverReplacesTheGatewayOfAGatewayBuild() = runBlocking {
        val vault = object : TokenVault {
            override fun get() = "tok"
            override fun put(token: String) = true
            override fun clear() {}
        }
        val s = Server { _, _, _ -> Reply(200, """{"text":"hi"}""") }
        val c = CloudAi({ true }, "https://gw.example", s, vault, null, vault) { true }
        assertFalse(c.saveCustom("https://llm.example/v1", "m", "k"))
        c.restoreCustom("https://llm.example/v1", "m")
        assertNull(c.custom)
        assertNull(c.customKey())
        assertTrue(c.viaGateway)
        assertEquals("hi", c.ask("sys", "user", emptyList()))
        assertEquals("https://gw.example/v1/assist", s.calls.single()[1])
    }

    @Test
    fun aSavedServiceReplacesTheBuildDefaultWithoutAGateway() = runBlocking {
        val key = object : TokenVault {
            var token: String? = null
            override fun get() = token
            override fun put(token: String): Boolean { this.token = token; return true }
            override fun clear() { token = null }
        }
        val s = Server { _, _, _ -> Reply(200, """{"choices":[{"message":{"role":"assistant","content":"hi"}}]}""") }
        val c = CloudAi({ true }, "", s, key, Direct("https://api.groq.com/openai/v1/chat/completions", "d", ""), key) { true }
        assertFalse(c.viaGateway)
        assertTrue(c.saveCustom("https://llm.example/v1", "m", "k"))
        assertEquals("hi", c.ask("sys", "user", emptyList()))
        assertEquals("https://llm.example/v1/chat/completions", s.calls.single()[1])
        assertFalse(s.calls.single()[2]!!.contains("reasoning_effort"))
        c.clearCustom()
        assertFalse(c.available)
    }
}
