package app.signsetu

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.signsetu.ui.components.GuardedAlertDialog
import app.signsetu.ui.session.OfficerSheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dialogs and sheets open their own windows; each must drop touches that come through another app's overlay. */
@RunWith(AndroidJUnit4::class)
class OverlayTouchTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialogWindowFiltersObscuredTouches() {
        var root: View? = null
        rule.setContent {
            GuardedAlertDialog(onDismissRequest = {}, confirmButton = {
                val view = LocalView.current
                SideEffect { root = view.rootView }
                Text("OK")
            })
        }
        rule.waitForIdle()
        assertTrue(root!!.filterTouchesWhenObscured)
    }

    @Test
    fun sheetWindowFiltersObscuredTouches() {
        var root: View? = null
        rule.setContent {
            OfficerSheet(onDismiss = {}) {
                val view = LocalView.current
                SideEffect { root = view.rootView }
                Text("Sheet")
            }
        }
        rule.waitForIdle()
        assertTrue(root!!.filterTouchesWhenObscured)
    }
}
