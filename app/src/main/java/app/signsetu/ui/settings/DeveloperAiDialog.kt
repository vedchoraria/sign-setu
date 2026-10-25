package app.signsetu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.assistant.AiProbe
import app.signsetu.ui.components.GuardedAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Developer settings: the OpenAI-style service the assistant talks to. The address may be a base such as .../v1 or a full
 * chat or responses address; models are listed from the service once the address and key are in, and the name can still be typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperAiDialog(
    probe: AiProbe,
    initialUrl: String,
    initialModel: String,
    loadKey: suspend () -> String?,
    onSave: (url: String, model: String, key: String) -> Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var key by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(initialModel) }
    var models by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    var failed by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var keyLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!keyLoaded) { keyLoaded = true; if (key.isEmpty()) key = withContext(Dispatchers.IO) { loadKey() }.orEmpty() }
    }
    val found = stringResource(R.string.dev_ai_models_found)
    val none = stringResource(R.string.dev_ai_models_none)
    fun discover() {
        if (busy) return
        busy = true
        scope.launch {
            val list = probe.models(url, key)
            models = list.orEmpty()
            failed = list == null
            status = if (list == null) none else found.format(list.size)
            busy = false
        }
    }
    // Typing the address and key finds the models on its own, once the typing pauses.
    LaunchedEffect(url, key) {
        if (url.trim().startsWith("https://") && key.isNotBlank()) { delay(900); discover() }
    }

    GuardedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dev_ai_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dev_ai_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.dev_ai_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.dev_ai_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = menu && models.isNotEmpty(), onExpandedChange = { menu = it }) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; menu = false },
                        label = { Text(stringResource(R.string.dev_ai_model)) },
                        singleLine = true,
                        trailingIcon = { if (models.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(expanded = menu && models.isNotEmpty(), onDismissRequest = { menu = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { model = m; menu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::discover, enabled = !busy && url.isNotBlank()) { Text(stringResource(R.string.dev_ai_find)) }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            status = ""
                            scope.launch {
                                val r = probe.ping(url, model, key)
                                failed = !r.ok
                                status = r.detail
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.dev_ai_test)) }
                }
                if (status.isNotEmpty()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (onSave(url, model, key)) onDismiss() else { failed = true; status = "The key could not be stored on this phone" } },
                enabled = url.trim().startsWith("https://") && model.isNotBlank() && key.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initialUrl.isNotBlank()) TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.dev_ai_clear), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
