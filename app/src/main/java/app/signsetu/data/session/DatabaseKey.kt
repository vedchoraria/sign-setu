package app.signsetu.data.session

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import java.io.File
import java.io.IOException
import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The database key (DEK) is random, and stored only wrapped by a non-exportable Keystore key (KEK). The KEK
 * needs no prompt (OfficerAuth guards the app) but only works while the phone is unlocked. */
class DatabaseKey(private val context: Context) {
    private val wrapped = File(context.noBackupFilesDir, "dek.bin")

    /** Closing wipes the key bytes. */
    class Unwrapped(val key: ByteArray, val reset: Boolean) : AutoCloseable {
        override fun close() = key.fill(0)
    }

    /** True while the phone has no secure lock screen: the DEK is then wrapped by a key not tied to unlocking, and
     * is moved under the unlocked-only key at the first unlock after a screen lock is set. */
    @Volatile var unbound = false
        private set

    /** Works only while the phone is unlocked; otherwise throws and nothing is changed. When the screen lock was
     * removed or reset, Android has deleted or corrupted the KEK and stored sessions can never be read again. */
    fun unwrap(): Unwrapped = try {
        open()
    } catch (e: GeneralSecurityException) {
        // Only a key Android invalidated (or an unlocked-only key once the lock screen is gone), or a wrapped DEK no
        // key opens, means the records are gone; anything else may pass, so it is thrown and the officer retries.
        val gone = e is KeyPermanentlyInvalidatedException || e is AEADBadTagException ||
            (e is InvalidKeyException && !context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        if (!gone) throw e
        destroy()
        Unwrapped(open().key, reset = true)
    }

    private fun open(): Unwrapped {
        val secure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        // Earlier builds kept an unbound fallback under ALIAS itself; while there is no lock screen it stays in use.
        val bound = if (secure || keystore().containsAlias(ALIAS)) existingOrNew(ALIAS, unlockedOnly = true) else null
        if (bound != null && wrapped.exists()) rewrapFromOpenKey(bound)
        val kek = bound ?: existingOrNew(OPEN_ALIAS, unlockedOnly = false)!!
        unbound = !secure || bound == null
        if (!wrapped.exists()) {
            // A new DEK cannot read a database written under an old one.
            val lost = context.deleteDatabase(DB_NAME)
            val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
            write(kek, dek)
            return Unwrapped(dek, reset = lost)
        }
        return Unwrapped(decrypt(kek), reset = false)
    }

    private fun decrypt(kek: SecretKey): ByteArray {
        val bytes = wrapped.readBytes()
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, bytes, 0, IV_LEN))
        }
        return cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN)
    }

    /** Written beside and renamed over, so an interrupted write never leaves a half key file. */
    private fun write(kek: SecretKey, dek: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, kek) }
        val tmp = File(wrapped.path + ".new").apply { writeBytes(cipher.iv + cipher.doFinal(dek)) }
        if (!tmp.renameTo(wrapped)) throw IOException("could not store the database key")
    }

    /** Moves a DEK held by the key not tied to unlocking under the unlocked-only key, keeping stored sessions. */
    private fun rewrapFromOpenKey(bound: SecretKey) {
        val keystore = keystore()
        val old = (keystore.getEntry(OPEN_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return
        // A bad tag means an interrupted earlier run already rewrapped the DEK.
        val dek = try { decrypt(old) } catch (_: AEADBadTagException) { keystore.deleteEntry(OPEN_ALIAS); return }
        try {
            write(bound, dek)
            keystore.deleteEntry(OPEN_ALIAS)
        } finally {
            dek.fill(0)
        }
    }

    /** Destroys both keys: every stored session becomes unreadable (Clear all data). */
    fun destroy() {
        context.deleteDatabase(DB_NAME)
        wrapped.delete()
        runCatching { listOf(ALIAS, OPEN_ALIAS, LEGACY_ALIAS).forEach { keystore().deleteEntry(it) } }
    }

    /** The key under [alias], made when missing. A transient Keystore error is thrown, never taken as a missing key,
     * since replacing the key would lose every session; null when this phone refuses an unlocked-only key. */
    private fun existingOrNew(alias: String, unlockedOnly: Boolean): SecretKey? {
        val keystore = keystore()
        if (keystore.containsAlias(alias)) {
            try {
                (keystore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
            } catch (_: UnrecoverableKeyException) {
                // The screen lock was reset and Android can no longer use this key: it is as good as missing.
            }
            keystore.deleteEntry(alias)
        }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(unlockedOnly)
            .build()
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
        } catch (e: GeneralSecurityException) {
            // Some builds refuse the unlocked-only flag even with a lock screen; the open key then serves until they don't.
            if (unlockedOnly) null else throw e
        }
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        // Old "signsetu.kek" required screen-lock auth; "signsetu.kek.open" is not tied to unlocking and is moved off.
        const val ALIAS = "signsetu.kek.unlocked"
        const val OPEN_ALIAS = "signsetu.kek.open"
        const val LEGACY_ALIAS = "signsetu.kek"
        const val DB_NAME = SessionDatabase.FILE_NAME
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
