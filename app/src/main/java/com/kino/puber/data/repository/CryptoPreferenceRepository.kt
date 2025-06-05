package com.kino.puber.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


internal class CryptoPreferenceRepository(
    private val context: Context,
) : ICryptoPreferenceRepository {

    private val sharedPreferences by lazy {
        val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return@lazy EncryptedSharedPreferences.create(
            /* context = */ context,
            /* fileName = */
            PREFS_NAME,
            /* masterKey = */
            masterKey,
            /* prefKeyEncryptionScheme = */
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            /* prefValueEncryptionScheme = */
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveAccessToken(token: String) = saveString(ACCESS_TOKEN_KEY_NAME, token)

    override fun getAccessToken(): String? = getString(ACCESS_TOKEN_KEY_NAME)

    override fun clearAccessToken() = saveString(ACCESS_TOKEN_KEY_NAME, null)

    override fun saveRefreshToken(token: String) = saveString(REFRESH_TOKEN_KEY_NAME, token)

    override fun getRefreshToken() = getString(REFRESH_TOKEN_KEY_NAME)

    override fun clearRefreshToken() = saveString(REFRESH_TOKEN_KEY_NAME, null)

    private fun saveString(name: String, value: String?) {
        sharedPreferences.edit {
            putString(name, value)
        }

    }

    private fun getString(name: String): String? =
        sharedPreferences.getString(name, null)

    companion object {
        private const val PREFS_NAME = "KINOPUBER_SECURE_PREFS"
        private const val ACCESS_TOKEN_KEY_NAME = "KINOPUBER_ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY_NAME = "KINOPUBER_REFRESH_TOKEN"
    }
}