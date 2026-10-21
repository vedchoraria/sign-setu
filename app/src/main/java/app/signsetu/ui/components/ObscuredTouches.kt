package app.signsetu.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties

/**
 * Dialogs, sheets and menus open windows of their own, which do not inherit the activity's touch filter.
 * Call this inside one to drop touches that arrive through another app's overlay there too.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    // The window's root view runs the check; Compose's own view handles touches without it.
    SideEffect { view.rootView.filterTouchesWhenObscured = true }
}

/** The app's only alert dialog: Material's, with the overlay touch filter applied to its window. */
@Composable
fun GuardedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { FilterObscuredTouches(); confirmButton() },
    dismissButton = dismissButton,
    icon = icon,
    title = title,
    text = text,
    properties = properties,
)
