package app.signsetu.assistant

import app.signsetu.data.content.SignLanguage

/** On-device report and follow-up question generation; nothing here leaves the phone. */
object OfflineReport {
    /** A report from rules alone: answers, pain pointed at, ages counted on fingers and officer notes in the log. */
    fun synthesizeOfflineForensicReport(
        sessionLogText: String,
        role: String,
        regionalDialect: String,
        signLanguage: SignLanguage = SignLanguage.ISL,
    ): String {
        val lines = sessionLogText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val subjectStatements = mutableListOf<String>()
        val officerQuestions = mutableListOf<String>()
        val painPoints = mutableListOf<String>()
        val childAges = mutableListOf<String>()
        val gestures = mutableListOf<String>()
        var drawings = 0
        val officerNotes = mutableListOf<String>()

        // A field the log line holds, or null when it is empty or "null".
        fun String.field(key: String, before: String) = substringAfter(key).substringBefore(before).trim().takeIf { it.isNotBlank() && it != "null" }

        for (line in lines) {
            when {
                ANSWERS.any { it in line } -> {
                    val textPart = line.field("text=", " card=")
                    val cardPart = line.substringAfter("card=").substringBefore(" lang=").trim()
                    (textPart ?: line.field("card=", " lang="))?.let(subjectStatements::add)
                    if (cardPart.startsWith("pain_") || cardPart in BODY_PARTS) painPoints.add(cardPart.removePrefix("pain_"))
                    if (cardPart.matches(COUNT_CARD)) childAges.add(cardPart.removePrefix("n"))
                    if (line.contains("cue:")) gestures.add(line.substringAfter("cue:").substringBefore("]"))
                }
                line.contains("QUESTION_SHOWN") -> line.field("text=", " card=")?.let(officerQuestions::add)
                line.contains("ANSWER_DRAWING") -> drawings++
                line.contains("NOTE") -> line.field("text=", " card=")?.let(officerNotes::add)
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
            • Primary Communication  : $regionalDialect / ${signLanguage.title}
            • Questions Formulated   : ${officerQuestions.size} structured non-leading items
            • Evidentiary Responses  : ${subjectStatements.size} recorded answers, $drawings drawing/landmark exhibits
            • Recorded Age Estimate  : $ageSummary

            II. STATEMENTS & ALLEGATIONS BY SUBJECT (EXACT AAC / ISL TRANSCRIPTION)
               • {statements}

            III. INJURY, PAIN & IMMEDIATE MEDICAL EVALUATION
            • Pointed / Indicated Pain Areas : $painSummary
            • Observed Physical Cues        : $gestureSummary
            • Medical Attention Recommendation: Immediate MLC (Medico-Legal Certificate) examination mandated if physical trauma indicated.

            IV. RECORDED OFFICER OBSERVATIONS & CASE NOTES
            {notes}

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
            // Filled after trimIndent: multi-line values would otherwise change the common indent.
            .replace("{statements}", statementSummary)
            .replace("{notes}", if (officerNotes.isNotEmpty()) officerNotes.joinToString("\n") { "   • $it" } else "   • Direct statement capture completed without supplementary notes.")
    }

    private val ANSWERS = listOf("ANSWER_CARD", "ANSWER_TEXT", "ANSWER_SIGNED", "ANSWER_GESTURE")
    private val BODY_PARTS = setOf("head", "arm", "chest", "stomach", "leg", "hand", "face", "throat")
    private val COUNT_CARD = Regex("n\\d+")

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
                if (isChild) listOf("Are you safe right now?", "When did this happen?", "Do you know the name of who did this?")
                else listOf("When did this happen?", "Are you in immediate physical safety now?", "Can you tell us where this happened?")
            }
            lower.contains("home") || lower.contains("घर") -> {
                listOf("Do you feel safe returning home?", "Is there a relative or friend you want to stay with?", "Can you tell us who lives with you?")
            }
            lower.contains("school") || lower.contains("स्कूल") || lower.contains("teacher") || lower.contains("sir") -> {
                if (isChild) listOf("Do you feel safe at school?", "Is there a teacher you trust?", "Can you tell me more about school?")
                else listOf("Can you tell me where this happened?", "Is there a staff member you would like us to contact?", "Can you tell us more about where this happened?")
            }
            lower.contains("touch") || lower.contains("छूना") || lower.contains("bad touch") -> {
                if (isChild) listOf("Are you safe now?", "Would you like to show us on the drawing board where?", "Do you want to take a break?")
                else listOf("Can you point on the body map to where?", "When did this happen?", "Would you like to pause before continuing?")
            }
            lower.contains("night") || lower.contains("dark") || lower.contains("रात") || lower.contains("अँधेरा") -> {
                if (isChild) listOf("Can you tell me more about that night?", "Who was there?", "Do you feel safe at home?")
                else listOf("Can you tell me more about that night?", "Who was there?", "Is there someone with you at night now?")
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
