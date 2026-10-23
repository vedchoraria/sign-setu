package app.signsetu.ui.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

/** Fires when the phone is tipped over its top edge toward the subject: gravity y goes from top-up to top-down
 * and stays there for [dwellMs]. */
class TiltDetector(val dwellMs: Long = 600, val armY: Float = 3f, val flipY: Float = -3f) {
    private var armed = false
    private var flippedSince = -1L

    /** True once per handover; re-arms only after the top is up again. */
    fun update(gy: Float, tMs: Long): Boolean {
        if (gy > armY) { armed = true; flippedSince = -1; return false }
        if (!armed || gy >= flipY) { flippedSince = -1; return false }
        if (flippedSince < 0) flippedSince = tMs
        if (tMs - flippedSince < dwellMs) return false
        armed = false
        flippedSince = -1
        return true
    }
}

enum class HardKey { UP, DOWN }

/** What the volume keys do now; NONE leaves them to the system. */
enum class KeyMode { NONE, OFFICER_READY, STAGE }

/** Volume-key events from the activity to the session screen, which sets [mode]. */
class HardKeys {
    @Volatile var mode = KeyMode.NONE
    val events = MutableSharedFlow<Pair<HardKey, Boolean>>(extraBufferCapacity = 8)

    fun consumes(key: HardKey) = when (mode) {
        KeyMode.NONE -> false
        KeyMode.OFFICER_READY -> key == HardKey.UP
        KeyMode.STAGE -> true
    }
}

/** Both volume keys held together for [holdMs]; fires once per hold, so a child pressing one key changes nothing. */
class ChordDetector(val holdMs: Long = 1500) {
    private val held = mutableSetOf<HardKey>()
    private var bothSince = -1L
    private var fired = false

    fun down(key: HardKey, tMs: Long) {
        held += key
        if (held.size == 2 && bothSince < 0) bothSince = tMs
    }

    fun up(key: HardKey) {
        held -= key
        bothSince = -1
        fired = false
    }

    fun poll(tMs: Long): Boolean {
        if (bothSince < 0 || fired || tMs - bothSince < holdMs) return false
        fired = true
        return true
    }
}

/** Listens to gravity only while [enabled]; the accelerometer, low-pass filtered, stands in where there is no gravity sensor. */
@Composable
fun HandoverTilt(enabled: Boolean, onHandover: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onHandover)
    DisposableEffect(enabled) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensor == null) return@DisposableEffect onDispose { }
        val filter = sensor.type == Sensor.TYPE_ACCELEROMETER
        val detector = TiltDetector()
        var gy = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val y = event.values[1]
                gy = if (filter && !gy.isNaN()) gy + 0.2f * (y - gy) else y
                if (detector.update(gy, SystemClock.elapsedRealtime())) callback()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
}

/** True while the officer has a dialog or sheet open: tilt and Volume-Up must not hand the phone over under it. */
internal object OfficerModal {
    val open = androidx.compose.runtime.mutableStateOf(false)
}

/** Tilt and volume keys for one session screen; [onLeaveStage] takes the phone back from the stage. */
@Composable
fun HandoverEffects(
    ui: SessionUi,
    vm: SessionViewModel,
    tiltToShow: Boolean,
    volumeKeys: Boolean,
    keys: HardKeys,
    onLeaveStage: () -> Unit,
) {
    val view = LocalView.current
    val onStage = ui.mode == Mode.SHOW
    val ready = ui.mode == Mode.OFFICER && ui.pending != null && !OfficerModal.open.value
    val current by rememberUpdatedState(ui)
    val leave by rememberUpdatedState(onLeaveStage)
    val show = { rotate: Boolean ->
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (rotate && !current.rotated) vm.toggleRotation()
        vm.show()
    }

    HandoverTilt(enabled = tiltToShow && ready) { show(true) }

    SideEffect {
        keys.mode = when {
            !volumeKeys -> KeyMode.NONE
            onStage -> KeyMode.STAGE
            ready -> KeyMode.OFFICER_READY
            else -> KeyMode.NONE
        }
    }
    DisposableEffect(keys) { onDispose { keys.mode = KeyMode.NONE } }
    // Fresh per stage: a key released off the stage goes to the system, so an old detector could think it still held.
    val chord = remember(onStage) { ChordDetector() }
    LaunchedEffect(keys, chord) {
        keys.events.collect { (key, down) ->
            if (down) chord.down(key, SystemClock.uptimeMillis()) else chord.up(key)
            if (down && key == HardKey.UP && keys.mode == KeyMode.OFFICER_READY) show(false)
        }
    }
    LaunchedEffect(onStage) {
        while (onStage) {
            delay(50)
            if (chord.poll(SystemClock.uptimeMillis())) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                leave()
            }
        }
    }
}
