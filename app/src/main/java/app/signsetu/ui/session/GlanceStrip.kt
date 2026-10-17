package app.signsetu.ui.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.givenOf
import app.signsetu.data.session.EventType
import app.signsetu.data.session.SessionEvent

private const val GLANCE_MAX = 32

/** Picked answers only: a machine reading is unconfirmed, and free writing may hold a name whatever was asked. */
private val GlanceAnswers = setOf(EventType.ANSWER_CARD, EventType.ANSWER_GESTURE)

/** Answers that ask the officer to stop and look: three short pulses. */
private val AttentionCards = setOf("stop", "break", "dont_know", "dont_understand", "wrong")

/**
 * "<last question> → <latest answer to it>", in the officer's language, or null before anything was shown.
 * Bystanders can read the strip, so a personal detail ([isPersonal] item id) never appears on it.
 */
internal fun glanceText(events: List<SessionEvent>, isPersonal: (String) -> Boolean): String? {
    val shown = events.indexOfLast { it.type == EventType.QUESTION_SHOWN }
    if (shown < 0) return null
    // A say-back carries the reading itself in its text, so one about a personal detail hides the whole strip.
    val checked = refOf(events[shown])?.let { ref -> events.firstOrNull { it.id == ref } }
    val askedFor = (checked?.let { c -> events.firstOrNull { it.id == c.shownId } } ?: events[shown]).itemId
    if (askedFor != null && isPersonal(askedFor)) return if (checked != null) null else questionLine(events[shown])
    val question = questionLine(events[shown])
    val answer = events.drop(shown + 1).lastOrNull { it.type in GlanceAnswers && givenOf(it.reason) == null }?.text ?: return question
    return "$question → ${answer.take(GLANCE_MAX).uppercase()}"
}

private fun questionLine(e: SessionEvent) =
    e.text.orEmpty().lineSequence().first().let { if (it.length > GLANCE_MAX) it.take(GLANCE_MAX - 1) + "…" else it }

/** Vibration felt by whoever holds the phone: one long for Yes, two for No, three short for a request, a tick otherwise. */
internal fun buzzPattern(answeredYes: Boolean?, answeredCard: String?): LongArray? = when {
    answeredYes == true -> longArrayOf(0, 400)
    answeredYes == false -> longArrayOf(0, 120, 120, 120)
    answeredCard in AttentionCards -> longArrayOf(0, 80, 100, 80, 100, 80)
    answeredCard != null -> longArrayOf(0, 30)
    else -> null
}

internal fun buzz(context: Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

/**
 * A thin band at the stage edge that faces the officer, drawn upside down so the officer sitting opposite can read
 * the last question and answer without taking the phone back. A long press hides it for the rest of the session.
 */
@Composable
internal fun GlanceStrip(text: String, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.glance_label)
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .graphicsLayer { rotationZ = 180f }
            .background(Color(0xCC111111))
            .combinedClickable(onClickLabel = null, onLongClickLabel = stringResource(R.string.glance_hide), onLongClick = onHide, onClick = {})
            .semantics { contentDescription = "$label: $text" }
            .padding(horizontal = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics { })
    }
}
