package app.signsetu.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.sign.SignChoice
import app.signsetu.sign.SignSegment
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val SLOWER_CUE_MS = 3_000L

/** A hairline round the white tiles, so they keep an edge on the light stage. */
internal val TileEdge = BorderStroke(1.dp, Color(0x22000000))

/** The subject says what their sign meant: two or three pictures, in their language, and "none of these".
 * Nothing is pre-selected and nothing counts until they tap. */
@Composable
internal fun SignChoicePanel(choices: List<SignChoice>, content: ContentBundle, lang: String, onChoose: (SignChoice?) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { c ->
                if (c.card != null) {
                    ChoiceTile(c.card, content.cards[c.card]?.label?.get(lang), false, { onChoose(c) }, Modifier.weight(1f), name = content.cards[c.card]?.label?.get("en"))
                } else {
                    PressableSurface(onClick = { onChoose(c) }, shape = RoundedCornerShape(20.dp), color = Color.White, border = TileEdge, modifier = Modifier.weight(1f).heightIn(min = 96.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(c.text, style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
                    }
                }
            }
        }
        val none = content.subjectText("none_of_these", lang)
        val noneName = stringResource(R.string.stage_none)
        val stage = LocalStageColors.current
        PressableSurface(
            onClick = { onChoose(null) },
            shape = RoundedCornerShape(50),
            color = stage.tool,
            border = stage.toolEdge,
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = none ?: noneName },
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = stage.onTool)
                none?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = stage.onTool, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** While signing, one quiet dot per sign seen: the subject knows they are being followed, without being shown
 * the reader's guesses, which are only proposals. */
@Composable
internal fun SeenSigns(segments: List<SignSegment>, modifier: Modifier) {
    if (segments.isEmpty()) return
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = segments.takeLast(12)
            // The newest dot is a little larger, so each new sign is seen to land.
            shown.forEachIndexed { i, _ -> Box(Modifier.size(if (i == shown.lastIndex) 12.dp else 9.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)) }
        }
    }
}

/** Shown only after a failed read of fast or lost hands, never up front: a picture and a gentle caption. Each
 * new [failed] read shows it for a few seconds, so it never sits over the hands while they sign again. */
@Composable
internal fun SignSlowerCue(failed: Any?, content: ContentBundle, lang: String, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(failed) {
        visible = failed != null
        if (visible) { delay(SLOWER_CUE_MS); visible = false }
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.94f)) {
            Row(Modifier.padding(horizontal = Spacing.md, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_slow_hand), contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
                content.subjectText("sign_slower", lang)?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, modifier = Modifier.padding(start = Spacing.sm)) }
            }
        }
    }
}
