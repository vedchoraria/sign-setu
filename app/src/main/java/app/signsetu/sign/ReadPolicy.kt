package app.signsetu.sign

import app.signsetu.camera.LandmarkFrame
import app.signsetu.data.content.WordEntry

/** Share of a sign's frames flagged blurry above which the picture is too smeared to read. */
const val BLUR_SHARE = 0.5f

/** Median gap between analysed frames above which the rate is too low for the reader: under about 8 frames a second. */
const val LOW_RATE_GAP_MS = 125L

/** Whether the pictures behind a sign were too poor to read, whatever the hands did: smeared, or analysed too
 * rarely. A sign this poor is handled like fast hands, by asking the subject to sign slower. */
internal fun poorPicture(frames: List<LandmarkFrame>): Boolean {
    if (frames.size < 2) return false
    val gaps = frames.zipWithNext { a, b -> b.timestampMs - a.timestampMs }.sorted()
    return frames.count { it.blurry } > frames.size * BLUR_SHARE || gaps[gaps.size / 2] > LOW_RATE_GAP_MS
}

/** The one place a read becomes something the subject is shown: the reader's guesses [all] (best first), a
 * matched home sign, and the item on screen [cards] go in. */
fun decideRead(
    frames: List<LandmarkFrame>,
    all: List<SignGuess>,
    home: SignGuess?,
    cards: Set<String>,
    signWords: Map<String, String>,
    words: Map<String, WordEntry>,
    extra: Map<String, List<String>> = emptyMap(),
): SignSegment {
    // Two or more cards are the answers to choose from, so they are searched for at any score; one card only
    // illustrates the question, and then just the top three are reordered. The subject still confirms.
    val pool = if (cards.size >= 2) all else all.take(3)
    val guesses = (listOfNotNull(home) + inContext(pool, cards, signWords, words, extra)).distinctBy { it.word }.take(3)
    return SignSegment(frames, guesses, needsSlower(frames, all), cards)
}

/** The signs of one window as words, one per sign: each sign's best guess for the item on screen, "?" where none was read. */
fun readSigns(signs: List<List<LandmarkFrame>>, reader: SignReader?, cards: Set<String>, words: Map<String, WordEntry>): List<String> =
    signs.map { sign ->
        val all = reader?.let { r -> runCatching { r.read(sign) }.getOrNull() }.orEmpty().sortedByDescending { it.score }
        decideRead(sign, all, null, cards, reader?.words.orEmpty(), words, reader?.cards.orEmpty()).word ?: "?"
    }
