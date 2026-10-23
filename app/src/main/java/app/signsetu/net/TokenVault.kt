package app.signsetu.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenVault {
    fun get(): String?
    /** False when the token could not be written; the caller must not claim enrolment succeeded. */
    fun put(token: String): Boolean
    fun clear()
}

/** The token is stored only wrapped by a non-exportable Keystore key, in a file excluded from backup. */
class KeystoreVault(context: Context) : TokenVault {
    private val file = File(context.noBackupFilesDir, "gw.bin")

    override fun get(): String? = runCatching {
        val b = file.takeIf { it.exists() }?.readBytes() ?: return null
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV_LEN)) }
            .doFinal(b, IV_LEN, b.size - IV_LEN).decodeToString()
    }.getOrElse {
        // A rotated or invalidated Keystore key, or a truncated file, can never be read again. Drop it so the
        // next enrolment starts clean instead of failing the same way on every launch.
        runCatching { file.delete() }
        null
    }

    override fun put(token: String): Boolean = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        // Written beside and renamed over, so a crash mid-write cannot leave a half token that never decrypts.
        val tmp = File(file.path + ".new").apply { writeBytes(cipher.iv + cipher.doFinal(token.toByteArray())) }
        if (!tmp.renameTo(file)) throw java.io.IOException("could not store the gateway token")
        true
    }.getOrElse { false }

    override fun clear() {
        file.delete()
        runCatching { keystore().deleteEntry(ALIAS) }
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    private companion object {
        const val ALIAS = "signsetu.gateway.token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
