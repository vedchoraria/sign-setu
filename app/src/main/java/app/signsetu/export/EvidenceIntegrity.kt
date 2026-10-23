package app.signsetu.export

import android.os.Build
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Session
import app.signsetu.data.session.SessionEvent
import app.signsetu.data.session.EventSeal
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

/** What re-walking a session's sealed log found: [head] is the last seal, the value the record is anchored to. */
data class ChainCheck(val head: String, val sealed: Int, val unsealed: Int, val brokenAt: Long?) {
    val intact: Boolean get() = brokenAt == null
    /** The head's first 64 bits as "a1b2-c3d4-e5f6-7a8b", for the page footer; the certificate prints it whole. */
    val short: String get() = head.take(16).chunked(4).joinToString("-")
}

/** The session's hash chain (EventSeal, sealed as each row was written) and the certificate for electronic
 * evidence under Section 63 of the Bharatiya Sakshya Adhiniyam, 2023 (formerly Section 65B of the Indian Evidence Act). */
object EvidenceIntegrity {

    /** Recomputes every seal in write order from the rows and drawings as they are now; the first row whose seal
     * no longer matches is reported. Rows from before sealing are counted and left out of the chain. */
    suspend fun verifyChain(session: Session, events: List<SessionEvent>, drawing: suspend (Long) -> ByteArray?): ChainCheck {
        var running = EventSeal.genesis(session.id, session.startedAt)
        var sealed = 0
        var unsealed = 0
        var broken: Long? = null
        for (e in events.sortedBy { it.id }) {
            val stored = e.hash
            if (stored == null) { unsealed++; continue }
            val expected = EventSeal.seal(
                running, session.id, e.ts, e.type.name, e.source.name, e.lang, e.text, e.cardId, e.lexiconId,
                e.drawingId?.let { drawing(it) }, e.itemId, e.reason, e.shownId, e.via,
            )
            if (expected != stored && broken == null) broken = e.id
            sealed++
            running = stored
        }
        return ChainCheck(running, sealed, unsealed, broken)
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
        chain: ChainCheck,
        recordDigest: String,
        signLanguage: SignLanguage = SignLanguage.ISL,
        storage: String = "SQLCipher AES-256; database key wrapped by an Android Keystore key",
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
            • Producing Officer        : ____________________ (Rank: __________)
            • Police Station / Unit    : ____________________
            • Operating Hardware Device: $deviceModel
            • Storage                  : $storage

            2. STATEMENT OF PROPER OPERATION (SECTION 63(2) BSA 2023):
            I, the undersigned police officer, do hereby certify and affirm:
            (a) The mobile application 'Sign Setu' was operating properly throughout the inquiry.
            (b) The said electronic device was under my lawful custody and direct personal control
                during the entire duration of the communication recording.
            (c) During the ordinary course of the inquiry, the device was used to display picture cards,
                sign language character demonstrations and captions, and to record the subject's touch
                selections, drawings and signs.
            (d) To the best of my knowledge, the electronic record was not altered; the hash chain below
                was sealed as each entry was written and was re-checked when this report was produced.

            3. FORENSIC RECORD METRICS:
            • Total Recorded Events    : ${metrics.totalEvents}
            • Items Shown              : ${metrics.questionCount}
            • Subject Direct Answers   : ${metrics.answerCount} (Affirmative: ${metrics.affirmativeCount}, Negative: ${metrics.negativeCount})
            • Graphic Exhibits/Drawings: ${metrics.drawingCount}
            • Signed Answers          : ${metrics.signedCount}
            • Active Session Duration  : ${metrics.durationSeconds} seconds

            4. CRYPTOGRAPHIC SEAL & CHAIN OF CUSTODY INTEGRITY:
            • Chain Head (SHA-256)     : ${chain.head}
            • Chain Status             : ${chainStatus(chain)}
            • PDF Record Digest        : $recordDigest (first 64 bits of SHA-256 over the printed rows)

            5. STATUTORY SOLEMN AFFIRMATION (SECTION 63(4) BSA 2023):
            I hereby certify that this electronic record is a true reproduction of the entries made
            on the aforesaid device. It is a communication record, not a statement; readings by the
            app or the officer and any machine draft are marked as such and are not the subject's words.

            Date: $certDate
            Station: ____________________

            ____________________________________________
            (Signature & Seal of Investigating Officer)
            Name: ____________________
            Rank: ____________________
            ================================================================================
        """.trimIndent()
    }

    private fun chainStatus(chain: ChainCheck): String {
        val base = if (chain.intact) "Intact: ${chain.sealed} sealed entries re-checked" else "BROKEN: entry ${chain.brokenAt} or one before it was altered"
        return if (chain.unsealed > 0) "$base; ${chain.unsealed} entries predate sealing and are not covered" else base
    }
}
