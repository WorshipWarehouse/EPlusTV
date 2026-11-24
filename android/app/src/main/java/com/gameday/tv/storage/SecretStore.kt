package com.gameday.tv.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.gameday.tv.core.Diag
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials never touch disk in plain text. */
interface SecretStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

/**
 * AES/GCM envelope encryption with a non-exportable key held by the Android Keystore
 * (available on API 23+, so API 25 is fine). If the Keystore is unavailable the store fails
 * closed: the value is dropped rather than written in clear text.
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun put(key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                SEPARATOR + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(prefKey(key), payload).apply()
        } catch (e: Exception) {
            Diag.error("SecretStore", "Could not store secret: ${e.javaClass.simpleName}")
            prefs.edit().remove(prefKey(key)).apply()
        }
    }

    override fun get(key: String): String? {
        val payload = prefs.getString(prefKey(key), null) ?: return null
        return try {
            val parts = payload.split(SEPARATOR)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            Diag.warn("SecretStore", "Could not read secret: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun remove(key: String) {
        prefs.edit().remove(prefKey(key)).apply()
    }

    private fun prefKey(key: String): String = "secret.$key"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gameday_secret_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val SEPARATOR = ":"
        private const val PREFS = "gameday_secrets"
    }
}

class MemorySecretStore : SecretStore {
    private val values = HashMap<String, String>()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun get(key: String): String? = values[key]

    override fun remove(key: String) {
        values.remove(key)
    }
}
