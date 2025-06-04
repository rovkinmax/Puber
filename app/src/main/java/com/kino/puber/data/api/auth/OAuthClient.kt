package com.kino.puber.data.api.auth

import com.kino.puber.data.api.config.KinoPubClientConfig
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.config.createHttpClient
import com.kino.puber.data.api.config.createOkHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
     * POST /oauth2/device?grant_type=device_code&client_id=myclient&client_secret=mysecret
     */
    suspend fun getDeviceCode(): Result<DeviceCodeResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_CODE)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<DeviceCodeResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Exchange device code for access token
     * POST /oauth2/device?grant_type=device_token&client_id=myclient&client_secret=mysecret&code=abcdefg
     */
    suspend fun getDeviceToken(
        code: String
    ): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_TOKEN)
            parameter("code", code)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<TokenResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Refresh access token using refresh token
     * POST /oauth2/token?grant_type=refresh_token&client_id=myclient&client_secret=mysecret&refresh_token=qwertyu12345678
     */
    suspend fun refreshToken(refreshToken: String): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}token") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_REFRESH_TOKEN)
            parameter("refresh_token", refreshToken)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<TokenResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Complete OAuth device flow with polling
     * Implements the complete flow as described in the official documentation
     * Returns Flow to emit intermediate states during the authentication process
     */
    fun completeDeviceFlow(
        pollingInterval: Long = 5000L,
        maxAttempts: Int = 60
    ): Flow<Result<DeviceFlowResult>> = flow {
        // Step 1: Get device code
        val deviceCodeResult = getDeviceCode()
        if (deviceCodeResult.isFailure) {
            emit(Result.failure(deviceCodeResult.exceptionOrNull()!!))
            return@flow
        }

        val deviceCode = deviceCodeResult.getOrThrow()

        emit(
            Result.success(
                DeviceFlowResult(
                    deviceCode = deviceCode,
                    token = null,
                )
            )
        )

        // Step 2: Poll for token
        // Use interval from API response (converted to milliseconds) or default
        val interval = (deviceCode.interval * 1000L).coerceAtLeast(pollingInterval)
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(interval)
            attempts++

            val tokenResult = getDeviceToken(
                code = deviceCode.code // Use 'code' field as per official documentation
            )

            if (tokenResult.isSuccess) {
                // Emit successful result and complete the flow
                emit(
                    Result.success(
                        DeviceFlowResult(
                            deviceCode = deviceCode,
                            token = tokenResult.getOrThrow()
                        )
                    )
                )
                return@flow
            }

            // Check if error is polling-related (authorization_pending)
            val error = tokenResult.exceptionOrNull()
            val isAuthorizationPending =
                error?.message?.contains("authorization_pending", true) ?: false

            if (!isAuthorizationPending) {
                // Emit non-recoverable error and complete the flow
                emit(Result.failure(error!!))
                return@flow
            }

            // Continue polling for authorization_pending errors
        }

        // Emit timeout error if max attempts reached
        emit(Result.failure(RuntimeException("Device flow timeout after $maxAttempts attempts")))
    }

    fun close() {
        httpClient.close()
    }

    /**
     * Safe handling of API responses with error detection
     */
    private suspend inline fun <reified T> handleApiResponse(response: HttpResponse): Result<T> {
        return try {
            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                return Result.failure(
                    Exception("HTTP ${response.status.value}: $errorText")
                )
            }

            val responseText = response.bodyAsText()

            // Проверяем на наличие ошибки в JSON
            if (responseText.contains("\"error\"")) {
                try {
                    val error = KinoPubConfig.json.decodeFromString<OAuthError>(responseText)
                    return Result.failure(
                        Exception("OAuth Error: ${error.error}${error.errorDescription?.let { " - $it" } ?: ""}")
                    )
                } catch (e: Exception) {
                    // Если не удалось распарсить как OAuthError, возвращаем сырой текст
                    return Result.failure(Exception("API Error: $responseText"))
                }
            }

            // Пытаемся десериализовать как ожидаемый тип
            val result = KinoPubConfig.json.decodeFromString<T>(responseText)
            Result.success(result)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun Boolean?.orElse(default: Boolean): Boolean = this ?: default 