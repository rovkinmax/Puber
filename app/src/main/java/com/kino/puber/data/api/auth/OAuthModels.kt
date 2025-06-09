package com.kino.puber.data.api.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCodeResponse(
    val code: String,
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
    val code: String
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
    val token: TokenResponse?,
)

/**
 * Represents different states during OAuth Device Flow process
 */
sealed class DeviceFlowState {
    /**
     * Device code successfully obtained, user should visit verification URL
     */
    data class DeviceCodeObtained(
        val deviceCode: DeviceCodeResponse
    ) : DeviceFlowState()

    /**
     * Waiting for user authorization
     */
    data class WaitingForAuthorization(
        val attempt: Int,
        val maxAttempts: Int,
        val deviceCode: DeviceCodeResponse
    ) : DeviceFlowState()

    /**
     * Authentication completed successfully
     */
    data class Completed(
        val result: DeviceFlowResult
    ) : DeviceFlowState()

    /**
     * Error occurred during the process
     */
    data class Error(
        val exception: Throwable,
        val isRecoverable: Boolean = false
    ) : DeviceFlowState()
} 