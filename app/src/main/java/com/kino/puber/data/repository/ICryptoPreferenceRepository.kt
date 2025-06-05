package com.kino.puber.data.repository

interface ICryptoPreferenceRepository {

    fun saveAccessToken(token: String)
    fun getAccessToken(): String?
    fun clearAccessToken()
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String?
    fun clearRefreshToken()
}
