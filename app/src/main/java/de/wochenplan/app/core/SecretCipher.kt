package de.wochenplan.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschluesselt das CalDAV-Passwort mit einem Schluessel aus dem Android-Keystore.
 *
 * Der Schluessel verlaesst den Keystore nie; gespeichert wird nur der Geheimtext.
 * Damit steht das Passwort auch dann nicht im Klartext in den App-Daten, wenn ein
 * Geraet mit Root-Rechten ausgelesen wird.
 */
object SecretCipher {

    private const val TAG = "SecretCipher"
    private const val KEY_ALIAS = "wochenplan.caldav.password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "Passwort konnte nicht verschluesselt werden", it) }.getOrNull()

    fun decrypt(payload: String): String? = runCatching {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH),
        )
        String(cipher.doFinal(combined, IV_LENGTH, combined.size - IV_LENGTH), Charsets.UTF_8)
    }.onFailure { Log.w(TAG, "Passwort konnte nicht entschluesselt werden", it) }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
