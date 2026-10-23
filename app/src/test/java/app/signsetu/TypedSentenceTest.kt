package app.signsetu

import app.signsetu.data.content.Made
import app.signsetu.data.content.toHindi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Sentences an officer types in a first meeting. A wrong caption in a police interview is worse than no caption, so
 * the ones that earlier came out as commands or nonsense are pinned word for word.
 */
class TypedSentenceTest {
    private val content = shipped

    private fun draft(s: String) = content.toHindi(s)

    @Test
    fun safetyCriticalSentencesReadCorrectly() {
        val cases = mapOf(
            "I am here to help you" to "मैं आपकी मदद के लिए यहाँ हूँ।",
            "You are not in trouble" to "आप किसी परेशानी में नहीं हैं।",
            "You did nothing wrong" to "आपने कुछ गलत नहीं किया।",
            "You can stop at any time" to "आप कभी भी रुक सकते हैं।",
            "There is nothing to be afraid of" to "डरने की कोई बात नहीं है।",
            "Show me where it hurts" to "मुझे दिखाइए कहाँ दर्द है।",
            "How long has it been hurting" to "कितने समय से दर्द हो रहा है?",
            "Is there someone we can call" to "क्या कोई है जिसे हम फ़ोन कर सकें?",
            "We will take you to the doctor" to "हम आपको डॉक्टर के पास ले जाएँगे।",
            "The doctor will see you soon" to "डॉक्टर जल्द ही आपको देखेंगे।",
            "She is my sister" to "वे मेरी बहन हैं।",
            "He hit me" to "उन्होंने मुझे मारा।",
            "He threatened me with a knife" to "उन्होंने चाकू दिखाकर मुझे डराया।",
            "Your friend is waiting outside" to "आपका दोस्त बाहर इंतज़ार कर रहा है।",
            "I need to ask you some questions" to "मुझे आपसे कुछ सवाल पूछने हैं।",
            "I want to go home now" to "मुझे अब घर जाना है।",
        )
        for ((en, hi) in cases) assertEquals(en, hi, draft(en)?.hi)
    }

    @Test
    fun firstPersonIsSingular() {
        // "I go home" is मैं जाता हूँ, not हम जाते हैं.
        assertEquals(Made.WORDS, draft("i go home")?.made)
        assertEquals("मैं घर जाता हूँ।", draft("i go home")?.hi)
        assertEquals("हम घर जाते हैं।", draft("we go home")?.hi)
    }

    @Test
    fun everyCounsellorSentenceGetsADraft() {
        val lines = listOf(
            "Can you tell me your name", "Do you want to call your family", "Do you understand what I am saying",
            "Are you feeling dizzy", "Have you eaten today", "When did this happen", "Did anyone see what happened",
            "Please sit down and take your time", "Please show me your arm", "Do you need a blanket",
            "Is it painful here", "We are listening", "I do not know his name", "I want to go home now",
        )
        for (l in lines) assertNotNull(l, draft(l))
    }
}
