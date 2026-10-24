package app.signsetu.ui.avatar

import app.signsetu.data.content.Gesture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** The character's head in a circle, for profile tiles and headers. */
@Composable
fun MiniFace(
    character: Character,
    size: Dp,
    background: Color,
    modifier: Modifier = Modifier,
    gesture: Gesture? = null,
    atSeconds: Float? = null,
) {
    // The head sits at (100, ~76) of the 200-unit canvas; a canvas 2.3x the circle frames it.
    val canvas = size * 2.3f
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Avatar(
            character = character,
            gesture = gesture,
            playKey = null,
            idle = false,
            atSeconds = atSeconds,
            view = AvatarView.Head,
            modifier = Modifier.requiredSize(canvas).offset(y = canvas * (if (character == Character.CHILD) 0.12f else 0.15f)),
        )
    }
}
