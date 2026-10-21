package app.signsetu.data.session

import android.content.Context
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
 * The KEK needs no prompt but only works while the phone is unlocked.
 */
class DatabaseKey(private val context: Context) {
    private val wrapped = File(context.noBackupFilesDir, "dek.bin")

    /** Closing wipes the key bytes. */
    class Unwrapped(val key: ByteArray, val reset: Boolean) : AutoCloseable {
        override fun close() = key.fill(0)
    }

    /**
     * Works only while the phone is unlocked; otherwise throws and nothing is changed.
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
        if (wrapped.exists()) migrateFromOpenKey()
        val kek = kek()
        if (!wrapped.exists()) {
            // A new DEK cannot read a database written under an old one.
            val lost = context.deleteDatabase(DB_NAME)
            val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, kek) }
            wrapped.writeBytes(cipher.iv + cipher.doFinal(dek))
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

    /** Rewraps a DEK held by the earlier prompt-free key under the unlocked-device key, keeping stored sessions. */
    private fun migrateFromOpenKey() {
        val keystore = keystore()
        val old = (keystore.getEntry(OPEN_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return
        // A bad tag means an interrupted earlier run already rewrapped the DEK.
        val dek = try { decrypt(old) } catch (_: AEADBadTagException) { keystore.deleteEntry(OPEN_ALIAS); return }
        try {
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, kek()) }
            val tmp = File(wrapped.path + ".new").apply { writeBytes(cipher.iv + cipher.doFinal(dek)) }
            check(tmp.renameTo(wrapped))
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

    private fun kek(): SecretKey {
        val keystore = keystore()
        // An entry Android can no longer decrypt (screen lock reset) is as good as missing.
        runCatching { (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey }.getOrNull()?.let { return it }
        runCatching { keystore.deleteEntry(ALIAS) }
        fun generate(unlockedOnly: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setUnlockedDeviceRequired(unlockedOnly)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(spec)
                generateKey()
            }
        }
        // Some builds refuse the unlocked-device flag without a secure lock screen; the app then keeps working unbound.
        return runCatching { generate(unlockedOnly = true) }.getOrElse { generate(unlockedOnly = false) }
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        // Old "signsetu.kek" required screen-lock auth; "signsetu.kek.open" had no binding and is migrated.
        const val ALIAS = "signsetu.kek.unlocked"
        const val OPEN_ALIAS = "signsetu.kek.open"
        const val LEGACY_ALIAS = "signsetu.kek"
        const val DB_NAME = SessionDatabase.FILE_NAME
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
