package app.signsetu.ui.session

import android.content.res.Resources
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.pick

/** One opening step: its words (a question id or a subject_ui key), the character's gesture and the picture held up. */
internal data class RuleStep(val key: String, val gesture: String, val card: String?)

/** The session opening (ABE 3.14 to 3.16): the officer's introduction, the ground rules, then a neutral
 * practice round so the subject learns the answers and the character before anything that matters is asked. */
internal val RuleSteps = listOf(
    RuleStep("s1", "police.salute", "police"),
    RuleStep("rule_not_there", "me.chest", null),
    RuleStep("rule_dont_know", "ask", "dont_know"),
    RuleStep("rule_wrong", "no.shake", "wrong"),
    RuleStep("rule_break", "okay", "stop"),
    RuleStep("practice_morning", "show_me", null),
)

/** SYSTEM rows the stage writes: the officer left before the opening ended, and how long a pause lasted. */
internal const val SYSTEM_RULES_SKIPPED = "rules_skipped"
internal const val SYSTEM_PAUSE_END = "pause_end:"

/** Officer-language text of those rows; null for any other row. */
internal fun stageSystemText(res: Resources, text: String?): String? = when {
    text == SYSTEM_RULES_SKIPPED -> res.getString(R.string.rules_skipped)
    text != null && text.startsWith(SYSTEM_PAUSE_END) -> res.getString(R.string.pause_ended, text.removePrefix(SYSTEM_PAUSE_END).toLongOrNull() ?: 0L)
    else -> null
}

internal fun ruleStep(pending: Pending?): RuleStep? = (pending as? Pending.GroundRules)?.let { RuleSteps.getOrNull(it.step) }

internal fun isPractice(pending: Pending?): Boolean = (pending as? Pending.GroundRules)?.step == RuleSteps.lastIndex

internal fun ruleText(content: ContentBundle, key: String, lang: String): String? =
    content.questions.firstOrNull { it.id == key }?.text?.pick(lang) ?: content.subjectText(key, lang)

internal fun ruleAudio(content: ContentBundle, key: String, lang: String): String? =
    content.questionAudio(key, lang) ?: content.subjectAudio(key, lang)
