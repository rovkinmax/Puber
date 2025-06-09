package com.kino.puber.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal class CryptoPreferenceRepository(
    private val context: Context,
) : ICryptoPreferenceRepository {

    private val sharedPreferences by lazy {
        generateAndStoreKeyIfNecessary(SECURITY_KEY_ALIAS)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun saveAccessToken(token: String) = saveString(ACCESS_TOKEN_KEY_NAME, token)

    override fun getAccessToken(): String? = getString(ACCESS_TOKEN_KEY_NAME)

    override fun clearAccessToken() = saveString(ACCESS_TOKEN_KEY_NAME, null)

    override fun saveRefreshToken(token: String) = saveString(REFRESH_TOKEN_KEY_NAME, token)

    override fun getRefreshToken() = getString(REFRESH_TOKEN_KEY_NAME)

    override fun clearRefreshToken() = saveString(REFRESH_TOKEN_KEY_NAME, null)

    override fun saveUsername(userName: String) = saveString(USERNAME_KEY_NAME, userName)

    override fun getUsername(): String? = getString(USERNAME_KEY_NAME)

    override fun clearUsername() = saveString(USERNAME_KEY_NAME, null)

    private fun saveString(name: String, value: String?) {
        sharedPreferences.edit {
            val encrypted = encrypt(SECURITY_KEY_ALIAS, value.orEmpty())
            putString(name, encrypted)
        }
    }

    private fun getString(name: String): String? {
        val value = sharedPreferences.getString(name, null) ?: return null
        return decrypt(SECURITY_KEY_ALIAS, value)
    }

    private fun encrypt(alias: String, plainText: String?): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey = (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText?.toByteArray(Charsets.UTF_8))

        // Сохраняем IV + шифртекст (через Base64)
        val output = iv + cipherText
        return Base64.encodeToString(output, Base64.DEFAULT)
    }

    private fun decrypt(alias: String, encryptedText: String): String {
        if (encryptedText.isNullOrEmpty()) return ""
        val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
        val iv = decoded.sliceArray(0 until 12) // IV всегда 12 байт для GCM
        val cipherBytes = decoded.sliceArray(12 until decoded.size)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey = (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    private fun generateAndStoreKeyIfNecessary(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keySpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).run {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                setKeySize(256)
                build()
            }
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
    }

    companion object {
        private const val PREFS_NAME = "KINOPUBER_SECURE_PREFS"
        private const val ACCESS_TOKEN_KEY_NAME = "KINOPUBER_ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY_NAME = "KINOPUBER_REFRESH_TOKEN"
        private const val USERNAME_KEY_NAME = "KINOPUBER_USERNAME_KEY_NAME"
        private const val SECURITY_KEY_ALIAS = "SECURITY_KEY_ALIAS"
    }
}