package app.signsetu.sign

import androidx.compose.runtime.Immutable
import app.signsetu.camera.LandmarkFrame

/**
 * Reads one isolated sign from the landmark frames of one segment (D-25). Runs on device; frames are not kept.
 * Guesses are suggestions only: a meaning is recorded when the officer accepts it or the subject confirms it (FR-66).
 */
interface SignReader : AutoCloseable {
    /** The vocabulary the reader knows, as words (lower case, English), in model output order. */
    val labels: List<String>
    /** Labels to the app's written words (assets/content/sign_words.json), where the two share a concept. */
    val words: Map<String, String> get() = emptyMap()
    /** Labels to more picture cards they answer than their word's own, e.g. "police" for a police station. */
    val cards: Map<String, List<String>> get() = emptyMap()
    /** Every label with its probability, highest first; empty when the frames cannot be read. */
    fun read(frames: List<LandmarkFrame>): List<SignGuess>
    override fun close() {}
}

/** One candidate word with the model's probability, highest first. */
@Immutable
data class SignGuess(val word: String, val score: Float, val spelled: Boolean = false)
