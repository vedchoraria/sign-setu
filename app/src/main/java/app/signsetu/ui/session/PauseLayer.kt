package app.signsetu.ui.session

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.session.SessionEvent
import kotlin.random.Random

/** Answers after which the subject gets a calm pause that only they end. */
internal val PauseCards = setOf("stop", "break")

/** How long the pause has lasted, from the subject's last Stop or Break; null when there was none. */
internal fun pauseSeconds(events: List<SessionEvent>, now: Long): Long? =
    events.lastOrNull { it.cardId in PauseCards }?.let { ((now - it.ts) / 1000).coerceAtLeast(0) }

/** A quiet night sky over the character: a circle that breathes in and out over four seconds and a few bubbles
 * to pop. No score and no sound. */
@Composable
internal fun PauseLayer(label: String?, onReady: () -> Unit, modifier: Modifier = Modifier, stop: (@Composable (Modifier) -> Unit)? = null) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val motion = rememberInfiniteTransition(label = "pause")
    val breath by motion.animateFloat(0f, if (still) 0f else 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "breath")
    val drift by motion.animateFloat(0f, if (still) 0f else 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "drift")
    // x and phase of each bubble, both 0..1; a popped bubble is replaced by a new one lower down.
    val bubbles = remember { mutableStateListOf<Offset>().apply { repeat(7) { add(Offset(Random.nextFloat(), Random.nextFloat())) } } }
    fun at(b: Offset, w: Float, h: Float) = Offset(w * (0.08f + 0.84f * b.x), h * (1f - (b.y + drift) % 1f))
    val ready = stringResource(R.string.pause_ready)

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { tap ->
                    val r = size.width * 0.06f
                    val hit = bubbles.indexOfFirst { (at(it, size.width.toFloat(), size.height.toFloat()) - tap).getDistance() < r * 1.4f }
                    if (hit >= 0) bubbles[hit] = Offset(Random.nextFloat(), (1.05f - drift) % 1f)
                }
            },
        ) {
            drawRect(Brush.verticalGradient(listOf(Color(0xEB0D1B3E), Color(0xEB1E3A6E))))
            val centre = Offset(size.width / 2f, size.height * 0.42f)
            val radius = size.minDimension * (0.16f + 0.1f * breath)
            drawCircle(Color(0x3384C5FF), radius * 1.35f, centre)
            drawCircle(Color(0x6684C5FF), radius, centre)
            bubbles.forEach { b -> drawCircle(Color(0x66FFFFFF), size.width * 0.06f, at(b, size.width, size.height)) }
        }
        Surface(
            onClick = onReady,
            shape = RoundedCornerShape(50),
            color = Color(0xFF1F7F54),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .height(96.dp)
                .widthIn(min = 96.dp)
                .semantics { contentDescription = ready },
        ) {
            Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(40.dp))
                label?.let { Text(it, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp)) }
            }
        }
        stop?.invoke(Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp).width(180.dp))
    }
}
