package app.signsetu.pair

import app.signsetu.data.content.SignMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Written answers are capped on the stage; the officer phone never truncates what arrives. */
const val TEXT_CAP = 2_000
/** Raw PNG bytes; base64 and JSON around it stay under the 1 MiB frame cap. */
const val DRAWING_CAP = 600_000

internal val WireJson = Json { classDiscriminator = "t"; ignoreUnknownKeys = false; encodeDefaults = true }

/** Only IDs travel; each side resolves them against its own verified content. */
@Serializable
sealed interface WirePending {
    @Serializable @SerialName("ask") data class Ask(val questionId: String) : WirePending
    @Serializable @SerialName("text") data class FreeText(val text: String, val cardIds: List<String>, val modes: Set<SignMode>, val caption: String? = null, val meant: String? = null) : WirePending
    @Serializable @SerialName("cards") data class Cards(val cardIds: List<String>) : WirePending
    @Serializable @SerialName("back") data class ShowBack(val lexiconId: String, val choices: List<String> = emptyList()) : WirePending
    @Serializable @SerialName("phrase") data class PhraseAsk(val phraseId: String, val slotCard: String) : WirePending
    @Serializable @SerialName("lang") data object LanguageFinder : WirePending
    @Serializable @SerialName("draw") data object Drawing : WirePending
    @Serializable @SerialName("rules") data class Rules(val step: Int) : WirePending
}

/** Only what the stage reads; what the subject already answered stays with the stage, so an echo never reverts a tap. */
@Serializable
data class StageView(
    val lang: String,
    val profile: String,
    val live: Boolean,
    val item: WirePending?,
    val rotated: Boolean,
    val largeCaptions: Boolean,
    val gestureSpeed: Float,
    val cameraOn: Boolean,
    val signLanguage: String = "ISL",
    val child: Boolean? = null,
)

@Serializable
sealed interface ToStage {
    @Serializable @SerialName("confirm") data object Confirm : ToStage
    @Serializable @SerialName("show") data class Show(val rev: Long, val itemId: Long?, val view: StageView) : ToStage
    @Serializable @SerialName("ack") data class Ack(val itemId: Long, val n: Int) : ToStage
    @Serializable @SerialName("hold") data object Hold : ToStage
    @Serializable @SerialName("ping") data object Ping : ToStage
    @Serializable @SerialName("bye") data class Bye(val reason: String) : ToStage
}

@Serializable
sealed interface AnswerBody {
    @Serializable @SerialName("card") data class Card(val id: String, val cue: String? = null) : AnswerBody
    /** The subject moves the opening on; it is not an answer and is not logged. */
    @Serializable @SerialName("next") data object Next : AnswerBody
    @Serializable @SerialName("yesno") data class YesNo(val yes: Boolean, val cue: String? = null) : AnswerBody
    @Serializable @SerialName("lang") data class Language(val code: String) : AnswerBody
    @Serializable @SerialName("text") data class Text(val text: String) : AnswerBody
    @Serializable @SerialName("draw") data class Drawing(val png: String) : AnswerBody
    /** The subject changed a lesson answer before confirming it; the earlier entry is dropped. */
    @Serializable @SerialName("change") data object Change : AnswerBody
    /** A lesson-sheet answer or phrase; [method] is a Method name and the officer's phone re-checks it. */
    @Serializable @SerialName("given") data class Given(val question: String, val method: String, val confirmed: Boolean, val value: String = "", val picks: List<String> = emptyList()) : AnswerBody
    /** Transcribed sign language answer from stage device camera, with optional skeleton landmark evidence PNG. */
    @Serializable @SerialName("signed") data class Signed(val words: List<String>, val skeletonPng: String? = null) : AnswerBody
}

@Serializable
sealed interface ToOfficer {
    @Serializable @SerialName("confirm") data object Confirm : ToOfficer
    @Serializable @SerialName("displayed") data class Displayed(val itemId: Long) : ToOfficer
    /** [late] marks an answer given while the link was down, or resent after a resume. */
    @Serializable @SerialName("answer") data class Answer(val itemId: Long, val n: Int, val body: AnswerBody, val late: Boolean = false) : ToOfficer
    @Serializable @SerialName("status") data class Status(val battery: Int, val charging: Boolean, val pinned: Boolean, val cameraOn: Boolean, val error: String? = null) : ToOfficer
    @Serializable @SerialName("ping") data object Ping : ToOfficer
    @Serializable @SerialName("bye") data class Bye(val reason: String) : ToOfficer
}

object ByeReason {
    const val END = "end"
    const val CANCEL = "cancel"
    const val FALLBACK = "fallback"
    const val EXIT = "exit"
    const val UNPINNED = "unpinned"
}

/** Sender-side check; an answer that fails it is never sent (the stage caps input before this). */
fun AnswerBody.fits(): Boolean = when (this) {
    is AnswerBody.Text -> text.length <= TEXT_CAP
    is AnswerBody.Drawing -> png.length <= (DRAWING_CAP + 2) / 3 * 4
    is AnswerBody.Given -> value.length <= TEXT_CAP && picks.size <= 4 && picks.all { it.length <= 64 }
    is AnswerBody.Signed -> words.joinToString(" ").length <= TEXT_CAP && (skeletonPng == null || skeletonPng.length <= (DRAWING_CAP + 2) / 3 * 4)
    else -> true
}

/** A drawing from the link is refused on its declared size, before any pixels are decoded. */
fun drawingBoundsOk(width: Int, height: Int) = width in 1..4096 && height in 1..4096

fun encodeToStage(m: ToStage): ByteArray = WireJson.encodeToString(ToStage.serializer(), m).toByteArray()
fun encodeToOfficer(m: ToOfficer): ByteArray = WireJson.encodeToString(ToOfficer.serializer(), m).toByteArray()
fun decodeToStage(b: ByteArray): ToStage = WireJson.decodeFromString(ToStage.serializer(), b.decodeToString())
fun decodeToOfficer(b: ByteArray): ToOfficer = WireJson.decodeFromString(ToOfficer.serializer(), b.decodeToString())
