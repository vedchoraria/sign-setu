package app.signsetu

import android.content.Context
import app.signsetu.data.settings.OfficerLocale
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import app.signsetu.ui.session.HardKey
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import app.signsetu.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(OfficerLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the recents thumbnail.
        // Debug builds skip it so UI tests can capture screens; only release builds are ever distributed.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        // Drops taps that pass through an overlay drawn by another app.
        window.decorView.filterTouchesWhenObscured = true
        enableEdgeToEdge()
        val container = (application as SignSetuApp).container
        setContent { AppRoot(container) }
    }

    /** Volume keys drive the handover only when a session asks for them, and never under TalkBack, which uses them. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent) = hardKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = hardKey(event) || super.onKeyUp(keyCode, event)

    private fun hardKey(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> HardKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> HardKey.DOWN
            else -> null
        }
        val keys = (application as SignSetuApp).container.hardKeys
        val talkBack = getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        if (key == null || talkBack || !keys.consumes(key)) return false
        if (event.repeatCount == 0) keys.events.tryEmit(key to (event.action == KeyEvent.ACTION_DOWN))
        return true
    }
}
