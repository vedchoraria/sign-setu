package app.signsetu.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val max_tokens: Int = 1000,
)

@Serializable
data class ChatChoice(val message: ChatMessage)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)

/**
 * AI Bridge for TokenRouter / Nemotron Omni-30B model inference,
 * report extraction, regional translation (Chhattisgarhi, Gondi, Halbi, Hindi, ISL),
 * and automated interview synthesis.
 */
object TokenRouterAiService {
    private const val BASE_URL = "https://api.tokenrouter.com/v1/chat/completions"
    private const val API_KEY = "sk-lrApMo2FRUgac9WlxNaGDCUxGFv4jkC9CzNMZFgDJTbwE9Nj"
    private const val DEFAULT_MODEL = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun chat(
        messages: List<ChatMessage>,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.2,
        maxTokens: Int = 800,
    ): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(BASE_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $API_KEY")
                connectTimeout = 12000
                readTimeout = 25000
                doOutput = true
            }

            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                max_tokens = maxTokens,
            )
            val requestBody = json.encodeToString(ChatCompletionRequest.serializer(), request)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), responseText)
                parsed.choices.firstOrNull()?.message?.content?.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Extracts an executive structured victim/accused trauma-informed police report
     * directly from dialogue and recognized ISL/gestures.
     */
    suspend fun extractInvestigationReport(
        sessionLogText: String,
        role: String = "VICTIM",
        regionalDialect: String = "Chhattisgarhi / ISL",
    ): String? {
        val systemPrompt = """
            You are an expert senior child-protection and trauma-informed forensic police investigator.
            Task: Need to extract report from victim/accused based on the raw session transcription, gestures, AAC inputs and ISL signs.
            Regional Context: Chhattisgarh (supporting Chhattisgarhi, Gondi, Halbi, Hindi, and Indian Sign Language).

            Structure the report clearly with:
            1. INCIDENT ESSENCE & TIMELINE
            2. ALLEGATIONS / STATEMENTS BY SUBJECT (Preserving exact gestures/words)
            3. WELFARE & IMMEDIATE NEEDS IDENTIFIED (Medical, fear, safety)
            4. ACCUSED / PERSONS INVOLVED (If mentioned)
            5. FORENSIC ADMISSIBILITY & RELIABILITY ASSESSMENT (Non-leading confirmation check)
            6. RECOMMENDED IMMEDIATE POLICE & CWC ACTIONS

            Keep the report professional, factual, objective, and trauma-informed.
        """.trimIndent()

        val messages = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "Role: $role\nRegional Language: $regionalDialect\nSession Log & Transcripts:\n$sessionLogText"),
        )
        return chat(messages, temperature = 0.1, maxTokens = 1200)
    }

    /**
     * Regional translation for Chhattisgarh languages (Chhattisgarhi 'hne', Gondi 'gon', Halbi 'hlb') & ISL gloss.
     */
    suspend fun translateRegional(
        text: String,
        targetLangCode: String,
    ): String? {
        val targetName = when (targetLangCode) {
            "hne" -> "Chhattisgarhi (छत्तीसगढ़ी)"
            "gon" -> "Gondi (गोंडी)"
            "hlb" -> "Halbi (हल्बी)"
            "hi" -> "Hindi (हिन्दी)"
            "isl" -> "Indian Sign Language (ISL Gloss stream)"
            else -> targetLangCode
        }

        val systemPrompt = "Translate accurately into $targetName for a vulnerable or traumatized subject. Keep language simple, comforting, and direct. Output only the translated sentence."
        val messages = listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", text),
        )
        return chat(messages, temperature = 0.1, maxTokens = 200)
    }
}
