package app.signsetu.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.DRAFT_CATEGORY
import app.signsetu.data.content.DictionaryEntry
import app.signsetu.data.content.SignLanguage
import app.signsetu.data.content.Question
import app.signsetu.data.content.dictionary
import app.signsetu.data.content.pick
import app.signsetu.data.content.search
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.theme.LocalStageColors
import app.signsetu.ui.theme.Spacing

/** Shown to the subject like a prepared item; the id keeps it apart from the question bank. */
fun DictionaryEntry.asQuestion(): Question =
    Question(id = "dict.$gestureId", group = "dictionary", text = buildMap { put("en", en); if (hi.isNotEmpty()) put("hi", hi) }, gesture = gestureId)

/** The officer's ISL dictionary (More page): every recorded and draft sign by category, searched in English or
 * Hindi. */
@Composable
fun IslDictionarySheet(content: ContentBundle, lang: String, signLanguage: SignLanguage, onShow: (Question) -> Unit, onDismiss: () -> Unit) {
    val all = remember(content) { content.dictionary() }
    val categories = remember(all) { all.map { it.category }.distinct() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var plays by remember { mutableIntStateOf(0) }
    val selected = all.firstOrNull { it.gestureId == selectedId }
    val shown = remember(all, query, category) { all.search(query, category) }
    val label = { e: DictionaryEntry -> if (lang == "hi" && e.hi.isNotEmpty()) e.hi else e.en }

    OfficerSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding()) {
            SheetTitle(stringResource(if (signLanguage == SignLanguage.ASL) R.string.asl_dictionary else R.string.isl_dictionary))
            Surface(shape = RoundedCornerShape(16.dp), color = LocalStageColors.current.skyTop, modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Box {
                    Avatar(Character.ADULT, selected?.let { content.gestures[it.gestureId] }, selectedId to plays, Modifier.align(Alignment.Center).size(180.dp))
                    if (selected != null) {
                        IconButton(onClick = { plays++ }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.isl_dictionary_replay))
                        }
                    }
                }
            }
            if (selected == null) {
                Text(stringResource(R.string.isl_dictionary_pick), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.sm))
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOf(selected.en, selected.hi).filter { it.isNotEmpty() }.joinToString("  ·  "), style = MaterialTheme.typography.titleMedium)
                        val note = listOfNotNull(
                            stringResource(if (!selected.recorded) R.string.isl_dictionary_draft else if (signLanguage == SignLanguage.ASL) R.string.asl_dictionary_recorded else R.string.isl_dictionary_recorded),
                            if (selected.static) stringResource(R.string.dictionary_held_pose) else null,
                            if (selected.oneHand) stringResource(R.string.dictionary_right_hand_only) else null,
                        ).joinToString(" ")
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { onShow(selected.asQuestion()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.isl_dictionary_show)) }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.isl_dictionary_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                item { FilterChip(category == null, { category = null }, { Text(stringResource(R.string.isl_dictionary_all)) }, Modifier.heightIn(min = 48.dp)) }
                items(categories) { c ->
                    val name = content.isl.categories[c]?.let { it.pick(lang) ?: it["en"] } ?: c
                    FilterChip(category == c, { category = if (category == c) null else c }, { Text(name) }, Modifier.heightIn(min = 48.dp))
                }
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (shown.isEmpty()) item { Text(stringResource(R.string.isl_dictionary_empty), modifier = Modifier.padding(vertical = Spacing.md)) }
                items(shown, key = { it.gestureId }) { e ->
                    ListItem(
                        headlineContent = { Text(label(e)) },
                        supportingContent = { Text(if (lang == "hi") e.en else e.hi) },
                        trailingContent = { if (e.category == DRAFT_CATEGORY) Text(stringResource(R.string.isl_dictionary_draft_tag), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { selectedId = e.gestureId; plays++ },
                    )
                }
            }
            Text(
                stringResource(if (signLanguage == SignLanguage.ASL) R.string.asl_dictionary_attribution else R.string.isl_dictionary_attribution),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
    }
}
