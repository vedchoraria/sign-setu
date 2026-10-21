package app.signsetu.ui.session

import android.os.SystemClock
import android.util.Log

/** Measures from the officer's Show to the stage's first frame. Logs are stripped from release builds. */
internal object ShowTiming {
    @Volatile private var startedAt = 0L

    fun start() {
        startedAt = SystemClock.uptimeMillis()
    }

    fun firstFrame() {
        val start = startedAt
        if (start == 0L) return
        startedAt = 0L
        Log.d("SignSetuTiming", "show-to-stage ${SystemClock.uptimeMillis() - start} ms")
    }
}
