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

/** Where the gateway token lives between runs. */
interface TokenVault {
    fun get(): String?
    fun put(token: String)
    fun clear()
}

/**
 * The token is stored only wrapped by a non-exportable Keystore key, in a file excluded from backup (SEC-11).
 * That key needs no screen unlock, so the token is the one secret the app can use while locked; it reaches only the
 * gateway's own endpoints and can be revoked there.
 */
class KeystoreVault(context: Context) : TokenVault {
    private val file = File(context.noBackupFilesDir, "gw.bin")

    override fun get(): String? = runCatching {
        val b = file.takeIf { it.exists() }?.readBytes() ?: return null
        Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV_LEN)) }
            .doFinal(b, IV_LEN, b.size - IV_LEN).decodeToString()
    }.getOrNull()

    override fun put(token: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        file.writeBytes(cipher.iv + cipher.doFinal(token.toByteArray()))
    }

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
