package app.signsetu.ui.session

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import java.util.Locale

/** A picture card on the express board; its words come from the card's reviewed labels. */
private class ExpressCard(val id: String, val tab: String, val accent: Color, val ink: Color)

/** The board's tabs are content card categories, so their titles exist in every language. */
private val Tabs = listOf("feelings", "needs", "people", "places", "answers", "body")

/** The things a frightened child or a person who cannot speak most needs to say. */
private val ExpressCards = listOf(
    ExpressCard("pain", "feelings", Color(0xFFFFD4D4), Color(0xFF8B1D1D)),
    ExpressCard("scared", "feelings", Color(0xFFFFF1C2), Color(0xFF8C5800)),
    ExpressCard("sick", "feelings", Color(0xFFFFE0D0), Color(0xFF8C2E00)),
    ExpressCard("stop", "feelings", Color(0xFFFFCDD2), Color(0xFFB71C1C)),
    ExpressCard("police", "feelings", Color(0xFFD6E4FF), Color(0xFF1D3B8B)),
    ExpressCard("okay", "feelings", Color(0xFFD1F2D9), Color(0xFF1B6B32)),
    ExpressCard("sad", "feelings", Color(0xFFEDE9FE), Color(0xFF5B21B6)),
    ExpressCard("angry", "feelings", Color(0xFFFFEDD5), Color(0xFF9A3412)),
    ExpressCard("water", "needs", Color(0xFFD8F3FF), Color(0xFF026AA7)),
    ExpressCard("toilet", "needs", Color(0xFFE8DEFF), Color(0xFF5521B5)),
    ExpressCard("food", "needs", Color(0xFFFFECCC), Color(0xFF8C5500)),
    ExpressCard("rest", "needs", Color(0xFFE2E8F0), Color(0xFF334155)),
    ExpressCard("phone", "needs", Color(0xFFC7F9CC), Color(0xFF145A32)),
    ExpressCard("medicine", "needs", Color(0xFFCCFBF1), Color(0xFF115E59)),
    ExpressCard("mother", "people", Color(0xFFFFD6E0), Color(0xFF881337)),
    ExpressCard("father", "people", Color(0xFFDBEAFE), Color(0xFF1E40AF)),
    ExpressCard("brother", "people", Color(0xFFE0E7FF), Color(0xFF3730A3)),
    ExpressCard("sister", "people", Color(0xFFFCE7F3), Color(0xFF9D174D)),
    ExpressCard("friends", "people", Color(0xFFDCFCE7), Color(0xFF166534)),
    ExpressCard("someone_else", "people", Color(0xFFFEE2E2), Color(0xFF991B1B)),
    ExpressCard("home", "places", Color(0xFFFEF3C7), Color(0xFF92400E)),
    ExpressCard("school", "places", Color(0xFFE0F2FE), Color(0xFF0369A1)),
    ExpressCard("hospital", "places", Color(0xFFFFE4E6), Color(0xFF9F1239)),
    ExpressCard("bus_stand", "places", Color(0xFFF1F5F9), Color(0xFF334155)),
    ExpressCard("road", "places", Color(0xFFF3F4F6), Color(0xFF374151)),
    ExpressCard("yes", "answers", Color(0xFFC2F0C2), Color(0xFF0F5132)),
    ExpressCard("no", "answers", Color(0xFFFFC5C5), Color(0xFF842029)),
    ExpressCard("dont_know", "answers", Color(0xFFE2E8F0), Color(0xFF475569)),
    ExpressCard("dont_understand", "answers", Color(0xFFFFF3CD), Color(0xFF664D03)),
    ExpressCard("thank_you", "answers", Color(0xFFE0F2FE), Color(0xFF0369A1)),
    ExpressCard("head", "body", Color(0xFFFFE4E6), Color(0xFF9F1239)),
    ExpressCard("throat", "body", Color(0xFFFEF3C7), Color(0xFF92400E)),
    ExpressCard("stomach", "body", Color(0xFFFFECCC), Color(0xFF8C5500)),
    ExpressCard("arm", "body", Color(0xFFE0E7FF), Color(0xFF3730A3)),
    ExpressCard("leg", "body", Color(0xFFDCFCE7), Color(0xFF166534)),
    ExpressCard("back", "body", Color(0xFFF3E8FF), Color(0xFF6B21A8)),
)

/** Speaks a tapped card aloud in the subject's language; silent when the phone has no voice for it. */
private class CardVoice(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { ready = it == TextToSpeech.SUCCESS }

    fun speak(text: String, lang: String) {
        val locale = if (lang == "en") Locale.ENGLISH else Locale.forLanguageTag("$lang-IN")
        if (!ready || tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card")
    }

    fun shutdown() = tts.shutdown()
}

/** The full-screen express board: large picture cards by category, each spoken aloud and sent as an answer. */
@Composable
fun FullChildMuteCommunicationBoard(
    content: ContentBundle,
    lang: String,
    chosenCard: String?,
    onCard: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(Tabs.first()) }
    var said by remember { mutableStateOf<ExpressCard?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val voice = remember(context) { CardVoice(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val boardName = stringResource(R.string.board_name)
    val closeName = stringResource(R.string.close)
    fun label(id: String) = content.cards[id]?.label?.or(lang) ?: id

    Surface(color = Color(0xFFFFFBF2), modifier = modifier.fillMaxSize().semantics { contentDescription = boardName }) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(said != null, Modifier.weight(1f), enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                    said?.let { card ->
                        Surface(shape = RoundedCornerShape(16.dp), color = card.accent, border = BorderStroke(2.dp, card.ink)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CardImage(card.id, Modifier.size(32.dp))
                                Text(label(card.id), Modifier.weight(1f), fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = card.ink)
                                Icon(Icons.Filled.Check, contentDescription = null, tint = card.ink)
                            }
                        }
                    }
                }
                if (said == null) Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.padding(start = 8.dp).size(40.dp).clickable { onDismiss() }.semantics { role = Role.Button; contentDescription = closeName },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFF334155)) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tabs.forEach { id ->
                    val selected = tab == id
                    val title = content.categories.firstOrNull { it.id == id }?.label?.or(lang) ?: id
                    val fill by animateColorAsState(if (selected) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = fill,
                        modifier = Modifier.clickable { tab = id }.semantics { role = Role.Tab; contentDescription = title },
                    ) {
                        Text(
                            title,
                            fontFamily = Baloo,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(ExpressCards.filter { it.tab == tab }, key = { it.id }) { card ->
                    val chosen = chosenCard == card.id
                    val text = label(card.id)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = card.accent,
                        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) card.ink else card.ink.copy(alpha = 0.2f)),
                        shadowElevation = if (chosen) 6.dp else 2.dp,
                        modifier = Modifier
                            .height(108.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                said = card
                                voice.speak(text, lang)
                                onCard(card.id)
                            }
                            .semantics { role = Role.Button; contentDescription = text },
                    ) {
                        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CardImage(card.id, Modifier.size(46.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text,
                                fontFamily = Baloo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = card.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
