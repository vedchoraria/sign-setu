package app.signsetu.data.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class InterpreterApp { NONE, WHATSAPP, SANDES }

/** Settings hold no personal data about any subject. */
@Immutable
data class AppSettings(
    val firstRunDone: Boolean = false,
    val retentionHours: Int = DEFAULT_RETENTION_HOURS,
    val interpreterApp: InterpreterApp = InterpreterApp.NONE,
    /** A phone number or contact name given by the department; never a subject's details. */
    val interpreterContact: String = "",
    /** Start time of the newest session; lets the purge worker shred everything without the officer's key. */
    val newestSessionAt: Long = 0L,
    /** The live mirror on the stage; a department can turn it off. */
    val cameraEnabled: Boolean = true,
    /** Larger captions on the stage, for people who read slowly. */
    val largeCaptions: Boolean = false,
    /** Gestures play at three-quarter speed by default. */
    val slowGestures: Boolean = false,
    /** Tipping the phone over its top edge shows what is prepared. */
    val tiltToShow: Boolean = true,
    /** Volume up shows what is prepared; both keys held leave the stage. */
    val volumeKeys: Boolean = true,
    /** Picture-board cards the officer starred; card IDs only. */
    val favourites: Set<String> = emptySet(),
    /** This device has served as a second screen; it never offers to unlock by itself, so a subject cannot open it. */
    val stageDevice: Boolean = false,
    /** Auto mode proposes welfare items in rule order; off unless a department allows it. */
    val allowAuto: Boolean = false,
    /** Signs reorders typed words into ISL order; off keeps them as typed, until Deaf reviewers confirm the rules. */
    val islReorder: Boolean = true,
    /** Sends masked session text to the department gateway while online; off until the officer accepts the notice. */
    val cloudAi: Boolean = false,
) {
    val interpreterConfigured get() = interpreterApp != InterpreterApp.NONE && interpreterContact.isNotBlank()

    companion object {
        /** Zero: sessions are kept until the officer deletes them. */
        const val DEFAULT_RETENTION_HOURS = 0
        val RETENTION_CHOICES = listOf(0, 4, 8, 12, 24, 48, 72)
    }
}

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val firstRun = booleanPreferencesKey("first_run_done")
        val retention = intPreferencesKey("retention_hours")
        val interpreterApp = stringPreferencesKey("interpreter_app")
        val interpreterContact = stringPreferencesKey("interpreter_contact")
        val newestSession = longPreferencesKey("newest_session_at")
        val camera = booleanPreferencesKey("camera_enabled")
        val largeCaptions = booleanPreferencesKey("large_captions")
        val slowGestures = booleanPreferencesKey("slow_gestures")
        val tiltToShow = booleanPreferencesKey("tilt_to_show")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val favourites = stringSetPreferencesKey("favourites")
        val stageDevice = booleanPreferencesKey("stage_device")
        val allowAuto = booleanPreferencesKey("allow_auto")
        val islReorder = booleanPreferencesKey("isl_reorder")
        val cloudAi = booleanPreferencesKey("cloud_ai")
    }

    val settings: Flow<AppSettings> = context.store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setFirstRunDone() = context.store.edit { it[Keys.firstRun] = true }
    suspend fun setRetentionHours(hours: Int) = context.store.edit { it[Keys.retention] = hours.coerceIn(4, 72) }
    suspend fun setInterpreter(app: InterpreterApp, contact: String) = context.store.edit {
        it[Keys.interpreterApp] = app.name
        it[Keys.interpreterContact] = contact.trim()
    }
    suspend fun setNewestSessionAt(time: Long) = context.store.edit { it[Keys.newestSession] = time }
    suspend fun setCameraEnabled(on: Boolean) = context.store.edit { it[Keys.camera] = on }
    suspend fun setLargeCaptions(on: Boolean) = context.store.edit { it[Keys.largeCaptions] = on }
    suspend fun setSlowGestures(on: Boolean) = context.store.edit { it[Keys.slowGestures] = on }
    suspend fun setStageDevice() = context.store.edit { it[Keys.stageDevice] = true }
    suspend fun setAllowAuto(on: Boolean) = context.store.edit { it[Keys.allowAuto] = on }
    suspend fun setIslReorder(on: Boolean) = context.store.edit { it[Keys.islReorder] = on }
    suspend fun setCloudAi(on: Boolean) = context.store.edit { it[Keys.cloudAi] = on }
    suspend fun setTiltToShow(on: Boolean) = context.store.edit { it[Keys.tiltToShow] = on }
    suspend fun setVolumeKeys(on: Boolean) = context.store.edit { it[Keys.volumeKeys] = on }
    suspend fun toggleFavourite(cardId: String) = context.store.edit {
        val now = it[Keys.favourites].orEmpty()
        it[Keys.favourites] = if (cardId in now) now - cardId else now + cardId
    }

    private fun Preferences.toSettings() = AppSettings(
        firstRunDone = this[Keys.firstRun] ?: false,
        retentionHours = this[Keys.retention] ?: AppSettings.DEFAULT_RETENTION_HOURS,
        interpreterApp = this[Keys.interpreterApp]?.let { runCatching { InterpreterApp.valueOf(it) }.getOrNull() } ?: InterpreterApp.NONE,
        interpreterContact = this[Keys.interpreterContact] ?: "",
        newestSessionAt = this[Keys.newestSession] ?: 0L,
        cameraEnabled = this[Keys.camera] ?: true,
        largeCaptions = this[Keys.largeCaptions] ?: false,
        slowGestures = this[Keys.slowGestures] ?: false,
        tiltToShow = this[Keys.tiltToShow] ?: true,
        volumeKeys = this[Keys.volumeKeys] ?: true,
        favourites = this[Keys.favourites].orEmpty(),
        stageDevice = this[Keys.stageDevice] ?: false,
        allowAuto = this[Keys.allowAuto] ?: false,
        islReorder = this[Keys.islReorder] ?: true,
        cloudAi = this[Keys.cloudAi] ?: false,
    )
}

/**
 * The international number wa.me expects, without "+". A 10-digit number, or 11 with a leading trunk 0, is Indian.
 * Returns null when the contact cannot be a phone number.
 */
fun whatsappNumber(contact: String): String? {
    val digits = contact.filter(Char::isDigit).let { if (it.length == 11 && it.startsWith("0")) it.drop(1) else it }
    return when (digits.length) {
        10 -> "91$digits"
        in 11..15 -> digits
        else -> null
    }
}
