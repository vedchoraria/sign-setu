package app.signsetu

import app.signsetu.data.content.Card
import app.signsetu.data.content.CardCategory
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.Group
import app.signsetu.data.content.LexiconEntry
import app.signsetu.data.content.Localized
import app.signsetu.data.content.Made
import app.signsetu.data.content.Phrase
import app.signsetu.data.content.Question
import app.signsetu.data.content.WordEntry
import app.signsetu.data.content.glossToText
import app.signsetu.data.content.toHindi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TranslateTest {
    @Serializable private data class TriageFile(val groups: List<Group>, val questions: List<Question>)
    @Serializable private data class CardsFile(val categories: List<CardCategory>, val cards: List<Card>)
    @Serializable private data class LexiconFile(val entries: List<LexiconEntry>)
    @Serializable private data class PhrasesFile(val phrases: List<Phrase>)
    @Serializable private data class WordsFile(val words: List<WordEntry>)

    private val dir = File("src/main/assets/content")
    private val json = Json { ignoreUnknownKeys = true }
    private inline fun <reified T> read(name: String) = json.decodeFromString<T>(File(dir, name).readText())
    private val triage = read<TriageFile>("triage.json")
    private val cardsFile = read<CardsFile>("cards.json")
    private val words = read<WordsFile>("words.json").words.flatMap { e -> e.w.map { it to e } }.toMap()
    private val content = ContentBundle(
        groups = triage.groups, questions = triage.questions, categories = cardsFile.categories,
        cards = cardsFile.cards.associateBy { it.id }, lexicon = read<LexiconFile>("lexicon.json").entries.associateBy { it.id },
        languages = emptyList(), subjectUi = read<Map<String, Localized>>("subject_ui.json"), rejected = emptySet(),
        phrases = read<PhrasesFile>("phrases.json").phrases, words = words,
    )

    private fun check(cases: List<Pair<String, String>>, made: Made) {
        for ((en, hi) in cases) {
            val d = content.toHindi(en)
            assertEquals(en, hi to made, d?.hi to d?.made)
        }
    }

    @Test
    fun reviewedSentencesWin() {
        check(listOf(
            "Are you hurt?" to "क्या आपको चोट लगी है?",
            "What is your name?" to "आपका नाम क्या है?",
            "Where do you live?" to "आप कहाँ रहते हैं?",
            "Do you want food?" to "क्या आपको खाना चाहिए?",
            "Thank you" to "धन्यवाद।",
            "are you hurt" to "क्या आपको चोट लगी है?",
        ), Made.BANK)
        check(listOf(
            "What's your name" to "आपका नाम क्या है?",
            "Do you need toilet?" to "क्या आपको शौचालय जाना है?",
            "I do not understand." to "मुझे समझ नहीं आया",
            "Do you need the doctor" to "क्या आपको डॉक्टर चाहिए?",
        ), Made.NEAR)
    }

    @Test
    fun templates() {
        check(listOf(
            "Do you want tea?" to "क्या आपको चाय चाहिए?",
            "Do you need medicine?" to "क्या आपको दवा चाहिए?",
            "Do you want to go home?" to "क्या आप घर जाना चाहते हैं?",
            "Do you need to rest?" to "क्या आपको आराम करना है?",
            "Are you thirsty?" to "क्या आपको प्यास लगी है?",
            "Are you safe?" to "क्या आप सुरक्षित हैं?",
            "Is your mother hurt?" to "क्या आपकी माँ को चोट लगी है?",
            "Is your father hurt?" to "क्या आपके पिता को चोट लगी है?",
            "Where is your brother?" to "आपका भाई कहाँ है?",
            "Where are your shoes?" to "आपके जूते कहाँ हैं?",
            "Who is this?" to "यह कौन है?",
            "Who is with you?" to "आपके साथ कौन है?",
            "What is your brother's name?" to "आपके भाई का नाम क्या है?",
            "How old is your sister?" to "आपकी बहन की उम्र कितनी है?",
            "Did someone hurt you?" to "क्या किसी ने आपको चोट पहुँचाई?",
            "Did he touch you?" to "क्या उन्होंने आपको छुआ?",
            "Show me your hand." to "मुझे अपना हाथ दिखाइए।",
            "I will help you." to "हम आपकी मदद करेंगे।",
            "I will call your mother." to "हम आपकी माँ को फ़ोन करेंगे।",
            "Sit here." to "यहाँ बैठिए।",
            "Please sit down" to "कृपया बैठिए।",
            "Come with me." to "मेरे साथ आइए।",
            "Don't worry." to "चिंता मत कीजिए।",
            "Do not be afraid" to "मत डरिए।",
            "Can you hear me?" to "क्या आप मुझे सुन सकते हैं?",
            "Can you write your name?" to "क्या आप अपना नाम लिख सकते हैं?",
            "Do you have a phone?" to "क्या आपके पास फ़ोन है?",
            "Is this your bag?" to "क्या यह आपका बैग है?",
            "How many sisters do you have?" to "आपकी कितनी बहनें हैं?",
            "How many brothers do you have?" to "आपके कितने भाई हैं?",
            "How many people are there?" to "कितने लोग हैं?",
            "When did it happen?" to "यह कब हुआ?",
            "Point to your stomach." to "अपने पेट की ओर इशारा कीजिए।",
            "Your name" to "आपका नाम",
            "Where do you work?" to "आप कहाँ काम करते हैं?",
            "Do you understand?" to "क्या आप समझते हैं?",
            "Tell me what happened." to "मुझे बताइए कि क्या हुआ।",
            "What happened to your mother?" to "आपकी माँ के साथ क्या हुआ?",
            "You are safe now." to "आप अब सुरक्षित हैं।",
            "I am a police officer." to "मैं पुलिस अधिकारी हूँ।",
            "The doctor is coming." to "डॉक्टर आ रहे हैं।",
            "Your mother is coming." to "आपकी माँ आ रही हैं।",
            "Look at me." to "मेरी ओर देखिए।",
        ), Made.TEMPLATE)
    }

    @Test
    fun severalSentencesTakeTheWeakestSource() {
        val d = content.toHindi("Do not worry. I will help you.")
        assertEquals("चिंता मत कीजिए। हम आपकी मदद करेंगे।" to Made.TEMPLATE, d?.hi to d?.made)
        val mixed = content.toHindi("Do you want water? I will help you.")
        assertEquals("क्या आपको पानी चाहिए? हम आपकी मदद करेंगे।" to Made.TEMPLATE, mixed?.hi to mixed?.made)
    }

    @Test
    fun wordByWordOnlyWhenEveryWordIsKnown() {
        check(listOf(
            "you go home now?" to "क्या आप घर अब जाते हैं?",
            "no water" to "पानी नहीं",
        ), Made.WORDS)
        assertNull(content.toHindi("My name is Ravi."))
        assertNull(content.toHindi("Blorft the zibble"))
        assertNull("no modal form, so no caption that drops it", content.toHindi("you can not go"))
        assertNull(content.toHindi("Do not worry. Ravi is here."))
        assertNull(content.toHindi(""))
    }

    @Test
    fun signedDraftSaysVerbsAsHindiDoes() {
        val cases = listOf(
            listOf("me", "water", "want") to "मुझे पानी चाहिए।",
            listOf("me", "water", "want", "no") to "मुझे पानी नहीं चाहिए।",
            listOf("me", "home", "go") to "मुझे घर जाना है।",
            listOf("you", "doctor", "want") to "आपको डॉक्टर चाहिए।",
            listOf("me", "pain") to "मुझे दर्द है।",
            listOf("you", "go", "where") to "आपको कहाँ जाना है?",
            listOf("me", "understand", "no") to "मुझे नहीं समझ आया।",
        )
        for ((gloss, hi) in cases) assertEquals(gloss.toString(), hi, glossToText(gloss, words).hi)
    }
}
