package app.signsetu.ui.session

import app.signsetu.data.content.givenOf
import app.signsetu.data.content.Method
import androidx.compose.foundation.border
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signsetu.R
import app.signsetu.data.content.pick
import app.signsetu.data.session.EventType
import app.signsetu.data.session.Profile
import app.signsetu.data.session.SessionEvent
import app.signsetu.ui.avatar.MiniFace
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.LocalInk
import app.signsetu.ui.theme.PictureWell
import app.signsetu.ui.theme.Spacing
import androidx.compose.material.icons.filled.PlayArrow
import app.signsetu.sign.SignSegment
import java.text.DateFormat
import java.util.Date
import app.signsetu.ui.pair.linkText

/** The conversation so far: what was shown on the right, the subject's answers on the left, newest at the bottom. */
@Composable
internal fun Transcript(events: List<SessionEvent>, profile: Profile, signed: Map<Long, List<SignSegment>>, onReplay: (Long) -> Unit, modifier: Modifier, onSayBack: (SessionEvent) -> Unit = {}, drawing: suspend (Long) -> ByteArray? = { null }) {
    if (events.isEmpty()) {
        Column(modifier.padding(Spacing.xxl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            MiniFace(characterFor(profile), 72.dp, Color(0xFFFFDDBE))
            Text(
                stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                stringResource(R.string.chat_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        return
    }
    // Suggestions and their outcomes belong in the log, not the conversation.
    val newestFirst = remember(events) { events.filter { it.type != EventType.PROMPT && it.type !in AutoRows }.asReversed() }
    val by = remember(events) { shownBy(events) }
    val grades = remember(events) { grades(events) }
    val trails = remember(events) { answerTrails(events) }
    val positions = remember(events) { choicePositions(events) }
    LazyColumn(
        modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(newestFirst, key = { it.id }) { e ->
            Box(Modifier.animateItem()) {
                when (e.type) {
                    EventType.QUESTION_SHOWN -> Shown(e, by[e.id])
                    EventType.NOTE -> Aside(e.text.orEmpty(), note = true)
                    EventType.SYSTEM -> Aside(systemText(e.text), note = false)
                    EventType.LINK -> Aside(linkText(e).orEmpty(), note = false)
                    else -> Answered(
                        e, profile, onReplay = if (e.id in signed) ({ onReplay(e.id) }) else null,
                        grade = grades[e.id], trail = trails[e.id], position = positions[e.id],
                        onSayBack = if (canSayBack(e, grades[e.id])) ({ onSayBack(e) }) else null,
                        drawing = drawing,
                    )
                }
            }
        }
    }
}

@Composable
internal fun systemText(text: String?) = systemText(LocalResources.current, text)

private val AutoRows = setOf(EventType.SUGGESTED, EventType.APPROVED, EventType.SKIPPED, EventType.AUTO_ADVANCED)

private fun timeOf(e: SessionEvent) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.ts))

@Composable
internal fun Shown(e: SessionEvent, by: ShownBy? = null) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(ink.brush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(e.text ?: "—", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        val tags = listOfNotNull(
            by?.tag?.let { stringResource(it) },
            if (e.reason == SHOWN_RIGHTS) stringResource(R.string.rights_shown_mark) else null,
            if (e.reason == SHOWN_AI) stringResource(R.string.ai_tag) else null,
        ).joinToString("") { " · $it" }
        Text(
            "${stringResource(e.type.label())} · ${timeOf(e)}$tags",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp),
        )
    }
}

@Composable
private fun Answered(
    e: SessionEvent,
    profile: Profile,
    onReplay: (() -> Unit)? = null,
    grade: Grade? = null,
    trail: Trail? = null,
    position: Pair<Int, Int>? = null,
    onSayBack: (() -> Unit)? = null,
    drawing: suspend (Long) -> ByteArray? = { null },
) {
    val colors = MaterialTheme.colorScheme
    val answer = LocalAnswerColors.current
    // A phrase-board pick or a pad entry is not typed, so it is named by how it was given.
    val given = givenOf(e.reason)
    val well = when {
        e.cardId == "yes" -> answer.yesContainer
        e.cardId == "no" -> answer.noContainer
        e.type == EventType.ANSWER_UNCLEAR -> colors.tertiaryContainer
        else -> PictureWell
    }
    Row(Modifier.fillMaxWidth().padding(end = 48.dp), verticalAlignment = Alignment.Bottom) {
        MiniFace(characterFor(profile), 28.dp, Color(0xFFFFDDBE))
        Column(Modifier.padding(start = Spacing.sm)) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp),
                color = colors.surfaceContainerLowest,
                border = trailBorder(trail) ?: BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Row(Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = well, modifier = Modifier.size(40.dp)) {
                        when {
                            e.cardId != null -> CardImage(e.cardId, Modifier.padding(5.dp))
                            e.drawingId != null -> Icon(Icons.Filled.Create, contentDescription = null, modifier = Modifier.padding(10.dp))
                            given?.first == Method.BOARD -> Icon(painterResource(R.drawable.ic_grid), contentDescription = null, modifier = Modifier.padding(10.dp))
                            e.type == EventType.ANSWER_TEXT -> Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(10.dp))
                            else -> Icon(painterResource(R.drawable.ic_hand), contentDescription = null, modifier = Modifier.padding(10.dp))
                        }
                    }
                    val signed = e.type == EventType.ANSWER_SIGNED
                    Text(
                        if (signed) signedDisplay(LocalResources.current, e.text) else e.text ?: stringResource(e.type.label()),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (signed && signedWords(e.text) == null) colors.onSurfaceVariant else colors.onSurface,
                        modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false),
                    )
                    onReplay?.let { play ->
                        IconButton(onClick = play, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.signed_replay), tint = colors.primary)
                        }
                    }
                }
            }
            e.drawingId?.let { DrawingThumb(it, drawing) }
            val mark = if (e.type == EventType.ANSWER_SIGNED) " · ${stringResource(signedMark(e))}" else ""
            val kind = given?.let { stringResource(it.first.label()).replaceFirstChar(Char::uppercase) } ?: stringResource(e.type.label())
            Text(
                "$kind$mark · ${timeOf(e)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp),
            )
            AnswerMeta(grade, trail, position, onSayBack, cueText(LocalResources.current, e))
        }
    }
}

/** Notes and system steps sit between the two sides, like dates in a chat. */
@Composable
private fun Aside(text: String, note: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (note) colors.tertiaryContainer else colors.surfaceContainerHigh,
            contentColor = if (note) colors.onTertiaryContainer else colors.onSurfaceVariant,
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** The subject's drawing itself, not just a label; a tap opens it full size. */
@Composable
private fun DrawingThumb(id: Long, load: suspend (Long) -> ByteArray?) {
    val image by produceState<ImageBitmap?>(null, id) {
        // Half size is plenty for the bubble and its dialog, and a quarter of the memory.
        value = withContext(Dispatchers.Default) { load(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } }
    }
    var open by remember { mutableStateOf(false) }
    val name = stringResource(R.string.report_drawing)
    image?.let { img ->
        Image(
            img, contentDescription = name,
            modifier = Modifier.padding(top = Spacing.xs).widthIn(max = 220.dp).heightIn(max = 220.dp)
                .clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { open = true },
        )
        if (open) GuardedAlertDialog(
            onDismissRequest = { open = false },
            text = { Image(img, contentDescription = name, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
