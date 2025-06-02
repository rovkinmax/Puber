package com.kino.puber.data.api.auth

import com.kino.puber.data.api.config.KinoPubClientConfig
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.config.createHttpClient
import com.kino.puber.data.api.config.createOkHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.coroutines.delay

class OAuthClient(
    private var config: KinoPubClientConfig = KinoPubClientConfig()
) {
    private var okHttpClient = createOkHttpClient(config)
    private var httpClient: HttpClient = createHttpClient(config, okHttpClient)

    /**
     * Update configuration and recreate HTTP client
     */
    fun updateConfig(newConfig: KinoPubClientConfig) {
        if (config != newConfig) {
            httpClient.close()
            okHttpClient.connectionPool.evictAll()
            config = newConfig
            okHttpClient = createOkHttpClient(config)
            httpClient = createHttpClient(config, okHttpClient)
        }
    }

    /**
     * Get device code for OAuth device flow
     */
    suspend fun getDeviceCode(): Result<DeviceCodeResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_CODE)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        Result.success(response.body<DeviceCodeResponse>())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Exchange device code for access token
     */
    suspend fun getDeviceToken(
        code: String,
        username: String,
        timestamp: Long = System.currentTimeMillis()
    ): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_TOKEN)
            parameter("code", code)
            parameter("username", username)
            parameter("timestamp", timestamp.toString())
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }
        
        Result.success(response.body<TokenResponse>())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshToken(refreshToken: String): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_REFRESH_TOKEN)
            parameter("refresh_token", refreshToken)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        Result.success(response.body<TokenResponse>())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Complete OAuth device flow with polling
     */
    suspend fun completeDeviceFlow(
        username: String,
        pollingInterval: Long = 5000L,
        maxAttempts: Int = 60
    ): Result<DeviceFlowResult> {
        return try {
            // Step 1: Get device code
            val deviceCodeResult = getDeviceCode()
            if (deviceCodeResult.isFailure) {
                return Result.failure(deviceCodeResult.exceptionOrNull()!!)
            }

            val deviceCode = deviceCodeResult.getOrThrow()

            // Step 2: Poll for token
            var attempts = 0
            while (attempts < maxAttempts) {
                delay(pollingInterval)
                attempts++

                val tokenResult = getDeviceToken(
                    code = deviceCode.deviceCode,
                    username = username
                )

                if (tokenResult.isSuccess) {
                    return Result.success(
                        DeviceFlowResult(
                            deviceCode = deviceCode,
                            token = tokenResult.getOrThrow()
                        )
                    )
                }

                // Check if error is polling-related (authorization_pending)
                val error = tokenResult.exceptionOrNull()
                if (error != null && !error.message?.contains("authorization_pending", true)
                        .orElse(false)
                ) {
                    return Result.failure(error)
                }
            }

            Result.failure(RuntimeException("Device flow timeout after $maxAttempts attempts"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        httpClient.close()
    }
}

private fun Boolean?.orElse(default: Boolean): Boolean = this ?: default 