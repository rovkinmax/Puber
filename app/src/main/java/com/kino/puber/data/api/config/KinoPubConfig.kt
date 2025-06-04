package com.kino.puber.data.api.config

import android.content.Context
import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.CurlLoggingInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

object KinoPubConfig {
    const val MAIN_API_BASE_URL = "https://api.service-kp.com/v1/"
    const val OAUTH_BASE_URL = "https://api.service-kp.com/oauth2/"
    const val EXTRA_API_BASE_URL = "https://api.service-kp.com/"

    const val CLIENT_ID = "android"
    const val CLIENT_SECRET = BuildConfig.CLIENT_SECRET
    const val GRANT_TYPE_DEVICE_CODE = "device_code"
    const val GRANT_TYPE_DEVICE_TOKEN = "device_token"
    const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
}

/**
 * OkHttp Interceptor для автоматического добавления CLIENT_ID, CLIENT_SECRET как query параметров
 */
class KinoPubParametersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Проверяем, является ли это OAuth запросом по URL
        val isOAuthRequest = originalRequest.url.toString().contains("/oauth2/")

        if (!isOAuthRequest) {
            return chain.proceed(originalRequest)
        }

        // Добавляем CLIENT_ID и CLIENT_SECRET как query параметры
        val urlBuilder = originalRequest.url.newBuilder()

        // Проверяем, есть ли уже эти параметры в URL
        val existingClientId = originalRequest.url.queryParameter("client_id")
        val existingClientSecret = originalRequest.url.queryParameter("client_secret")

        if (existingClientId == null) {
            urlBuilder.addQueryParameter("client_id", KinoPubConfig.CLIENT_ID)
        }
        if (existingClientSecret == null) {
            urlBuilder.addQueryParameter("client_secret", KinoPubConfig.CLIENT_SECRET)
        }

        val newRequest = originalRequest.newBuilder()
            .url(urlBuilder.build())
            .build()

        return chain.proceed(newRequest)
    }
}

data class KinoPubClientConfig(
    val enableLogging: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val connectTimeout: Long = 60_000,
    val requestTimeout: Long = 120_000,
    val socketTimeout: Long = 120_000,
    val context: Context? = null,
    val username: String? = null,
    val retryOnFailure: Boolean = true,
    val maxRetries: Int = 3
) {
    /**
     * Generates User-Agent string based on device info and username
     * Format: kinopub/{version} device/{model} os/Android{version} username/{username}
     */
    fun getUserAgent(): String {
        return UserAgentBuilder.build(username)
    }
}

/**
 * Создает OkHttp клиент с необходимыми interceptors
 */
fun createOkHttpClient(config: KinoPubClientConfig = KinoPubClientConfig()): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(config.requestTimeout, TimeUnit.MILLISECONDS)
        .writeTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
        .addInterceptor(KinoPubParametersInterceptor())
        .addInterceptor(CurlLoggingInterceptor(BuildConfig.DEBUG))
        .build()
}

fun createHttpClient(
    config: KinoPubClientConfig = KinoPubClientConfig(),
    okHttpClient: OkHttpClient = createOkHttpClient(config),
    accessToken: String? = null
): HttpClient = HttpClient(OkHttp) {

    // OkHttp engine configuration
    engine {
        preconfigured = okHttpClient
    }

    // Request timeout configuration (Ktor level)
    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeout
        requestTimeoutMillis = config.requestTimeout
        socketTimeoutMillis = config.socketTimeout
    }

    // JSON serialization
    install(ContentNegotiation) {
        json(KinoPubConfig.json)
    }

    // Logging
    if (config.enableLogging) {
        install(Logging) {
            level = config.logLevel
            logger = Logger.DEFAULT
        }
    }

    // Default request configuration
    install(DefaultRequest) {
        headers {
            append("User-Agent", config.getUserAgent())
            append("Accept", "application/json")
            append("Content-Type", "application/json")
        }
    }

    // Authentication if token provided
    if (accessToken != null) {
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(accessToken, "")
                }
            }
        }
    }

    // Retry on failure
    if (config.retryOnFailure) {
        install(HttpRequestRetry) {
            maxRetries = config.maxRetries
            retryIf { _, response ->
                response.status.value >= 500
            }
            exponentialDelay()
        }
    }
} 