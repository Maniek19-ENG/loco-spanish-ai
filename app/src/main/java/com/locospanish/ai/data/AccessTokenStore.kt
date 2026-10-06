package com.locospanish.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Personal Gemini key, encrypted with Android Keystore; never embedded in the APK. */
class AccessTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("gemini_access", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("loco-gemini-access", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("loco-gemini-access", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(value: String) {
        if (value.isBlank()) { prefs.edit { clear() }; return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit {
            putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString("value", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
        }
    }
    fun read(): String = runCatching {
        val raw = prefs.getString("value", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP))) }
        String(cipher.doFinal(Base64.decode(raw, Base64.NO_WRAP)))
    }.getOrDefault("")
}
