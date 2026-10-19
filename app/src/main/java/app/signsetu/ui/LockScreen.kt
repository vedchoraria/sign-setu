package app.signsetu.ui

import app.signsetu.ui.components.BrandButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalInk
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.ui.theme.Spacing

/** Shown whenever the database is closed. Without a secure lock screen the app refuses to store anything (SEC-20). */
@Composable
fun LockScreen(
    onUnlock: (onFailed: () -> Unit) -> Unit,
    /** False on a device that has served as a second screen: only an explicit tap opens the database (D-26). */
    autoPrompt: Boolean = true,
    stageButton: (@Composable () -> Unit)? = null,
) {
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoPrompt) onUnlock { failed = true } }
    // Avoid flashing the lock screen while the silent unlock runs.
    if (autoPrompt && !failed) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val ink = LocalInk.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
                Text(
                    stringResource(R.string.app_name),
                    fontFamily = Baloo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(72.dp).background(ink.brush, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = ink.onBand, modifier = Modifier.size(28.dp))
                }
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                )
                Text(
                    stringResource(R.string.lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (failed) {
                    Text(stringResource(R.string.lock_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.sm))
                }
                Spacer(Modifier.weight(1f))
                BrandButton(
                    onClick = {
                        failed = false
                        onUnlock { failed = true }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.lock_button), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(Spacing.sm))
                stageButton?.invoke()
                Text(
                    stringResource(R.string.notice_aid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
    }
}
