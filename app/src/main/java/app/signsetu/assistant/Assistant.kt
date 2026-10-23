package app.signsetu.assistant

import androidx.compose.runtime.Immutable
import app.signsetu.data.content.Language
import app.signsetu.data.content.isDevanagari

/** A machine-made caption and what it says back in English, so the officer can see a wrong translation before Send. */
@Immutable
data class Suggestion(val caption: String, val meaning: String)

/** The on-device assistant. It only translates the officer's own words into the subject's language and back; it
 * never answers, judges or adds anything, and the officer decides whether the caption is used. */
class Assistant(private val models: ModelStore, private val cloud: CloudAi? = null) {
    suspend fun suggest(text: String, language: Language): Suggestion? {
        if (!needsTranslation(text, language.code)) return null
        val local = models.use { model ->
            val caption = model.chat(toLanguage(language), text, 120).firstLine() ?: return@use null
            val meaning = model.chat(BACK_TO_ENGLISH, caption, 120).firstLine() ?: return@use null
            Suggestion(caption, meaning)
        }
        if (local != null) return local
        // Only the officer's own sentence goes out; it holds no subject details.
        val caption = cloud?.ask(toLanguage(language), text, emptyList(), 200)?.firstLine() ?: return null
        val meaning = cloud.ask(BACK_TO_ENGLISH, caption, emptyList(), 120)?.firstLine() ?: text
        return Suggestion(caption, meaning)
    }

    private fun toLanguage(language: Language) = translatePrompt(language)

    private fun String.firstLine() = lineSequence().map { it.trim().trim('"') }.firstOrNull { it.isNotEmpty() }

    private companion object {
        const val BACK_TO_ENGLISH = BACK_TO_ENGLISH_PROMPT
    }
}

/** The gateway refuses a system prompt that does not open as the ones in this file and CloudAi.kt do (assist_prompt_openings). */
internal fun translatePrompt(language: Language) =
    "Translate the sentence into ${language.english} (${language.name}), in its own script. Keep the exact meaning, " +
        "tense and who does what. Use simple words for a child. Reply with the translated sentence only."

internal const val BACK_TO_ENGLISH_PROMPT = "Translate this sentence into English. Reply with the English only."

/** English needs no translation, and nor does Hindi already typed in Devanagari. */
fun needsTranslation(text: String, langCode: String): Boolean =
    langCode != "en" && !(langCode == "hi" && text.isDevanagari())
