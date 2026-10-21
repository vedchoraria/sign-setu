package app.signsetu.ui.session

import app.signsetu.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo
import java.util.Locale

/** A picture card on the express board, with its words and what is spoken when it is tapped. */
data class ExpressPictogram(
    val id: String,
    val englishLabel: String,
    val hindiLabel: String,
    val speechPhrase: String,
    val hindiSpeechPhrase: String,
    val category: ExpressCategory,
    val accentColor: Color,
    val textColor: Color = Color(0xFF1E293B),
)

enum class ExpressCategory(val en: String, val hi: String) {
    FEELINGS("Feelings & Safety", "भावना और सुरक्षा"),
    NEEDS("Basic Needs", "मूल ज़रूरतें"),
    PEOPLE("People & Family", "लोग और परिवार"),
    PLACES("Places & School", "स्थान और स्कूल"),
    ANSWERS("Quick Answers", "त्वरित उत्तर"),
    BODY("Where it hurts", "कहाँ दर्द है"),
}

/** The board's cards, by category: the things a frightened child or a person who cannot speak most needs to say. */
val ChildExpressCards = listOf(
    ExpressPictogram("pain", "I am Hurt / Pain", "दर्द हो रहा है", "I am in pain", "मुझे दर्द हो रहा है", ExpressCategory.FEELINGS, Color(0xFFFFD4D4), Color(0xFF8B1D1D)),
    ExpressPictogram("scared", "I am Scared", "मुझे डर लग रहा है", "I feel scared", "मुझे डर लग रहा है", ExpressCategory.FEELINGS, Color(0xFFFFF1C2), Color(0xFF8C5800)),
    ExpressPictogram("sick", "I Feel Sick", "तबीयत खराब है", "I feel sick", "मेरी तबीयत खराब है", ExpressCategory.FEELINGS, Color(0xFFFFE0D0), Color(0xFF8C2E00)),
    ExpressPictogram("stop", "Please Stop", "कृपया रुकिए", "Please stop", "कृपया रुकिए", ExpressCategory.FEELINGS, Color(0xFFFFCDD2), Color(0xFFB71C1C)),
    ExpressPictogram("police", "Need Police Help", "पुलिस मदद", "I need police help", "मुझे पुलिस सहायता चाहिए", ExpressCategory.FEELINGS, Color(0xFFD6E4FF), Color(0xFF1D3B8B)),
    ExpressPictogram("okay", "I Feel Safe", "मैं सुरक्षित हूँ", "I feel safe now", "मैं अब सुरक्षित हूँ", ExpressCategory.FEELINGS, Color(0xFFD1F2D9), Color(0xFF1B6B32)),
    ExpressPictogram("sad", "I am Sad / Crying", "मैं उदास हूँ", "I am very sad", "मैं बहुत उदास हूँ", ExpressCategory.FEELINGS, Color(0xFFEDE9FE), Color(0xFF5B21B6)),
    ExpressPictogram("angry", "Someone is Angry", "कोई गुस्सा कर रहा है", "Someone is angry with me", "मुझ पर कोई गुस्सा कर रहा है", ExpressCategory.FEELINGS, Color(0xFFFFEDD5), Color(0xFF9A3412)),

    ExpressPictogram("water", "Need Water", "पानी चाहिए", "I need water please", "मुझे पानी चाहिए", ExpressCategory.NEEDS, Color(0xFFD8F3FF), Color(0xFF026AA7)),
    ExpressPictogram("toilet", "Washroom", "शौचालय जाना है", "I need the washroom", "मुझे शौचालय जाना है", ExpressCategory.NEEDS, Color(0xFFE8DEFF), Color(0xFF5521B5)),
    ExpressPictogram("food", "Hungry / Food", "भूख लगी है / खाना", "I am hungry, I need food", "मुझे भूख लगी है, खाना चाहिए", ExpressCategory.NEEDS, Color(0xFFFFECCC), Color(0xFF8C5500)),
    ExpressPictogram("rest", "Tired / Rest", "आराम करना है", "I am tired, I need rest", "मैं थक गया हूँ, आराम चाहिए", ExpressCategory.NEEDS, Color(0xFFE2E8F0), Color(0xFF334155)),
    ExpressPictogram("phone", "Call Family", "घर पर फोन करो", "Please call my family", "कृपया मेरे घर फोन कीजिए", ExpressCategory.NEEDS, Color(0xFFC7F9CC), Color(0xFF145A32)),
    ExpressPictogram("medicine", "Medicine", "दवा चाहिए", "I need medicine", "मुझे दवा चाहिए", ExpressCategory.NEEDS, Color(0xFFCCFBF1), Color(0xFF115E59)),

    ExpressPictogram("mother", "Mother / Mummy", "माँ", "I want my mother", "मुझे मेरी माँ चाहिए", ExpressCategory.PEOPLE, Color(0xFFFFD6E0), Color(0xFF881337)),
    ExpressPictogram("father", "Father / Papa", "पिताजी", "I want my father", "मुझे मेरे पिताजी चाहिए", ExpressCategory.PEOPLE, Color(0xFFDBEAFE), Color(0xFF1E40AF)),
    ExpressPictogram("brother", "Brother", "भाई", "My brother", "मेरा भाई", ExpressCategory.PEOPLE, Color(0xFFE0E7FF), Color(0xFF3730A3)),
    ExpressPictogram("sister", "Sister", "बहन", "My sister", "मेरी बहन", ExpressCategory.PEOPLE, Color(0xFFFCE7F3), Color(0xFF9D174D)),
    ExpressPictogram("friends", "Friend", "दोस्त", "My friend", "मेरा दोस्त", ExpressCategory.PEOPLE, Color(0xFFDCFCE7), Color(0xFF166534)),
    ExpressPictogram("someone_else", "Strangers / Other Person", "अनजान व्यक्ति / कोई और", "An unknown person", "कोई अनजान व्यक्ति", ExpressCategory.PEOPLE, Color(0xFFFEE2E2), Color(0xFF991B1B)),

    ExpressPictogram("home", "Home / Take me Home", "घर / मुझे घर जाना है", "I want to go home", "मुझे घर जाना है", ExpressCategory.PLACES, Color(0xFFFEF3C7), Color(0xFF92400E)),
    ExpressPictogram("school", "School", "स्कूल", "At my school", "मेरे स्कूल में", ExpressCategory.PLACES, Color(0xFFE0F2FE), Color(0xFF0369A1)),
    ExpressPictogram("hospital", "Hospital / Clinic", "अस्पताल", "Take me to hospital", "मुझे अस्पताल ले चलो", ExpressCategory.PLACES, Color(0xFFFFE4E6), Color(0xFF9F1239)),
    ExpressPictogram("bus_stand", "Bus / Train Stand", "बस / ट्रेन स्टैंड", "At the bus or train station", "बस या रेलवे स्टेशन पर", ExpressCategory.PLACES, Color(0xFFF1F5F9), Color(0xFF334155)),
    ExpressPictogram("road", "Road / Outside", "सड़क / बाहर", "On the road", "सड़क पर", ExpressCategory.PLACES, Color(0xFFF3F4F6), Color(0xFF374151)),

    ExpressPictogram("yes", "YES", "हाँ", "Yes", "हाँ", ExpressCategory.ANSWERS, Color(0xFFC2F0C2), Color(0xFF0F5132)),
    ExpressPictogram("no", "NO", "नहीं", "No", "नहीं", ExpressCategory.ANSWERS, Color(0xFFFFC5C5), Color(0xFF842029)),
    ExpressPictogram("dont_know", "Don't Know", "पता नहीं", "I don't know", "मुझे नहीं पता", ExpressCategory.ANSWERS, Color(0xFFE2E8F0), Color(0xFF475569)),
    ExpressPictogram("dont_understand", "Don't Understand", "समझ नहीं आया", "I do not understand", "मुझे समझ नहीं आया", ExpressCategory.ANSWERS, Color(0xFFFFF3CD), Color(0xFF664D03)),
    ExpressPictogram("thank_you", "Thank You", "धन्यवाद", "Thank you", "धन्यवाद", ExpressCategory.ANSWERS, Color(0xFFE0F2FE), Color(0xFF0369A1)),

    ExpressPictogram("head", "Head", "सिर", "My head hurts", "सिर में दर्द है", ExpressCategory.BODY, Color(0xFFFFE4E6), Color(0xFF9F1239)),
    ExpressPictogram("throat", "Neck / Throat", "गला", "My throat hurts", "गले में दर्द है", ExpressCategory.BODY, Color(0xFFFEF3C7), Color(0xFF92400E)),
    ExpressPictogram("stomach", "Belly / Stomach", "पेट", "My stomach hurts", "पेट में दर्द है", ExpressCategory.BODY, Color(0xFFFFECCC), Color(0xFF8C5500)),
    ExpressPictogram("arm", "Arm / Hand", "हाथ / बांह", "My arm hurts", "हाथ में चोट है", ExpressCategory.BODY, Color(0xFFE0E7FF), Color(0xFF3730A3)),
    ExpressPictogram("leg", "Leg / Foot", "पैर", "My leg hurts", "पैर में चोट है", ExpressCategory.BODY, Color(0xFFDCFCE7), Color(0xFF166534)),
    ExpressPictogram("back", "Back", "पीठ", "My back hurts", "पीठ में दर्द है", ExpressCategory.BODY, Color(0xFFF3E8FF), Color(0xFF6B21A8)),
)

