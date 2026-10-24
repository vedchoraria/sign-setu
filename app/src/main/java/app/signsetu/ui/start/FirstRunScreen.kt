package app.signsetu.ui.start

import app.signsetu.ui.components.BrandButton
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signsetu.R
import android.app.Activity
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import app.signsetu.data.settings.OfficerLocale
import app.signsetu.ui.settings.languageName
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.data.content.Gesture
import app.signsetu.ui.session.Backdrop
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import app.signsetu.ui.theme.Spacing

private class Step(@StringRes val title: Int, @StringRes val body: Int, val gesture: String, val icon: @Composable () -> Painter)

private val STEPS = listOf(
    Step(R.string.intro_aid_title, R.string.intro_aid_body, "namaste") { rememberVectorPainter(Icons.Filled.Info) },
    Step(R.string.intro_private_title, R.string.intro_private_body, "me.chest") { rememberVectorPainter(Icons.Filled.Lock) },
)

/** Shown once, before the first unlock. */
@Composable
fun FirstRunScreen(gestures: Map<String, Gesture>, onDone: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = STEPS[index]
    val last = index == STEPS.lastIndex
    val context = LocalContext.current
    BackHandler(enabled = index > 0) { index-- }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    STEPS.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 10.dp else 8.dp)
                                .background(
                                    if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                if (index > 0) TextButton(onClick = { index-- }) { Text(stringResource(R.string.back)) }
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
                BrandButton(
                    onClick = { if (last) onDone() else index++ },
                    modifier = Modifier.padding(start = Spacing.sm).height(48.dp),
                ) { Text(stringResource(if (last) R.string.intro_done else R.string.intro_next)) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Spacer(Modifier.size(Spacing.lg))
            // Capped so the step text, not the character, leads on the officer's screen.
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            val stageHeight = minOf(windowHeight * 0.32f, 240.dp)
            Box(Modifier.fillMaxWidth().height(stageHeight).clip(MaterialTheme.shapes.large)) {
                Backdrop(Modifier.fillMaxSize())
                Avatar(
                    character = Character.CHILD,
                    gesture = gestures[step.gesture],
                    playKey = index,
                    modifier = Modifier.fillMaxHeight(0.94f).aspectRatio(1f).align(Alignment.BottomCenter),
                )
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(14.dp).size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(step.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.intro_step, index + 1, STEPS.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(step.title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(step.body), style = MaterialTheme.typography.bodyLarge)
            if (index == 0) {
                // the officer picks the language of the app before anything else.
                val current = OfficerLocale.get(context)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OfficerLocale.CHOICES.forEach { tag ->
                        FilterChip(
                            selected = current == tag,
                            onClick = { OfficerLocale.set(context, tag); (context as? Activity)?.recreate() },
                            label = { Text(stringResource(languageName(tag)), style = MaterialTheme.typography.labelLarge) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
