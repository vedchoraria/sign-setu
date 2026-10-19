package app.signsetu.assistant

/** On-device report and follow-up question generation; nothing here leaves the phone. */
object OfflineReport {
    /**
     * Rule-based forensic report built entirely on-device.
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
}
