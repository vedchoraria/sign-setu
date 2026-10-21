package app.signsetu.ui.session

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import kotlinx.coroutines.delay

/** Kept apart from the stage, so the clock's tick and the live dot's pulse redraw only this bar. */
@Composable
internal fun CallHeader(onBreathe: () -> Unit, modifier: Modifier) {
    var callSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            callSeconds++
        }
    }
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Surface(
        color = Color(0xCC0F172A),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                // The end stays clear for the officer's handle.
                .padding(start = 16.dp, end = 64.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color(0xFF10B981), modifier = Modifier.size(12.dp)) {}
                    Surface(shape = CircleShape, color = Color(0xFF10B981).copy(alpha = pulseAlpha), modifier = Modifier.size(20.dp)) {}
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                    }
                    Text(
                        text = "%02d:%02d".format(callSeconds / 60, callSeconds % 60),
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E3A8A).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                modifier = Modifier.clickable(onClick = onBreathe),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = "🫁", fontSize = 12.sp)
                    Text(
                        text = stringResource(R.string.call_breathe),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF93C5FD),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CalmingBreathingGuide(onDismiss: () -> Unit) {
    val breathScale by rememberInfiniteTransition(label = "breathCycle").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFA0F172A),
        border = BorderStroke(2.dp, Color(0xFF38BDF8)),
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(0.92f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.call_breath_title),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF334155),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(vertical = 8.dp),
            ) {
                Surface(shape = CircleShape, color = Color(0xFF0284C7).copy(alpha = 0.25f), modifier = Modifier.size(150.dp * breathScale)) {}
                Surface(shape = CircleShape, color = Color(0xFF38BDF8).copy(alpha = 0.45f), modifier = Modifier.size(110.dp * breathScale)) {}
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.size(72.dp),
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(if (breathScale > 1.05f) R.string.call_exhale else R.string.call_inhale),
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.call_breath_body),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = Color(0xFFE2E8F0),
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0369A1),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = stringResource(R.string.call_ready),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun CallActionButton(icon: String, label: String, backgroundColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
    ) {
        Surface(
            shape = CircleShape,
            color = backgroundColor,
            modifier = Modifier.size(50.dp),
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (icon) {
                    "yes" -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "no" -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    "express" -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    else -> CardImage(icon, Modifier.size(28.dp))
                }
            }
        }
        Text(
            text = label,
            fontFamily = Baloo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE2E8F0),
        )
    }
}
