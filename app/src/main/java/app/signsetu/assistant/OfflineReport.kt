package app.signsetu.assistant

import app.signsetu.data.content.SignLanguage

/** On-device report and follow-up question generation; nothing here leaves the phone. */
object OfflineReport {
    /** A draft from rules alone: counts and the subject's own answers as logged. It states nothing the log does
     * not hold, and makes no legal, medical or reliability claim. */
    fun synthesizeOfflineForensicReport(
        sessionLogText: String,
        role: String,
        regionalDialect: String,
        signLanguage: SignLanguage = SignLanguage.ISL,
    ): String {
        val lines = sessionLogText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val answers = mutableListOf<String>()
        val readings = mutableListOf<String>()
        val pain = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var questions = 0
        var drawings = 0

        // A field the log line holds, or null when it is empty or "null".
        fun String.field(key: String, before: String) = substringAfter(key).substringBefore(before).trim().takeIf { it.isNotBlank() && it != "null" }

        for (line in lines) {
            when {
                line.contains("[ANSWER_DRAWING]") -> drawings++
                ANSWERS.any { it in line } -> {
                    val value = line.field("text=", " card=") ?: line.field("card=", " lang=") ?: continue
                    // Machine and officer readings are not the subject's answers.
                    if (line.contains("[SUBJECT]")) answers += value else readings += value
                    val card = line.substringAfter("card=").substringBefore(" lang=").trim()
                    if (line.contains("[SUBJECT]") && (card.startsWith("pain_") || card in BODY_PARTS)) pain += card.removePrefix("pain_")
                }
                line.contains("[QUESTION_SHOWN]") -> questions++
                line.contains("[NOTE]") && line.contains("[OFFICER]") -> line.field("text=", " card=")?.let(notes::add)
            }
        }

        fun list(items: List<String>) = if (items.isEmpty()) "   • Not recorded" else items.joinToString("\n") { "   • $it" }
        val who = if (role.equals("ACCUSED", ignoreCase = true)) "Accused or in custody" else if (role.equals("VICTIM", ignoreCase = true)) "Victim or witness" else "Not set"

        return """
            MACHINE DRAFT SUMMARY: NOT PART OF THE RECORD
            Built on this phone from the session log by fixed rules. Check every line against the record.

            Role: $who
            Language: $regionalDialect / ${signLanguage.title}
            Items shown: $questions
            Drawings: $drawings

            Answers the subject gave:
            {answers}

            Readings by the app or the officer (not confirmed by the subject):
            {readings}

            Body areas the subject picked: ${pain.distinct().joinToString(", ").ifEmpty { "Not recorded" }}

            Officer notes:
            {notes}
        """.trimIndent()
            // Filled after trimIndent: multi-line values would otherwise change the common indent.
            .replace("{answers}", list(answers))
            .replace("{readings}", list(readings))
            .replace("{notes}", list(notes))
    }

    private val ANSWERS = listOf("ANSWER_CARD", "ANSWER_TEXT", "ANSWER_SIGNED", "ANSWER_GESTURE")
    private val BODY_PARTS = setOf("head", "arm", "chest", "stomach", "leg", "hand", "face", "throat")

    /** Gentle, non-leading follow-up questions chosen from the subject's last answer. */
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
                if (isChild) listOf("Are you safe right now?", "Can you tell me more about that?", "What happened next?")
                else listOf("When did this happen?", "Are you in immediate physical safety now?", "Can you tell us where this happened?")
            }
            lower.contains("home") || lower.contains("घर") -> {
                listOf("Can you tell me more about home?", "Is there someone you would like to be with now?", "Do you want to take a break?")
            }
            lower.contains("school") || lower.contains("स्कूल") || lower.contains("teacher") || lower.contains("sir") -> {
                if (isChild) listOf("Do you feel safe at school?", "Is there a teacher you trust?", "Can you tell me more about school?")
                else listOf("Can you tell me where this happened?", "Is there a staff member you would like us to contact?", "Can you tell us more about where this happened?")
            }
            lower.contains("touch") || lower.contains("छूना") || lower.contains("bad touch") -> {
                if (isChild) listOf("Are you safe now?", "Can you tell me more about that?", "Do you want to take a break?")
                else listOf("Can you tell me more about that?", "What happened next?", "Would you like to pause before continuing?")
            }
            lower.contains("night") || lower.contains("dark") || lower.contains("रात") || lower.contains("अँधेरा") -> {
                if (isChild) listOf("Can you tell me more about that night?", "What happened next?", "Do you want to take a break?")
                else listOf("Can you tell me more about that night?", "What happened next?", "Would you like water or a brief pause?")
            }
            isChild -> {
                listOf("Are you feeling comfortable?", "Would you like to show us on the drawing board?", "Do you want to take a short break?")
            }
            else -> {
                listOf("Are you comfortable continuing?", "Can you describe what happened next?", "Would you like water or a brief pause?")
            }
        }
    }
}
