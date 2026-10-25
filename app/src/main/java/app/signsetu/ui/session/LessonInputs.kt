package app.signsetu.ui.session

import app.signsetu.ui.theme.LocalStageColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.AnswerType
import app.signsetu.data.content.CLASSES
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Given
import app.signsetu.data.content.or
import app.signsetu.data.content.Method
import app.signsetu.data.content.NAME_MAX
import app.signsetu.data.content.Question
import app.signsetu.data.content.TEXT_MAX
import app.signsetu.data.content.answerText
import app.signsetu.data.content.groupPhone
import app.signsetu.data.content.padPress
import app.signsetu.data.content.state
import app.signsetu.data.content.valid
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import app.signsetu.ui.theme.LocalAnswerColors
import app.signsetu.ui.theme.Spacing

/** The bank group whose phrases the subject can say from the stage. */
const val EVERYDAY = "everyday"

private val Soft = Color(0xFFFFE7BF)
private val OnSoft = Color(0xFF3D2700)
/** A Done that cannot be pressed yet. */
internal val Off = Color(0xFFBFD8CB)

/** The question on stage, when it takes a lesson-sheet answer input instead of the answer rails. */
fun lessonQuestion(pending: Pending?): Question? = (pending as? Pending.Ask)?.question?.takeIf { it.answer != null }

/** Answer inputs for the lesson-sheet questions. A written or typed answer is read back large and logged as the
 * subject's own only when they accept it; a read-back left open is logged unconfirmed. */
@Composable
internal fun AnswerPanel(
    q: Question,
    content: ContentBundle,
    lang: String,
    chosen: String?,
    onCard: (String) -> Unit,
    onGiven: (Given?) -> Unit,
    onSign: (() -> Unit)?,
    modifier: Modifier,
) {
    val type = q.answer ?: return
    var readBack by remember(q.id) { mutableStateOf<Given?>(null) }
    var accepted by remember(q.id) { mutableStateOf<Given?>(null) }
    val entered = { g: Given -> readBack = g; accepted = null; onGiven(g) }
    Column(modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val back = readBack
            val done = accepted
            when {
                done != null -> ReadBack(content.answerText(type, done, lang), content, lang, onYes = null, onFix = { accepted = null })
                back != null -> ReadBack(
                    content.answerText(type, back, lang), content, lang,
                    onYes = { val g = back.copy(confirmed = true); onGiven(g); accepted = g; readBack = null },
                    onFix = { onGiven(null); readBack = null },
                )
                else -> when (type) {
                    AnswerType.NAME, AnswerType.TEXT -> WordEntry(type, null) { entered(Given(q.id, Method.WRITTEN, false, it.trim())) }
                    AnswerType.AGE, AnswerType.COUNT, AnswerType.PHONE -> NumberPad(type) { entered(Given(q.id, Method.PAD, false, it)) }
                    AnswerType.SIBLINGS -> SiblingPads(content, lang) { entered(Given(q.id, Method.PAD, false, it)) }
                    AnswerType.CLASS -> ClassGrid(content, lang) { entered(Given(q.id, Method.PICKER, false, it)) }
                    AnswerType.STATE, AnswerType.DISTRICT, AnswerType.PLACE ->
                        PlacePicker(type, content, lang) { picks, written -> entered(Given(q.id, Method.PICKER, false, written.trim(), picks)) }
                    // Done on their own selection is the subject's confirmation.
                    AnswerType.PICTURES -> PictureChoices(q.cards, content, lang) { picks ->
                        val g = Given(q.id, Method.PICTURE, true, picks = picks)
                        onGiven(g)
                        accepted = g
                    }
                }
            }
        }
        WaysOut(content, lang, chosen, onCard, onSign, extra = "no_phone".takeIf { type == AnswerType.PHONE })
    }
}

