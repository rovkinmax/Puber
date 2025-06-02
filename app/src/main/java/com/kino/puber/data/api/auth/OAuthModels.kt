package com.kino.puber.data.api.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class TokenRequest(
    val code: String,
    val username: String,
    val timestamp: Long
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class OAuthError(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

data class DeviceFlowResult(
    val deviceCode: DeviceCodeResponse,
    val token: TokenResponse
) 