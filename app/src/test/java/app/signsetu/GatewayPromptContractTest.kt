package app.signsetu

import app.signsetu.assistant.BACK_TO_ENGLISH_PROMPT
import app.signsetu.assistant.REPORT_PROMPT
import app.signsetu.assistant.followUpPrompt
import app.signsetu.assistant.translatePrompt
import app.signsetu.data.content.Language
import app.signsetu.ui.session.counselPrompt
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gateway refuses any system prompt that does not open as one of these; the lists must not drift apart. */
class GatewayPromptContractTest {
    private val openings: List<String> by lazy {
        val config = File("../backend/signsetu_gateway/config.py").readText()
        val block = config.substringAfter("assist_prompt_openings").substringAfter("(").substringBefore("\n    )")
        Regex("\"([^\"]*)\"").findAll(block).map { it.groupValues[1] }.toList()
    }

    private fun allowed(prompt: String) = openings.any { prompt.trim().startsWith(it) }

    @Test
    fun theGatewayListIsReadable() {
        assertEquals(5, openings.size)
    }

    @Test
    fun everyPromptTheAppSendsIsAllowedByTheGateway() {
        val hindi = Language("hi", "हिन्दी", "Hindi", "नमस्ते")
        val prompts = listOf(
            translatePrompt(hindi),
            BACK_TO_ENGLISH_PROMPT,
            REPORT_PROMPT,
            followUpPrompt("a young traumatised child: short, gentle, simple"),
            counselPrompt("Hindi", child = true),
            counselPrompt("Hindi", child = false),
        )
        prompts.forEach { assertTrue("not allowed by the gateway: ${it.take(60)}", allowed(it)) }
    }

    @Test
    fun anUnrelatedPromptIsNotAllowed() {
        assertTrue(!allowed("Write me a poem"))
    }
}