/** The answer shown back large; Yes makes it the subject's, Change lets them enter it again. */
@Composable
private fun ReadBack(text: String, content: ContentBundle, lang: String, onYes: (() -> Unit)?, onFix: () -> Unit) {
    val answer = LocalAnswerColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                if (onYes != null) content.subjectText("is_this_right", lang)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink) }
                else Icon(Icons.Filled.Check, contentDescription = null, tint = answer.onYesContainer, modifier = Modifier.size(32.dp))
                Text(
                    text, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, color = Ink,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 40.sp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BigButton(rememberVectorPainter(Icons.Filled.Edit), content.subjectText("fix", lang), stringResource(R.string.stage_fix), Soft, OnSoft, Modifier.weight(1f), onFix)
            onYes?.let { BigButton(rememberVectorPainter(Icons.Filled.Check), content.subjectText("yes", lang), stringResource(R.string.stage_confirm), answer.yes, answer.onYes, Modifier.weight(1f), it) }
        }
    }
}

@Composable
private fun BigButton(icon: Painter, label: String?, name: String, color: Color, onColor: Color, modifier: Modifier, onClick: () -> Unit) {
    PressableSurface(onClick = onClick, shape = RoundedCornerShape(50), color = color, modifier = modifier.height(64.dp).semantics { contentDescription = label ?: name }) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = onColor)
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.sm)) }
        }
    }
}

/** Large digits on a phone-style pad; Done stays grey until the number is possible for the question. */
@Composable
private fun NumberPad(type: AnswerType, title: (@Composable () -> Unit)? = null, onEnter: (String) -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    val answer = LocalAnswerColors.current
    val ok = valid(type, digits)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            title?.invoke()
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.weight(1f).height(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (type == AnswerType.PHONE) groupPhone(digits) else digits, fontFamily = Baloo, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 34.sp),
                    )
                }
            }
        }
        // One tap on a held-up hand of 1 to 10 for a child who cannot work the pad.
        if (type == AnswerType.AGE || type == AnswerType.COUNT) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(10) { index ->
                    val num = (index + 1).toString()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (digits == num) Color(0xFFFFB300) else Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD7C9B8)),
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                digits = num
                                if (valid(type, num)) {
                                    onEnter(num)
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("🖐", fontSize = 14.sp)
                            Text(num, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        }
                    }
                }
            }
        }
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c -> PadKey(c.toString(), Modifier.weight(1f)) { digits = padPress(type, digits, c) } }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadIcon(rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), stringResource(R.string.pad_delete), Soft, OnSoft, Modifier.weight(1f)) { digits = digits.dropLast(1) }
            PadKey("0", Modifier.weight(1f)) { digits = padPress(type, digits, '0') }
            PadIcon(rememberVectorPainter(Icons.Filled.Check), stringResource(R.string.stage_done), if (ok) answer.yes else Off, answer.onYes, Modifier.weight(1f)) { if (ok) onEnter(digits) }
        }
    }
}

@Composable
private fun PadKey(digit: String, modifier: Modifier, onClick: () -> Unit) {
    PressableSurface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = modifier.fillMaxSize().heightIn(min = 48.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(digit, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink) }
    }
}

@Composable
private fun PadIcon(icon: Painter, name: String, color: Color, onColor: Color, modifier: Modifier, onClick: () -> Unit) {
    PressableSurface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = color, modifier = modifier.fillMaxSize().heightIn(min = 48.dp).semantics { contentDescription = name }) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(28.dp)) }
    }
}

/** Brothers, then sisters, each on its own pad with its picture; the value is "brothers/sisters". */
@Composable
private fun SiblingPads(content: ContentBundle, lang: String, onEnter: (String) -> Unit) {
    var brothers by rememberSaveable { mutableStateOf<String?>(null) }
    val card = if (brothers == null) "brother" else "sister"
    key(card) {
        NumberPad(AnswerType.SIBLINGS, title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CardImage(card, Modifier.size(36.dp))
                content.subjectText(if (brothers == null) "brothers" else "sisters", lang)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Ink) }
            }
        }) { n -> brothers?.let { onEnter("$it/$n") } ?: run { brothers = n } }
    }
}

