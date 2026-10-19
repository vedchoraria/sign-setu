package app.signsetu.data.session

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database key (DEK) is random, and stored only wrapped by a non-exportable Keystore key (KEK).
 * The KEK is usable for a short window after the officer authenticates (SEC-01, SEC-22).
 */
class DatabaseKey(private val context: Context) {
    private val wrapped = File(context.noBackupFilesDir, "dek.bin")

    class Unwrapped(val key: ByteArray, val reset: Boolean) : AutoCloseable {
        fun wipe() {
            key.fill(0)
        }

        override fun close() {
            wipe()
        }
    }

    /**
     * Works for a short window after the officer unlocks the phone or passes a prompt; otherwise throws
     * UserNotAuthenticatedException and nothing is changed.
     * When the screen lock was removed or reset, Android has deleted or corrupted the KEK and stored sessions can never
     * be read again; they are destroyed and a fresh key is made, so the officer is not locked out for good.
     */
    fun unwrap(): Unwrapped = try {
        open()
    } catch (e: GeneralSecurityException) {
        if (e !is KeyPermanentlyInvalidatedException && e !is AEADBadTagException) throw e
        destroy()
        Unwrapped(open().key, reset = true)
    }

    private fun open(): Unwrapped {
        val kek = kek()
        if (!wrapped.exists()) {
            // A new DEK cannot read a database written under an old one.
            val lost = context.deleteDatabase(DB_NAME)
            val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, kek) }
            wrapped.writeBytes(cipher.iv + cipher.doFinal(dek))
            return Unwrapped(dek, reset = lost)
        }
        val bytes = wrapped.readBytes()
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, bytes, 0, IV_LEN))
        }
        return Unwrapped(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), reset = false)
    }

    /** Destroys both keys: every stored session becomes unreadable (Clear all data). */
    fun destroy() {
        context.deleteDatabase(DB_NAME)
        wrapped.delete()
        runCatching { keystore().deleteEntry(ALIAS); keystore().deleteEntry(LEGACY_ALIAS) }
    }

    private fun kek(): SecretKey {
        val keystore = keystore()
        // An entry Android can no longer decrypt (screen lock reset) is as good as missing.
        runCatching { (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey }.getOrNull()?.let { return it }
        runCatching { keystore.deleteEntry(ALIAS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        // Old "signsetu.kek" required screen-lock auth; a new alias avoids an unlock that can no longer prompt.
        const val ALIAS = "signsetu.kek.open"
        const val LEGACY_ALIAS = "signsetu.kek"
        const val DB_NAME = SessionDatabase.FILE_NAME
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
