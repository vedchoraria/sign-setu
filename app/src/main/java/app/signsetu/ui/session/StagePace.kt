package app.signsetu.ui.session

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import app.signsetu.data.content.WordBeat
import app.signsetu.data.session.Profile

/** Signing speeds the subject can pick on the stage. */
internal val Paces = listOf(0.5f, 0.75f, 1f)

internal fun nextPace(pace: Float): Float = Paces.firstOrNull { it > pace + 0.01f } ?: Paces.first()

/** A young child starts slow; everyone else starts at the officer's setting. */
internal fun initialPace(profile: Profile, setting: Float): Float =
    if (profile == Profile.YOUNG_CHILD) minOf(setting, 0.75f) else setting

internal fun beatAt(beats: List<WordBeat>, offset: Int): WordBeat? = beats.firstOrNull { offset in it.range }

/**
 * Stage choices that last for one session, in memory only: the subject's signing speed and whether the glance strip
 * shows. It starts hidden, since upside-down text confuses a subject; the officer turns it on from the menu.
 */
internal object StagePace {
    private var session: String? = null
    val speed = mutableFloatStateOf(1f)
    val glanceHidden = mutableStateOf(true)

    fun forSession(id: String, initial: Float) {
        if (session == id) return
        session = id
        speed.floatValue = initial
        glanceHidden.value = true
    }
}