@Composable
private fun ClassGrid(content: ContentBundle, lang: String, onPick: (String) -> Unit) {
    val named = mapOf("college" to stringResource(R.string.stage_college), "not_in_school" to stringResource(R.string.stage_not_in_school))
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CLASSES.take(12).chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c -> PadKey(c, Modifier.weight(1f)) { onPick(c) } }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CLASSES.drop(12).forEach { c ->
                PressableSurface(onClick = { onPick(c) }, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.weight(1f).fillMaxSize().heightIn(min = 48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(content.subjectText(c, lang) ?: named.getValue(c), style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** State first, Chhattisgarh at the top; then its district, or the name written when the state has no list here. */
@Composable
private fun PlacePicker(type: AnswerType, content: ContentBundle, lang: String, onPicked: (List<String>, String) -> Unit) {
    var stateId by rememberSaveable { mutableStateOf<String?>(null) }
    var districtId by rememberSaveable { mutableStateOf<String?>(null) }
    val state = content.state(stateId)
    when {
        state == null -> PickList(content.subjectText("pick_state", lang), content.states.map { it.id to it.name.or(lang).orEmpty() }, null) { id ->
            if (type == AnswerType.STATE) onPicked(listOf(id), "") else stateId = id
        }
        state.districts.isNotEmpty() && districtId == null ->
            PickList(content.subjectText("pick_district", lang), state.districts.map { it.id to it.name.or(lang).orEmpty() }, { stateId = null }) { id ->
                if (type == AnswerType.DISTRICT) onPicked(listOf(state.id, id), "") else districtId = id
            }
        else -> {
            val picks = listOfNotNull(state.id, districtId)
            WordEntry(
                AnswerType.TEXT,
                content.subjectText(if (type == AnswerType.DISTRICT) "pick_district" else "write_place", lang),
                onBack = { if (districtId != null) districtId = null else stateId = null },
                onSkip = { onPicked(picks, "") }.takeIf { type == AnswerType.PLACE },
            ) { onPicked(picks, it) }
        }
    }
}

@Composable
private fun PickList(title: String?, items: List<Pair<String, String>>, onBack: (() -> Unit)?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            onBack?.let { Back(it) }
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.first }) { (id, name) ->
                PressableSurface(onClick = { onPick(id) }, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Box(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), contentAlignment = Alignment.CenterStart) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Back(onClick: () -> Unit) {
    val name = stringResource(R.string.stage_back)
    PressableSurface(onClick = onClick, shape = CircleShape, color = Soft, modifier = Modifier.size(48.dp).semantics { contentDescription = name }) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = OnSoft) }
    }
}

/** Their own words in their own script; the keyboard learns nothing. */
@Composable
private fun WordEntry(type: AnswerType, title: String?, onBack: (() -> Unit)? = null, onSkip: (() -> Unit)? = null, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val answer = LocalAnswerColors.current
    val ok = valid(type, text)
    val max = if (type == AnswerType.NAME) NAME_MAX else TEXT_MAX
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink) }
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= max) text = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) onDone(text) }),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier.fillMaxSize().padding(Spacing.lg).focusRequester(focus),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            onBack?.let { Back(it) }
            onSkip?.let { BigButton(rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), null, stringResource(R.string.stage_skip), Soft, OnSoft, Modifier.weight(1f), it) }
            BigButton(rememberVectorPainter(Icons.Filled.Check), null, stringResource(R.string.stage_done), if (ok) answer.yes else Off, answer.onYes, Modifier.weight(1f)) { if (ok) onDone(text) }
        }
    }
}

/** Every picture the question lists; the subject picks one or more, then Done. */
@Composable
private fun PictureChoices(cards: List<String>, content: ContentBundle, lang: String, onDone: (List<String>) -> Unit) {
    val picked = remember(cards) { mutableStateListOf<String>() }
    val answer = LocalAnswerColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(cards.filter { it in content.cards }.distinct(), key = { it }) { id ->
                ChoiceTile(id, content.cards[id]?.label?.get(lang), id in picked, { if (!picked.remove(id)) picked += id }, Modifier, name = content.cards[id]?.label?.get("en"))
            }
        }
        BigButton(
            rememberVectorPainter(Icons.Filled.Check), content.subjectText("done", lang), stringResource(R.string.stage_done),
            if (picked.isNotEmpty()) answer.yes else Off, answer.onYes, Modifier.fillMaxWidth(),
        ) { if (picked.isNotEmpty()) onDone(picked.toList()) }
    }
}

