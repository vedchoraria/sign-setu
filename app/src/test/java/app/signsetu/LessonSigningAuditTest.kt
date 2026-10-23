package app.signsetu

import app.signsetu.data.content.SignMode
import app.signsetu.data.content.TOKEN
import app.signsetu.data.content.motionFor
import app.signsetu.data.content.planText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every lesson-sheet sentence as the character signs it: each word a recorded sign, an authored one, letters
 * or nothing. Prints the plan so a reviewer can read it. */
class LessonSigningAuditTest {

    private val content = shipped

    private val sentences = listOf(
        "p1", "p2", "p3", "f5", "f6", "f7", "f8", "f1", "a1", "a2", "a3", "a4", "a5", "sc1", "sc2", "sc3", "sc4", "sc5",
    ).map { content.question(it)!!.text["en"]!! } + (1..8).map { content.question("e$it")!!.text["en"]!! } + listOf(
        "My name is Ravi.", "I am ten years old.", "My mobile number is 9876543210.",
        "There are five members in my family.", "I live with my mother, father, one brother, one sister.",
        "I live in Raipur, Chhattisgarh.", "I study in Class 5.", "My favourite subject is Hindi.",
        "My father's name is Ravi.", "My mother's name is Sita.", "My school name is Saraswati.", "I need water.",
        "Hello.", "How are you?", "Please help me.", "I did not understand.", "Please repeat it.", "Thank you.", "I want to go home.",
    )

    @Test
    fun lessonSentencesUseRecordedSignsAndSpellOnlyNames() {
        var recorded = 0; var authored = 0; var spelled = 0; var missing = 0
        val spelledWords = mutableSetOf<String>()
        for (s in sentences) {
            val plan = content.planText(s, setOf(SignMode.SIGNS, SignMode.SPELL))
            val parts = plan.beats.map { b ->
                val phrase = s.substring(b.range).lowercase().split('\'', '’').first()
                val motion = content.motionFor(phrase, content.words[phrase], setOf(SignMode.SIGNS))?.id
                b.range to when {
                    b.letters != null -> "spell".also { spelled++; spelledWords += phrase }
                    motion?.startsWith("isl.") == true -> "rec".also { recorded++ }
                    else -> "$motion".also { authored++ }
                }
            }
            val line = TOKEN.findAll(s).joinToString(" ") { m ->
                parts.firstOrNull { m.range.first in it.first }?.let { "${m.value}[${it.second}]" } ?: "${m.value}[-]".also { missing++ }
            }
            println(line)
        }
        println("recorded $recorded, authored $authored, spelled $spelled, dropped $missing")
        // Only names, numbers and places are fingerspelled; every other word has a sign.
        assertEquals(setOf("ravi", "sita", "saraswati", "raipur", "chhattisgarh", "9876543210", "5"), spelledWords)
        assertTrue("recorded signs $recorded of ${recorded + authored}", recorded >= 9 * (recorded + authored) / 10)
    }
}
