package app.signsetu.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.SignMode
import app.signsetu.data.content.planText
import app.signsetu.ui.avatar.AVATAR_ASPECT
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.data.content.questionKind
import app.signsetu.ui.theme.Spacing

/**
 * Type anything and watch the character sign it: the same planner the session uses, with nothing to record and
 * no one to ask. A word with no sign is fingerspelled and marked as such, so it is clear what was signed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(content: ContentBundle, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var plays by rememberSaveable { mutableIntStateOf(0) }
    // Every door open: a sign where there is one, fingerspelling otherwise, and pictures for numbers.
    val modes = remember { setOf(SignMode.SIGNS, SignMode.GESTURES, SignMode.SPELL, SignMode.PICTURES) }
    val plan = remember(text, content) { content.takeIf { it.gestures.isNotEmpty() }?.planText(text, modes) }
    val gesture = plan?.gesture
    val question = remember(text) { questionKind(text) }
    val spoken = remember(text) { text.trim().isNotEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Avatar(
                    character = Character.ADULT,
                    gesture = gesture,
                    playKey = Triple(text, plays, gesture?.id),
                    idle = !spoken,
                    question = question,
                    // Beside the word list rather than filling the panel: the character reads better at stage size.
                    modifier = Modifier.heightIn(max = 520.dp).aspectRatio(AVATAR_ASPECT),
                )
            }
            if (plan != null && plan.beats.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(max = 112.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    for (beat in plan.beats) {
                        val word = text.substring(beat.range)
                        // A beat with letters and no picture is fingerspelled; say so, so the spelling reads as deliberate.
                        val spelled = beat.letters != null && beat.card == null
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(word, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (spelled) stringResource(R.string.practice_spelled) else stringResource(R.string.practice_signed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(if (spoken) R.string.practice_no_sign else R.string.practice_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.practice_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { plays++ },
                enabled = gesture != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.practice_sign)) }
        }
    }
}