/** Speaks a tapped card aloud in the subject's language. */
class ChildSpeechSynthesizer(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Hindi
                ready = true
            }
        }
    }

    fun speak(phrase: String, lang: String) {
        if (!ready || tts == null) return
        tts?.language = when (lang) {
            "en" -> Locale.ENGLISH
            "ta", "te", "bn", "mr", "gu" -> Locale.forLanguageTag("$lang-IN")
            else -> Hindi
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "child_aac_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

private val Hindi = Locale.forLanguageTag("hi-IN")

/** The full-screen express board: large picture cards by category, each spoken aloud and sent as an answer. */
@Composable
fun FullChildMuteCommunicationBoard(
    lang: String,
    chosenCard: String?,
    onCard: (String) -> Unit,
    onDismiss: () -> Unit,
    tts: ChildSpeechSynthesizer?,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(ExpressCategory.FEELINGS) }
    var lastExpressed by remember { mutableStateOf<ExpressPictogram?>(null) }
    val haptic = LocalHapticFeedback.current
    val boardName = stringResource(R.string.board_name)
    val closeName = stringResource(R.string.close)

    Surface(
        color = Color(0xFFFFFBF2),
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = boardName },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFFF6F61), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            text = if (lang == "hi") "बच्चा व मूक संवाद बोर्ड" else "Child & Express Communication",
                            fontFamily = Baloo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B),
                        )
                        Text(
                            text = if (lang == "hi") "छूकर अपनी बात कहें (आवाज़ निकलेगी)" else "Tap any card to speak out loud",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onDismiss() }
                        .semantics { role = Role.Button; contentDescription = closeName },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFF334155))
                    }
                }
            }

            AnimatedVisibility(
                visible = lastExpressed != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                lastExpressed?.let { expr ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = expr.accentColor,
                        border = BorderStroke(2.dp, expr.textColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CardImage(expr.id, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "hi") expr.hindiSpeechPhrase else expr.speechPhrase,
                                    fontFamily = Baloo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = expr.textColor,
                                )
                                Text(
                                    text = if (lang == "hi") "अधिकारी को बता दिया गया है ✓" else "Notified the officer ✓",
                                    fontSize = 11.sp,
                                    color = expr.textColor.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val tabColor by animateColorAsState(if (isSelected) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                    val tabTextColor = if (isSelected) Color.White else Color(0xFF475569)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tabColor,
                        modifier = Modifier
                            .clickable { selectedCategory = cat }
                            .semantics {
                                role = Role.Tab
                                contentDescription = if (lang == "hi") cat.hi else cat.en
                            },
                    ) {
                        Text(
                            text = if (lang == "hi") cat.hi else cat.en,
                            fontFamily = Baloo,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = tabTextColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            val filteredCards = remember(selectedCategory) {
                ChildExpressCards.filter { it.category == selectedCategory }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filteredCards, key = { it.id }) { item ->
                    val isChosen = chosenCard == item.id
                    val label = if (lang == "hi") item.hindiLabel else item.englishLabel
                    val speechPhrase = if (lang == "hi") item.hindiSpeechPhrase else item.speechPhrase

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = item.accentColor,
                        border = if (isChosen) BorderStroke(3.dp, item.textColor) else BorderStroke(1.dp, item.textColor.copy(alpha = 0.2f)),
                        shadowElevation = if (isChosen) 6.dp else 2.dp,
                        modifier = Modifier
                            .height(108.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastExpressed = item
                                tts?.speak(speechPhrase, lang)
                                onCard(item.id)
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = label
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CardImage(item.id, Modifier.size(46.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                fontFamily = Baloo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = item.textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    GestureHintItem("👍", if (lang == "hi") "हाँ" else "Yes")
                    GestureHintItem("👎", if (lang == "hi") "नहीं" else "No")
                    GestureHintItem("✋", if (lang == "hi") "रुकें" else "Stop")
                    GestureHintItem("🤷", if (lang == "hi") "पता नहीं" else "Don't know")
                    GestureHintItem("👆", if (lang == "hi") "यहाँ दर्द है" else "Point to hurt")
                }
            }
        }
    }
}

@Composable
private fun GestureHintItem(emoji: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(label, fontFamily = Baloo, fontSize = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    }
}
