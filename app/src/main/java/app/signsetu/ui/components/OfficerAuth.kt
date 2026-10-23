package app.signsetu.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import app.signsetu.R

/** The officer's own screen lock (PIN, pattern, password or strong biometric) before the record is opened, left,
 * ended, exported or deleted. Never asked during a conversation. */
object OfficerAuth {
    // One confirmation covers the next few gated steps, so "End" then "Export" asks once.
    private const val GRACE_MS = 60_000L
    @Volatile private var okAt = 0L

    /** Forget the last confirmation; the app locked. */
    fun reset() { okAt = 0L }

    /** Runs [onOk] once the officer confirms, or at once without a secure lock screen (nothing to ask for). */
    fun confirm(activity: Activity?, onOk: () -> Unit, onFail: () -> Unit = {}) {
        if (activity == null) { onFail(); return }
        if (okAt != 0L && SystemClock.elapsedRealtime() - okAt < GRACE_MS) { onOk(); return }
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) { onOk(); return }
        val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.auth_title))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    okAt = SystemClock.elapsedRealtime()
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            })
        }.onFailure { onFail() }
    }
}
