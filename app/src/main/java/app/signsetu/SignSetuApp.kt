package app.signsetu

import app.signsetu.assistant.ModelStore
import app.signsetu.assistant.Assistant
import app.signsetu.assistant.CloudAi
import app.signsetu.assistant.Direct
import app.signsetu.net.KeystoreVault
import app.signsetu.net.UrlHttp
import app.signsetu.net.isOnline
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.signsetu.data.content.ContentRepository
import app.signsetu.data.session.DatabaseKey
import app.signsetu.data.session.RetentionWorker
import app.signsetu.data.session.SessionDatabase
import app.signsetu.data.session.SessionRepository
import app.signsetu.data.settings.SettingsStore
import app.signsetu.camera.LandmarkTracker
import app.signsetu.camera.LandmarkTrackers
import app.signsetu.data.content.SignLanguage
import app.signsetu.sign.SignReader
import app.signsetu.sign.SignReaders
import app.signsetu.export.SessionReport
import app.signsetu.pair.ByeReason
import app.signsetu.pair.Identity
import app.signsetu.pair.LinkRole
import app.signsetu.pair.LinkState
import app.signsetu.pair.Peer
import app.signsetu.pair.StageLink
import app.signsetu.pair.StageTransport
import app.signsetu.pair.StageTransports
import app.signsetu.ui.session.HardKeys
import app.signsetu.ui.components.OfficerAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import androidx.core.content.edit

class SignSetuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container)
        RetentionWorker.schedule(this)
    }
}

/** Manual wiring; the app is small enough not to need a DI framework. */
class AppContainer(private val app: Application) : DefaultLifecycleObserver, ComponentCallbacks2 {
    val content = ContentRepository(app.assets)
    val settings = SettingsStore(app)
    val report = SessionReport(app)
    val models = ModelStore(app)
    val cloudAi = CloudAi({ settings.current().cloudAi }, BuildConfig.GATEWAY_URL, UrlHttp(), KeystoreVault(app), Direct(BuildConfig.ASSIST_URL, BuildConfig.ASSIST_MODEL, BuildConfig.ASSIST_KEY), KeystoreVault(app, "assist.bin", "signsetu.assist.key")) { isOnline(app) }
    val assistant = Assistant(models, cloudAi)
    val hardKeys = HardKeys()

    /** Landmark tracking, created per build type; without it the stage shows a plain mirror. */
    private val tracker = lazy { LandmarkTrackers.create(app) }
    val landmarkTracker: LandmarkTracker? by tracker
    private fun landmarkTrackerOrNull() = if (tracker.isInitialized()) tracker.value else null

    /** Reads isolated signs from landmarks; none until its model is present. */
    private val signReaders = SignLanguage.entries.associateWith { lang -> lazy { SignReaders.create(app, lang) } }
    val signReader: SignReader? get() = signReader(SignLanguage.ISL)
    fun signReader(lang: SignLanguage): SignReader? = signReaders.getValue(lang).value

    /** warn only; blocking would fail an officer in an emergency. */
    val deviceLooksRooted: Boolean by lazy {
        Build.TAGS?.contains("test-keys") == true ||
            listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su").any { File(it).exists() }
    }

    // The latest day this app has seen: turning the phone's date back does not make an old build young again.
    private val clock = app.getSharedPreferences("clock", android.content.Context.MODE_PRIVATE)

    // The latest day seen this run; the preference is only written when the day moves on, not on every composition.
    @Volatile private var seenDay = -1L

