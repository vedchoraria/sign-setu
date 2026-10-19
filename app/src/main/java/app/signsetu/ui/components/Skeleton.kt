package app.signsetu.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding

/** A placeholder block with a moving highlight. The phase is read at draw time, so it never recomposes. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerLowest
    val phase = rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    return background(base).drawWithContent {
        val x = size.width * phase.value
        drawRect(Brush.horizontalGradient(listOf(base, light, base), startX = x - size.width / 2, endX = x + size.width / 2))
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Int = 56) {
    Box(modifier.fillMaxWidth().height(height.dp).clipRounded().shimmer())
}

private fun Modifier.clipRounded() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

/** Shown while content or the database opens, in the shape of the start screen. */
@Composable
fun ScreenSkeleton() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(20.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth(0.5f), height = 32)
        Spacer(Modifier.height(24.dp))
        SkeletonBlock(height = 120)
        repeat(3) {
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(height = 72)
        }
    }
}
