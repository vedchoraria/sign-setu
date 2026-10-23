package app.signsetu.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Turns sun mode on after the light stays above [onLux] for [onMs] and off after it stays below [offLux] for
 * [offMs], so a passing shadow or a hand over the sensor never flickers the stage. */
class SunHysteresis(
    val onLux: Float = 10_000f,
    val onMs: Long = 3_000,
    val offLux: Float = 2_000f,
    val offMs: Long = 10_000,
) {
    var on = false
        private set
    private var since: Long? = null

    fun update(lux: Float, tMs: Long): Boolean {
        val pushing = if (on) lux < offLux else lux > onLux
        if (!pushing) {
            since = null
        } else {
            val start = since ?: tMs.also { since = it }
            if (tMs - start >= if (on) offMs else onMs) {
                on = !on
                since = null
            }
        }
        return on
    }
}

/** The officer's manual switch; it holds for the app's lifetime, in memory only. */
object SunMode {
    val forced = mutableStateOf(false)
}

/** True while the room is bright enough for sun mode; false on phones without a light sensor. */
@Composable
fun rememberSunMode(): State<Boolean> {
    val context = LocalContext.current
    val sun = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val hysteresis = SunHysteresis()
        val handler = Handler(Looper.getMainLooper())
        var lux = 0f
        val feed = Runnable { sun.value = hysteresis.update(lux, SystemClock.elapsedRealtime()) }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lux = event.values[0]
                feed.run()
                // The sensor reports only changes, so a steady reading is fed once more after the hold time.
                handler.removeCallbacks(feed)
                handler.postDelayed(feed, (if (hysteresis.on) hysteresis.offMs else hysteresis.onMs) + 50)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            manager?.unregisterListener(listener)
            handler.removeCallbacks(feed)
        }
    }
    return sun
}
