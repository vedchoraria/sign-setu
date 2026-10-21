package app.signsetu.export

import android.os.Build
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Counts of what a session recorded, for the certificate. */
data class SessionMetrics(
    val sessionId: String,
    val startedAt: Long,
    val totalEvents: Int,
    val questionCount: Int,
    val answerCount: Int,
    val drawingCount: Int,
    val signedCount: Int,
    val affirmativeCount: Int,
    val negativeCount: Int,
    val unclearOrStopCount: Int,
    val durationSeconds: Long,
)

/**
 * A SHA-256 hash chain over the session timeline, and the certificate for electronic evidence under Section 63 of the
 * Bharatiya Sakshya Adhiniyam, 2023 (formerly Section 65B of the Indian Evidence Act, 1872).
 */
object EvidenceIntegrity {

    private fun sha256Hex(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).toHexString()

    /**
     * H0 hashes the session's id, start, role and language; each event in time order hashes the previous hash with its
     * own fields. The last hash's first 64 bits, as "a1b2-c3d4-e5f6-7a8b", seal the record.
     */
    fun computeChainHash(session: Session, events: List<SessionEvent>): String {
        val genesis = "GENESIS|${session.id}|${session.startedAt}|${session.role.name}|${session.subjectLang}"
        var currentHash = sha256Hex(genesis.toByteArray(Charsets.UTF_8))
        for (event in events.sortedWith(compareBy({ it.ts }, { it.id }))) {
            val payload = listOf(
                currentHash,
                event.id.toString(),
                event.ts.toString(),
                event.type.name,
                event.source.name,
                event.lang.orEmpty(),
                event.text.orEmpty(),
                event.cardId.orEmpty(),
                (event.drawingId ?: 0L).toString(),
                event.itemId.orEmpty(),
                (event.shownId ?: 0L).toString(),
                event.via.orEmpty(),
            ).joinToString("|")
            currentHash = sha256Hex(payload.toByteArray(Charsets.UTF_8))
        }

        return currentHash.take(16).chunked(4).joinToString("-")
    }

    /** The session runs from its start to its latest event, or to [now] while it has none. */
    fun computeSessionMetrics(session: Session, events: List<SessionEvent>, now: Long = System.currentTimeMillis()): SessionMetrics {
        val end = events.maxOfOrNull { it.ts } ?: now
        val duration = (end - session.startedAt).coerceAtLeast(0L) / 1000L

        var questions = 0
        var answers = 0
        var drawings = 0
        var signed = 0
        var yesCount = 0
        var noCount = 0
        var unclearOrStop = 0

        for (e in events) {
            when (e.type) {
                EventType.QUESTION_SHOWN -> questions++
                EventType.ANSWER_CARD, EventType.ANSWER_DRAWING, EventType.ANSWER_GESTURE,
                EventType.ANSWER_TEXT, EventType.ANSWER_SIGNED, EventType.ANSWER_UNCLEAR -> {
                    answers++
                    when (e.cardId) {
                        "yes", "c_yes" -> yesCount++
                        "no", "c_no" -> noCount++
                        "stop", "dont_know", "unclear", "dont_understand" -> unclearOrStop++
                        else -> if (e.type == EventType.ANSWER_UNCLEAR) unclearOrStop++
                    }
                    if (e.type == EventType.ANSWER_DRAWING || e.drawingId != null) drawings++
                    if (e.type == EventType.ANSWER_SIGNED) signed++
                }
                else -> {}
            }
        }

        return SessionMetrics(
            sessionId = session.id,
            startedAt = session.startedAt,
            totalEvents = events.size,
            questionCount = questions,
            answerCount = answers,
            drawingCount = drawings,
            signedCount = signed,
            affirmativeCount = yesCount,
            negativeCount = noCount,
            unclearOrStopCount = unclearOrStop,
            durationSeconds = duration,
        )
    }

    /** The certificate's text; the officer signs the printed copy. */
    fun generateSection63Certificate(
        session: Session,
        events: List<SessionEvent>,
        chainDigest: String,
        recordDigest: String,
        signLanguage: SignLanguage = SignLanguage.ISL,
        officerName: String = "Investigating Officer",
        officerRank: String = "Sub-Inspector / Inspector of Police",
        policeStation: String = "Designated Police Station",
        deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
    ): String {
        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss z", Locale.ENGLISH)
        val sessionStartDate = df.format(Date(session.startedAt))
        val certDate = df.format(Date())
        val metrics = computeSessionMetrics(session, events)

        return """
            ================================================================================
            CERTIFICATE OF ADMISSIBILITY OF ELECTRONIC EVIDENCE
            [Under Section 63 of Bharatiya Sakshya Adhiniyam, 2023 (BSA)]
            [Formerly Section 65B of the Indian Evidence Act, 1872]
            ================================================================================

            1. CASE & SESSION PARTICULARS:
            • Electronic Session ID   : ${session.id}
            • Session Started At       : $sessionStartDate
            • Subject Communication    : ${session.subjectLang.uppercase()} / ${signLanguage.title}
            • Inquiry Role             : ${session.role.name}
            • Producing Officer        : $officerName ($officerRank)
            • Police Station / Unit    : $policeStation
            • Operating Hardware Device: $deviceModel
            • Storage & Vault Standard : SQLCipher AES-256 Hardware-backed Keystore Encryption

            2. STATEMENT OF PROPER OPERATION (SECTION 63(2) BSA 2023):
            I, the undersigned police officer, do hereby certify and affirm:
            (a) The mobile application 'Sign Setu' was operating properly throughout the inquiry.
            (b) The said electronic device was under my lawful custody and direct personal control
                during the entire duration of the communication recording.
            (c) During the ordinary course of the inquiry, the device was used to display structured,
                non-leading Augmentative and Alternative Communication (AAC) cards, sign language
                avatar demonstrations, and to record the subject's touch selections, drawings and signs.
            (d) No unauthorized interception, electronic alteration, file tampering, or data substitution
                occurred during or subsequent to the production of this electronic record.

            3. FORENSIC RECORD METRICS:
            • Total Recorded Events    : ${metrics.totalEvents}
            • Non-Leading Inquiries    : ${metrics.questionCount}
            • Subject Direct Answers   : ${metrics.answerCount} (Affirmative: ${metrics.affirmativeCount}, Negative: ${metrics.negativeCount})
            • Graphic Exhibits/Drawings: ${metrics.drawingCount}
            • Signed Answers          : ${metrics.signedCount}
            • Active Session Duration  : ${metrics.durationSeconds} seconds

            4. CRYPTOGRAPHIC SEAL & CHAIN OF CUSTODY INTEGRITY:
            • Terminal Chained Hash    : $chainDigest (SHA-256 Backward Chained Digest)
            • PDF Record Digest        : $recordDigest (SHA-256 Export Integrity Digest)
            • Evidentiary Chain Status : Hash-chained; verify by recomputing both digests above

            5. STATUTORY SOLEMN AFFIRMATION (SECTION 63(4) BSA 2023):
            I hereby certify that the contents of this electronic record and accompanying report
            are a true, accurate, and unedited representation of the statements and responses
            provided by the subject on the aforesaid device.

            Date: $certDate
            Station: $policeStation

            ____________________________________________
            (Signature & Seal of Investigating Officer)
            Name: $officerName
            Rank: $officerRank
            ================================================================================
        """.trimIndent()
    }
}