    val buildExpired: Boolean
        get() {
            val stored = clock.getLong("seen_day", 0L)
            val today = maxOf(LocalDate.now().toEpochDay(), stored)
            if (today != seenDay) {
                seenDay = today
                if (today > stored) clock.edit { putLong("seen_day", today) }
            }
            return buildExpired(today, BuildConfig.BUILD_EPOCH_DAY)
        }
    private val databaseKey = DatabaseKey(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var database: SessionDatabase? = null
    private var backgroundedAt = 0L

    private val _sessions = MutableStateFlow<SessionRepository?>(null)
    private val _keyReset = MutableStateFlow(false)
    /** True after stored sessions were lost because the screen lock changed; the officer is told once. */
    val keyReset: StateFlow<Boolean> = _keyReset.asStateFlow()
    fun acknowledgeKeyReset() { _keyReset.value = false }

    private val _keyUnbound = MutableStateFlow(false)
    /** True after an unlock on a phone with no secure lock screen: records are then not tied to unlocking it. */
    val keyUnbound: StateFlow<Boolean> = _keyUnbound.asStateFlow()
    fun acknowledgeKeyUnbound() { _keyUnbound.value = false }
    @Volatile private var unboundTold = false

    private val _downgraded = MutableStateFlow(false)
    /** True after stored sessions were reset because of a database downgrade; the officer is notified. */
    val downgraded: StateFlow<Boolean> = _downgraded.asStateFlow()
    fun acknowledgeDowngrade() { _downgraded.value = false }

    /** Null while the app is locked: no session data can be read without the officer's credential. */
    val sessions: StateFlow<SessionRepository?> = _sessions.asStateFlow()

    // One unlock at a time; a lock() while it runs bumps the epoch, and the unlock then closes what it opened.
    private val unlocking = Mutex()
    @Volatile private var epoch = 0

    /** Call right after a successful authentication prompt, while the Keystore key is usable. */
    suspend fun unlock() = unlocking.withLock {
        val started = epoch
        withContext(Dispatchers.IO) {
            if (_sessions.value != null) return@withContext
            val db = try {
                openDatabase()
            } catch (e: SQLiteNotADatabaseException) {
                // The file does not match the key (corrupted, or left from an older key): unreadable for good.
                databaseKey.destroy()
                openDatabase().also { _keyReset.value = true }
            }
            if (epoch != started) { db.close(); return@withContext }
            val repo = SessionRepository(db.dao(), onSessionStarted = { settings.setNewestSessionAt(it) })
            repo.purgeExpired(settings.current().retentionHours)
            // Exported reports are only needed while the share sheet hands them over.
            SessionReport.exportsDir(app).deleteRecursively()
            withContext(Dispatchers.Main) {
                if (epoch != started) { db.close(); return@withContext }
                database = db
                _sessions.value = repo
            }
        }
    }

    private fun openDatabase(): SessionDatabase = databaseKey.unwrap().use { unwrapped ->
        if (unwrapped.reset) _keyReset.value = true
        // Once per process: an officer without a lock screen is not stopped at every unlock.
        if (databaseKey.unbound && !unboundTold) { unboundTold = true; _keyUnbound.value = true }
        // SQLCipher clears the key bytes itself once the database is open.
        SessionDatabase.open(app, unwrapped.key, onDestructiveMigration = { _downgraded.value = true }).also { db ->
            runCatching { db.openHelper.writableDatabase }.onFailure { db.close(); throw it }
        }
    }

    fun unlockAsync(onFailed: () -> Unit) {
        scope.launch { runCatching { unlock() }.onFailure { Log.w(TAG, "unlock failed", it); onFailed() } }
    }

    /** The second-screen transport; null when the device has no Bluetooth, and every entry point is then hidden. */
    val pairTransport: StageTransport? by lazy { StageTransports.create(app) }
    private val _link = MutableStateFlow<StageLink?>(null)
    /** At most one link: this phone as officer, or this device as the stage. Memory only. */
    val link: StateFlow<StageLink?> = _link.asStateFlow()
    // Link logic runs on one thread; its blocking reads and writes go to the IO dispatcher.
    private val linkScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private suspend fun identity() = Identity(BuildConfig.VERSION_CODE, content.load().manifestHash, "${Build.MANUFACTURER} ${Build.MODEL}")

    /** This device becomes the subject's screen; the session database is never opened here. */
    fun startStage() = scope.launch {
        val transport = pairTransport ?: return@launch
        endLink(ByeReason.CANCEL)
        _link.value = StageLink(LinkRole.STAGE, transport, identity(), linkScope).also { it.listen() }
    }

    /** Pairs with [peer], or resumes the link already pinned to it. */
    fun pairWith(peer: Peer) = scope.launch {
        val transport = pairTransport ?: return@launch
        val current = _link.value
        if (current?.role == LinkRole.OFFICER && current.state.value == LinkState.Lost && current.pinned == peer.id) {
            current.reconnect()
            return@launch
        }
        endLink(ByeReason.CANCEL)
        _link.value = StageLink(LinkRole.OFFICER, transport, identity(), linkScope).also {
            it.peerLabel = peer.label
            it.connect(peer.id)
        }
    }

    fun endLink(reason: String = ByeReason.END) {
        _link.value?.end(reason)
        _link.value = null
    }

    private val officerLink get() = _link.value?.takeIf { it.role == LinkRole.OFFICER }

    /** Main thread only. Screens still composed may write once more; the repository drops those writes. */
    fun lock() {
        epoch++
        OfficerAuth.reset()
        // The records were in use until now; the locked-time wipe counts retention from here.
        if (_sessions.value != null) scope.launch { settings.setNewestSessionAt(System.currentTimeMillis()) }
        _sessions.value?.closed = true
        _sessions.value = null
        officerLink?.let { endLink() }
        scope.launch { models.release() }
        landmarkTrackerOrNull()?.let { scope.launch(Dispatchers.Default) { it.close() } }
        val db = database
        database = null
        // Closed after the session screen has gone, so its last collectors do not query a closed file.
        scope.launch { delay(1_000); db?.close() }
    }

    fun clearAllData() {
        val open = database
        lock()
        // Delete the file only once nothing has it open: an unlinked SQLite file stays readable until it is closed.
        runCatching { open?.close() }
        databaseKey.destroy()
        SessionReport.exportsDir(app).deleteRecursively()
    }

    init {
        app.registerComponentCallbacks(this)
    }

    /** The assistant and the landmark graph are the largest buffers; both load again on next use. */
    @Suppress("DEPRECATION") // The trim levels still arrive on every API; Android has no replacement constants.
    override fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        scope.launch { models.release() }
        landmarkTrackerOrNull()?.let { scope.launch(Dispatchers.Default) { it.close() } }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
        // The officer left the app: the stage covers its item until they are back.
        officerLink?.hold()
    }

    /** Set while a session screen is open: a trip to a video call and back does not lock the conversation. */
    @Volatile var inSession = false

    override fun onStart(owner: LifecycleOwner) {
        val limit = if (inSession) RELOCK_IN_SESSION_MS else RELOCK_AFTER_MS
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > limit) lock()
        officerLink?.unhold()
    }

    private companion object {
        const val RELOCK_AFTER_MS = 5 * 60 * 1000L
        const val RELOCK_IN_SESSION_MS = 30 * 60 * 1000L
        const val TAG = "SignSetu"
    }
}