/** I don't know, I don't understand, That's wrong and Stop under every input, in the same order as the rails. */
@Composable
private fun WaysOut(content: ContentBundle, lang: String, chosen: String?, onCard: (String) -> Unit, onSign: (() -> Unit)?, extra: String? = null) {
    val answer = LocalAnswerColors.current
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        extra?.let { Way(it, content.cards[it]?.label?.get(lang), stringResource(R.string.stage_no_phone), Color.White, Ink, chosen == it) { onCard(it) } }
        Way("dont_know", content.subjectText("dont_know", lang), stringResource(R.string.stage_dont_know), answer.unsure, answer.onUnsure, chosen == "dont_know") { onCard("dont_know") }
        Way(DONT_UNDERSTAND, content.subjectText(DONT_UNDERSTAND, lang), stringResource(R.string.stage_dont_understand), answer.unsure, answer.onUnsure, chosen == DONT_UNDERSTAND) { onCard(DONT_UNDERSTAND) }
        Way("wrong", content.subjectText("wrong", lang), stringResource(R.string.stage_wrong), answer.unsure, answer.onUnsure, chosen == "wrong") { onCard("wrong") }
        onSign?.let {
            val word = content.subjectText("sign", lang)
            val name = word ?: stringResource(R.string.officer_read_signs)
            val stage = LocalStageColors.current
            // A tool colour and a word, so the camera never reads as a second Stop beside it.
            PressableSurface(onClick = it, shape = RoundedCornerShape(16.dp), color = stage.tool, border = stage.toolEdge, modifier = Modifier.weight(1f).fillMaxSize().semantics { contentDescription = name }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(painterResource(R.drawable.ic_videocam), contentDescription = null, tint = stage.onTool, modifier = Modifier.size(24.dp))
                    word?.let { w -> Text(w, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = stage.onTool, maxLines = 1) }
                }
            }
        }
        Way("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosen == "stop") { onCard("stop") }
    }
}

@Composable
private fun RowScope.Way(cardId: String, label: String?, name: String, color: Color, onColor: Color, chosen: Boolean, onClick: () -> Unit) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = if (chosen) BorderStroke(3.dp, onColor) else null,
        modifier = Modifier.weight(1f).fillMaxSize().semantics { contentDescription = label ?: name },
    ) {
        Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CardImage(cardId, Modifier.size(if (label != null) 24.dp else 36.dp))
            label?.let { Text(it, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 12.sp, color = onColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Opens the phrase board, so the subject can start a conversation as well as answer one. */
@Composable
internal fun SayButton(content: ContentBundle, lang: String, onClick: () -> Unit, modifier: Modifier) {
    val name = content.subjectText("say", lang) ?: stringResource(R.string.stage_say)
    PressableSurface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 2.dp, modifier = modifier.size(48.dp).semantics { contentDescription = name }) {
        CardImage("namaste", Modifier.padding(8.dp))
    }
}

/** The everyday phrases as pictures with words; a tap logs the phrase as the subject's own. */
@Composable
internal fun PhraseBoard(content: ContentBundle, lang: String, chosen: String?, onCard: (String) -> Unit, onSay: (Given) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val phrases = remember(content) { content.questions.filter { it.group == EVERYDAY } }
    val answer = LocalAnswerColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(phrases, key = { it.id }) { q ->
                val label = q.text.get(lang)
                PressableSurface(
                    onClick = { onSay(Given(q.id, Method.BOARD, true)) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.heightIn(min = 64.dp).semantics { contentDescription = label ?: q.text["en"].orEmpty() },
                ) {
                    Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        q.cards.firstOrNull()?.let { CardImage(it, Modifier.size(40.dp)) }
                        label?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Back(onBack)
            Way("stop", content.subjectText("stop", lang), stringResource(R.string.stage_stop), answer.pause, answer.onPause, chosen == "stop") { onCard("stop") }
        }
    }
}
