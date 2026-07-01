package com.kino.puber.domain.interactor.api

import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.genre.GenreInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class ApiDomainState(
    val domain: String,
    val customDomain: String?,
) {
    val isCustom: Boolean get() = customDomain != null
}

internal sealed interface ApiDomainUpdateResult {
    data class Success(val state: ApiDomainState) : ApiDomainUpdateResult
    data object Empty : ApiDomainUpdateResult
    data object Invalid : ApiDomainUpdateResult
}

internal sealed interface ApiDomainDetectionResult {
    data class Success(val state: ApiDomainState) : ApiDomainDetectionResult
    data object NotFound : ApiDomainDetectionResult
}

internal class ApiDomainInteractor(
    private val preferences: ICryptoPreferenceRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val genreInteractor: GenreInteractor,
    okHttpClient: OkHttpClient,
) {
    private val probeJson = Json { ignoreUnknownKeys = true }

    private val probeClient = okHttpClient.newBuilder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun initialize() {
        KinoPubConfig.setDomainOverride(preferences.getApiDomain().toValidDomainOrNull())
    }

    fun getState(): ApiDomainState {
        val customDomain = KinoPubConfig.CUSTOM_API_DOMAIN
        return ApiDomainState(
            domain = customDomain ?: KinoPubConfig.DEFAULT_API_DOMAIN,
            customDomain = customDomain,
        )
    }

    fun saveCustomDomain(input: String): ApiDomainUpdateResult {
        val normalized = normalizeDomain(input)
        if (normalized.isEmpty()) return ApiDomainUpdateResult.Empty
        if (!normalized.isValidHostname()) return ApiDomainUpdateResult.Invalid

        preferences.saveApiDomain(normalized)
        KinoPubConfig.setDomainOverride(normalized)
        clearDomainSensitiveCaches()
        return ApiDomainUpdateResult.Success(getState())
    }

    suspend fun detectAndSaveWorkingDomain(): ApiDomainDetectionResult = withContext(Dispatchers.IO) {
        val preset = KinoPubConfig.BUILT_IN_ENDPOINTS.firstOrNull(::isEndpointReachable)
            ?: return@withContext ApiDomainDetectionResult.NotFound

        preferences.saveApiDomain(preset.domain.takeIf { it != KinoPubConfig.DEFAULT_API_DOMAIN })
        KinoPubConfig.setDomainOverride(preset.domain.takeIf { it != KinoPubConfig.DEFAULT_API_DOMAIN })
        clearDomainSensitiveCaches()
        ApiDomainDetectionResult.Success(getState())
    }

    fun resetToDefault(): ApiDomainState {
        preferences.saveApiDomain(null)
        KinoPubConfig.setDomainOverride(null)
        clearDomainSensitiveCaches()
        return getState()
    }

    private fun String?.toValidDomainOrNull(): String? {
        val normalized = normalizeDomain(this.orEmpty())
        return normalized.takeIf { it.isNotEmpty() && it.isValidHostname() }
    }

    private fun clearDomainSensitiveCaches() {
        itemDetailsRepository.clear()
        genreInteractor.clearCache()
    }

    private fun isEndpointReachable(endpoint: com.kino.puber.data.api.config.ApiEndpointPreset): Boolean {
        val request = Request.Builder()
            .url("${endpoint.mainBaseUrl}items/fresh?type=movie")
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            probeClient.newCall(request).execute().use { response ->
                response.code in MIN_REACHABLE_STATUS..MAX_REACHABLE_STATUS &&
                    response.code != HTTP_NOT_FOUND &&
                    response.isKinoPubApiResponse()
            }
        }.getOrDefault(false)
    }

    private fun Response.isKinoPubApiResponse(): Boolean {
        val contentType = header("Content-Type").orEmpty()
        if (!contentType.contains(JSON_CONTENT_TYPE, ignoreCase = true)) return false

        val root = runCatching {
            probeJson.parseToJsonElement(body.string()).jsonObject
        }.getOrNull() ?: return false

        return root.hasPaginatedItems() || root.hasApiError()
    }

    private fun JsonObject.hasPaginatedItems(): Boolean {
        return containsKey(API_ITEMS_FIELD) && containsKey(API_PAGINATION_FIELD)
    }

    private fun JsonObject.hasApiError(): Boolean {
        return containsKey(API_STATUS_FIELD) &&
            (containsKey(API_ERROR_FIELD) || containsKey(API_MESSAGE_FIELD))
    }

    private companion object {
        private const val MAX_HOSTNAME_LENGTH = 253
        private const val MIN_DOMAIN_PARTS = 2
        private const val MAX_LABEL_LENGTH = 63
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val MIN_REACHABLE_STATUS = 200
        private const val MAX_REACHABLE_STATUS = 499
        private const val HTTP_NOT_FOUND = 404
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val API_ITEMS_FIELD = "items"
        private const val API_PAGINATION_FIELD = "pagination"
        private const val API_STATUS_FIELD = "status"
        private const val API_ERROR_FIELD = "error"
        private const val API_MESSAGE_FIELD = "message"

        fun normalizeDomain(input: String): String {
            return input
                .trim()
                .lowercase(Locale.US)
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
                .trim('.')
        }

        fun String.isValidHostname(): Boolean {
            if (length > MAX_HOSTNAME_LENGTH) return false

            val labels = split(".")
            if (labels.size < MIN_DOMAIN_PARTS || labels.any(String::isEmpty)) return false

            return labels.all { label ->
                label.length <= MAX_LABEL_LENGTH &&
                    label.first().isLetterOrDigit() &&
                    label.last().isLetterOrDigit() &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }
        }
    }
}
