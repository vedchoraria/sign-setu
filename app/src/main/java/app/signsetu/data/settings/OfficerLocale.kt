package app.signsetu.data.settings

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/**
 * The officer's interface language, chosen in the app on every Android version.
 * Plain preferences, because it is needed before the first frame; it holds no personal data.
 */
object OfficerLocale {
    /** An empty tag follows the phone's language. */
    val CHOICES = listOf("", "en", "hi")

    private const val FILE = "locale"
    private const val KEY = "officer_lang"

    fun get(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(KEY, tag) }
    }

    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}
