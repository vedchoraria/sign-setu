package app.signsetu.ui.pair

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import app.signsetu.R
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.session.LINK_FALLBACK
import app.signsetu.ui.session.SYSTEM_BREAK_GIVEN

/** LINK rows hold a machine key, then any detail (model, build, address hash) after a space. */
object LinkKey {
    const val CONNECTED = "connected"
    const val CODE_VERIFIED = "code_verified"
    const val LOST = "lost"
    const val RESUMED = "resumed"
    const val HOLD = "hold"
    const val UNPINNED = "unpinned"
    const val DISPLAY_NOT_CONFIRMED = "display_not_confirmed"
    const val ENDED = "ended"
}

private val labels = mapOf(
    LinkKey.CONNECTED to R.string.link_connected,
    LinkKey.CODE_VERIFIED to R.string.link_code_verified,
    LinkKey.LOST to R.string.link_lost,
    LinkKey.RESUMED to R.string.link_resumed,
    LinkKey.HOLD to R.string.link_hold,
    LinkKey.UNPINNED to R.string.link_unpinned,
    LinkKey.DISPLAY_NOT_CONFIRMED to R.string.link_display_not_confirmed,
    LINK_FALLBACK to R.string.link_fallback,
    LinkKey.ENDED to R.string.link_ended,
)

/** The officer-language line for a LINK row or a break, or null for any other event. */
fun linkText(res: Resources, e: SessionEvent): String? = when {
    e.type == EventType.SYSTEM && e.text == SYSTEM_BREAK_GIVEN -> res.getString(R.string.log_break_given)
    e.type != EventType.LINK -> null
    else -> {
        val key = e.text.orEmpty().substringBefore(' ')
        val detail = e.text.orEmpty().substringAfter(' ', "")
        val label = labels[key]?.let(res::getString) ?: key
        if (detail.isEmpty()) label else "$label · $detail"
    }
}

@Composable
fun linkText(e: SessionEvent): String? = linkText(LocalResources.current, e)
