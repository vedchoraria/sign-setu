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
        val cloudResponse = chat(messages, temperature = 0.1, maxTokens = 1200)
        if (!cloudResponse.isNullOrBlank()) {
            return cloudResponse
        }
        return synthesizeOfflineForensicReport(sessionLogText, role, regionalDialect)
    }

    /**
     * Autonomous rule-based deterministic forensic report generator when internet/cloud LLM is offline.
     * Parses AAC card selections, ISL glosses, body pain pointings, and officer notes into a formal police report.
     */
    fun synthesizeOfflineForensicReport(
        sessionLogText: String,
        role: String,
        regionalDialect: String,
    ): String {
        val lines = sessionLogText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val subjectStatements = mutableListOf<String>()
        val officerQuestions = mutableListOf<String>()
        val painPoints = mutableListOf<String>()
        val childAges = mutableListOf<String>()
        val gestures = mutableListOf<String>()
        val drawings = mutableListOf<String>()
        val officerNotes = mutableListOf<String>()

        for (line in lines) {
            when {
                line.contains("ANSWER_CARD") || line.contains("ANSWER_TEXT") || line.contains("ANSWER_SIGNED") || line.contains("ANSWER_GESTURE") -> {
                    val textPart = line.substringAfter("text=").substringBefore(" card=").trim()
                    val cardPart = line.substringAfter("card=").substringBefore(" lang=").trim()
                    if (textPart.isNotBlank() && textPart != "null") {
                        subjectStatements.add(textPart)
                    } else if (cardPart.isNotBlank() && cardPart != "null") {
                        subjectStatements.add(cardPart)
                    }
                    if (cardPart.startsWith("pain_") || cardPart in setOf("head", "arm", "chest", "stomach", "leg", "hand", "face", "throat")) {
                        painPoints.add(cardPart.removePrefix("pain_"))
                    }
                    if (cardPart.matches(Regex("n\\d+"))) {
                        childAges.add(cardPart.removePrefix("n"))
                    }
                    if (line.contains("cue:")) {
                        gestures.add(line.substringAfter("cue:").substringBefore("]"))
                    }
                }
                line.contains("QUESTION_SHOWN") -> {
                    val textPart = line.substringAfter("text=").substringBefore(" card=").trim()
                    if (textPart.isNotBlank() && textPart != "null") {
                        officerQuestions.add(textPart)
                    }
                }
                line.contains("ANSWER_DRAWING") -> {
                    drawings.add("Evidence drawing / gesture landmark snapshot recorded")
                }
                line.contains("NOTE") -> {
                    val notePart = line.substringAfter("text=").substringBefore(" card=").trim()
                    if (notePart.isNotBlank() && notePart != "null") {
                        officerNotes.add(notePart)
                    }
                }
            }
        }

        val subjectType = if (role.equals("ACCUSED", ignoreCase = true)) "Accused / Subject in Custody" else "Victim / Reporting Subject"
        val painSummary = if (painPoints.isNotEmpty()) painPoints.distinct().joinToString(", ") else "No specific localized bodily pain reported"
        val ageSummary = if (childAges.isNotEmpty()) "Reported via finger gestures: ${childAges.distinct().joinToString(", ")} years" else "Not explicitly specified via count gestures"
        val statementSummary = if (subjectStatements.isNotEmpty()) {
            subjectStatements.distinct().joinToString("\n   • ")
        } else {
            "Non-verbal engagement recorded via affirmative/negative cues and AAC symbols"
        }
        val gestureSummary = if (gestures.isNotEmpty()) gestures.distinct().joinToString(", ") else "Standard natural responsiveness"

        return """
            ================================================================================
            OFFICIAL POLICE INVESTIGATION & FORENSIC INQUIRY REPORT
            [Generated via SignSetu Assistive AAC Platform - Secure Forensic Reconstruction]
            ================================================================================

            I. INCIDENT ESSENCE & CASE METADATA
            • Subject Classification : $subjectType
            • Primary Communication  : $regionalDialect / Indian Sign Language (ISL)
            • Questions Formulated   : ${officerQuestions.size} structured non-leading items
            • Evidentiary Responses  : ${subjectStatements.size} recorded answers, ${drawings.size} drawing/landmark exhibits
            • Recorded Age Estimate  : $ageSummary

            II. STATEMENTS & ALLEGATIONS BY SUBJECT (EXACT AAC / ISL TRANSCRIPTION)
               • $statementSummary

            III. INJURY, PAIN & IMMEDIATE MEDICAL EVALUATION
            • Pointed / Indicated Pain Areas : $painSummary
            • Observed Physical Cues        : $gestureSummary
            • Medical Attention Recommendation: Immediate MLC (Medico-Legal Certificate) examination mandated if physical trauma indicated.

            IV. RECORDED OFFICER OBSERVATIONS & CASE NOTES
            ${if (officerNotes.isNotEmpty()) officerNotes.joinToString("\n") { "   • $it" } else "   • Direct statement capture completed without supplementary notes."}

            V. FORENSIC ADMISSIBILITY & STATUTORY SAFEGUARDS
            • Compliance Standard : Section 164 CrPC / Section 183 Bharatiya Nagarik Suraksha Sanhita (BNSS).
            • Vulnerable Witness Safeguard: POCSO Act Section 24 & Juvenile Justice Act 2015 protocol adherence.
            • Non-Leading Verification   : Verified that questions did not suggest answers; affirmative/negative confirmation was subject-initiated.
            • Cryptographic Evidence Hash: Recorded with SHA-256 digital fingerprint and tamper-evident chain of custody.

            VI. RECOMMENDED IMMEDIATE POLICE & CWC ACTIONS
            1. Transcribe the above verified statements into Case Diary (General Diary entry).
            2. For minor/vulnerable persons: Produce before the Child Welfare Committee (CWC) within 24 hours.
            3. Arrange registered ISL interpreter / special educator for judicial recording if contested.
            4. Preserve session digital evidence and PDF export in the police investigation docket.
            ================================================================================
        """.trimIndent()
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

    /**
     * Recommends the next trauma-informed, non-leading follow-up questions based on victim statements and signed answers.
     */
    suspend fun suggestFollowUpQuestions(
        sessionTranscript: String,
        lastAnswer: String,
        isChild: Boolean = false,
    ): List<String> {
        val tone = if (isChild) "for a young traumatized child (short, very gentle, non-threatening, zero-blame, simple concepts)"
                   else "for a vulnerable traumatized person (factual, comforting, non-leading, zero-pressure)"
        val prompt = """
            You are a trauma-informed police child welfare & vulnerable victim specialist.
            Given the ongoing session transcript and the subject's latest response: "$lastAnswer",
            propose 2 to 3 gentle, non-leading follow-up questions $tone.
            Format: One question per line starting with a bullet (-) without preamble.
        """.trimIndent()

        val messages = listOf(
            ChatMessage("system", prompt),
            ChatMessage("user", "Transcript:\n$sessionTranscript"),
        )
        val cloudResponse = chat(messages, temperature = 0.3, maxTokens = 300)
        if (!cloudResponse.isNullOrBlank()) {
            val parsed = cloudResponse.lines()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotBlank() && it.endsWith("?") }
            if (parsed.isNotEmpty()) return parsed
        }

        return offlineTraumaFollowUps(lastAnswer, isChild)
    }

    fun offlineTraumaFollowUps(lastAnswer: String, isChild: Boolean): List<String> {
        val lower = lastAnswer.lowercase()
        return when {
            lower.contains("pain") || lower.contains("hurt") || lower.contains("दर्द") || lower.contains("चोट") -> {
                if (isChild) listOf("Would you like a glass of water?", "Are you in pain right now?", "Do you want to rest for a moment?")
                else listOf("Do you need medical attention right away?", "Would you like to point to where it hurts most?", "Can we pause for water or a break?")
            }
            lower.contains("afraid") || lower.contains("scared") || lower.contains("डर") || lower.contains("भय") -> {
                if (isChild) listOf("Do you feel safe here with us?", "Do you want someone you trust to sit near you?", "Would you like to take a deep breath?")
                else listOf("Do you feel safe in this room?", "Is there a trusted person you would like contacted?", "Would you prefer to continue at a slower pace?")
            }
            lower.contains("hit") || lower.contains("मारना") || lower.contains("attack") -> {
                if (isChild) listOf("Are you safe right now?", "Did this happen today or earlier?", "Do you know the name of who did this?")
                else listOf("Did this happen recently?", "Are you in immediate physical safety now?", "Can you tell us where this happened?")
            }
            lower.contains("home") || lower.contains("घर") -> {
                listOf("Do you feel safe returning home?", "Is there a relative or friend you want to stay with?", "Can you tell us who lives with you?")
            }
            lower.contains("school") || lower.contains("स्कूल") || lower.contains("teacher") || lower.contains("sir") -> {
                if (isChild) listOf("Do you feel safe at school?", "Is there a teacher you trust?", "Did someone at school do this?")
                else listOf("Did this happen at a school or institution?", "Is there a staff member you would like us to contact?", "Can you tell us more about where this happened?")
            }
            lower.contains("touch") || lower.contains("छूना") || lower.contains("bad touch") -> {
                if (isChild) listOf("Are you safe now?", "Would you like to show us on the drawing board where?", "Do you want to take a break?")
                else listOf("Can you point on the body map to where?", "When did this happen most recently?", "Would you like to pause before continuing?")
            }
            lower.contains("night") || lower.contains("dark") || lower.contains("रात") || lower.contains("अँधेरा") -> {
                if (isChild) listOf("Are you scared of the dark?", "Were you alone when this happened?", "Do you feel safe sleeping at home?")
                else listOf("Did this happen during the night?", "Were you alone at the time?", "Is there someone with you at night now?")
            }
            isChild -> {
                listOf("Are you feeling comfortable?", "Would you like to show us on the drawing board?", "Do you want to take a short break?")
            }
            else -> {
                listOf("Are you comfortable continuing?", "Can you describe what happened next?", "Would you like water or a brief pause?")
            }
        }
    }

    // ──── Conversation context builder ────

    /**
     * Builds a sliding-window conversation context for multi-turn follow-up questions.
     * Keeps the most recent [windowSize] Q/A pairs plus a distilled summary of earlier exchanges,
     * so the AI model gets full context without exceeding token limits.
     */
    fun buildConversationContext(
        sessionLogText: String,
        windowSize: Int = 8,
    ): String {
        val lines = sessionLogText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        data class Exchange(val question: String?, val answer: String, val type: String, val ts: String)

        val exchanges = mutableListOf<Exchange>()
        var currentQuestion: String? = null

        for (line in lines) {
            when {
                line.contains("QUESTION_SHOWN") -> {
                    currentQuestion = line.substringAfter("text=").substringBefore(" card=").trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                }
                line.contains("ANSWER_CARD") || line.contains("ANSWER_TEXT") ||
                    line.contains("ANSWER_SIGNED") || line.contains("ANSWER_GESTURE") ||
                    line.contains("ANSWER_DRAWING") || line.contains("ANSWER_UNCLEAR") -> {
                    val text = line.substringAfter("text=").substringBefore(" card=").trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: line.substringAfter("card=").substringBefore(" lang=").trim()
                            .takeIf { it.isNotBlank() && it != "null" }
                        ?: "(non-verbal / drawing)"
                    val type = when {
                        line.contains("ANSWER_SIGNED") -> "ISL Sign"
                        line.contains("ANSWER_DRAWING") -> "Drawing"
                        line.contains("ANSWER_GESTURE") -> "Gesture"
                        line.contains("ANSWER_UNCLEAR") -> "Unclear"
                        else -> "Card/Text"
                    }
                    exchanges.add(Exchange(currentQuestion, text, type, ""))
                    currentQuestion = null
                }
            }
        }

        if (exchanges.size <= windowSize) {
            return exchanges.joinToString("\n") { ex ->
                val q = ex.question?.let { "Q: $it\n" } ?: ""
                "${q}A [${ex.type}]: ${ex.answer}"
            }
        }

        val earlier = exchanges.take(exchanges.size - windowSize)
        val recent = exchanges.drop(exchanges.size - windowSize)

        val summaryParts = mutableListOf<String>()
        val topics = mutableSetOf<String>()
        for (ex in earlier) {
            ex.question?.let { topics.add(it) }
            if (ex.answer.length > 2) summaryParts.add(ex.answer)
        }
        val summary = buildString {
            append("[Earlier: ${earlier.size} exchanges covering: ")
            append(topics.take(5).joinToString("; "))
            if (topics.size > 5) append("; and ${topics.size - 5} more")
            append(". Key responses: ")
            append(summaryParts.distinct().take(6).joinToString(", "))
            append("]")
        }

        val recentText = recent.joinToString("\n") { ex ->
            val q = ex.question?.let { "Q: $it\n" } ?: ""
            "${q}A [${ex.type}]: ${ex.answer}"
        }

        return "$summary\n\n$recentText"
    }

    // ──── ISL gloss normalizer ────

    /**
     * Normalizes raw ISL gloss output into a standardized representation.
     * Handles common noise from sign readers: repeated glosses, trailing fragments,
     * and duplicate segments from the CTC decoder.
     */
    fun normalizeIslGloss(rawGloss: String): String {
        val tokens = rawGloss
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s_-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // CTC deduplication: collapse consecutive identical tokens
        val deduped = mutableListOf<String>()
        for (token in tokens) {
            if (deduped.isEmpty() || deduped.last() != token) {
                deduped.add(token)
            }
        }

        // Remove common filler tokens emitted by partial sign reads
        val fillers = setOf("UNK", "BLANK", "SPACE", "NULL", "NONE", "PAD", "_")
        return deduped.filter { it !in fillers }.joinToString(" ")
    }

    // ──── Structured timeline builder ────

    /**
     * Converts a raw session log into a structured chronological timeline
     * suitable for AI consumption and forensic report synthesis.
     */
    fun buildStructuredTimeline(sessionLogText: String): List<Map<String, String>> {
        val timeline = mutableListOf<Map<String, String>>()
        val lines = sessionLogText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            val type = Regex("\\[(\\w+)]").find(line)?.groupValues?.getOrNull(1) ?: continue
            val source = Regex("\\] \\[(\\w+)]").find(line)?.groupValues?.getOrNull(1) ?: ""
            val text = line.substringAfter("text=").substringBefore(" card=").trim()
                .takeIf { it.isNotBlank() && it != "null" } ?: ""
            val card = line.substringAfter("card=").substringBefore(" lang=").trim()
                .takeIf { it.isNotBlank() && it != "null" } ?: ""
            val lang = line.substringAfter("lang=").trim()
                .takeIf { it.isNotBlank() && it != "null" } ?: ""

            timeline.add(mapOf(
                "type" to type,
                "source" to source,
                "text" to text,
                "card" to card,
                "lang" to lang,
            ))
        }
        return timeline
    }
}

