package app.signsetu

import app.signsetu.assistant.BACK_TO_ENGLISH_PROMPT
import app.signsetu.assistant.REPORT_PROMPT
import app.signsetu.assistant.followUpPrompt
import app.signsetu.assistant.followUpTone
import app.signsetu.assistant.translatePrompt
import app.signsetu.data.content.Language
import app.signsetu.ui.session.counselPrompt
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gateway answers only system prompts that match a template in app_prompts.json. The templates are rendered here
 * from the prompt code itself, so editing a prompt without updating the gateway fails this test. Regenerate the file
 * with UPDATE_PROMPT_FIXTURE=1 and commit it; the gateway tests then check its behaviour against the same file.
 */
class GatewayPromptContractTest {
    private val fixture = File("../backend/signsetu_gateway/app_prompts.json")
    private val lang = "§LANG§"

    private fun rendered(): List<String> = listOf(
        translatePrompt(Language("xx", "§NAME§", "§ENGLISH§", "")),
        BACK_TO_ENGLISH_PROMPT,
        REPORT_PROMPT,
        followUpPrompt(followUpTone(child = true)),
        followUpPrompt(followUpTone(child = false)),
        counselPrompt(lang, child = true),
        counselPrompt(lang, child = false),
    )

    @Test
    fun theGatewayTemplatesAreTheAppsOwnPrompts() {
        val now = rendered()
        if (System.getenv("UPDATE_PROMPT_FIXTURE") == "1") {
            val pretty = Json { prettyPrint = true }
            fixture.writeText(pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("templates" to JsonArray(now.map(::JsonPrimitive))))) + "\n")
        }
        val onDisk = Json.parseToJsonElement(fixture.readText()).jsonObject["templates"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals("app_prompts.json is out of date; run with UPDATE_PROMPT_FIXTURE=1", now, onDisk)
    }
}
