package com.ottotech.workoutbuilder.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's Anthropic API key, encrypted at rest.
 *
 * The AES key never leaves the Android Keystore; only the IV and ciphertext are written to
 * SharedPreferences. Deliberately not `EncryptedSharedPreferences` — its library is
 * deprecated. Never log the plaintext.
 *
 * If the Keystore key is lost (e.g. the user cleared credentials), decryption fails and the
 * stored ciphertext is discarded: the user simply re-enters the key.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)

    val hasKey: Boolean get() = get() != null

    fun get(): String? {
        val stored = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun set(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_CIPHERTEXT, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "anthropic_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_CIPHERTEXT = "api_key"
    }
}
