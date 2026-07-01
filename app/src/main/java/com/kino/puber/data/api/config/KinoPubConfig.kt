package com.kino.puber.data.api.config

import android.util.Base64

data class ApiEndpointPreset(
    val domain: String,
    val apiHost: String,
    val mainBaseUrl: String,
    val oauthBaseUrl: String,
    val extraBaseUrl: String,
)

object KinoPubConfig {

    private val defaultDomain: String by lazy {
        val encoded = "=02bj5Ccr1SZjlmdyV2c"
        String(Base64.decode(encoded.reversed().toByteArray(), Base64.DEFAULT)).trim()
    }

    @Volatile
    private var domainOverride: String? = null

    private val defaultEndpoint: ApiEndpointPreset
        get() {
            val apiHost = "api.$defaultDomain"
            return ApiEndpointPreset(
                domain = defaultDomain,
                apiHost = apiHost,
                mainBaseUrl = "https://$apiHost/v1/",
                oauthBaseUrl = "https://$apiHost/oauth2/",
                extraBaseUrl = "https://$apiHost/",
            )
        }

    private val aladorEndpoint = ApiEndpointPreset(
        domain = "api.alador.space",
        apiHost = "api.alador.space",
        mainBaseUrl = "https://api.alador.space/v1/",
        oauthBaseUrl = "https://api.alador.space/oauth2/",
        extraBaseUrl = "https://api.alador.space/",
    )

    private val legacyCdnEndpoint = ApiEndpointPreset(
        domain = "cdn-service.online",
        apiHost = "cdn-service.online",
        mainBaseUrl = "https://cdn-service.online/api/v1/",
        oauthBaseUrl = "https://cdn-service.online/api/oauth2/",
        extraBaseUrl = "https://cdn-service.online/",
    )

    private val activeEndpoint: ApiEndpointPreset
        get() = domainOverride
            ?.let(::resolveEndpoint)
            ?: defaultEndpoint

    val DEFAULT_API_DOMAIN: String get() = defaultDomain
    val CURRENT_API_DOMAIN: String get() = activeEndpoint.domain
    val CURRENT_API_HOST: String get() = activeEndpoint.apiHost
    val CUSTOM_API_DOMAIN: String? get() = domainOverride
    val BUILT_IN_ENDPOINTS: List<ApiEndpointPreset>
        get() = listOf(defaultEndpoint, aladorEndpoint, legacyCdnEndpoint)

    val MAIN_API_BASE_URL: String get() = activeEndpoint.mainBaseUrl
    val OAUTH_BASE_URL: String get() = activeEndpoint.oauthBaseUrl
    val EXTRA_API_BASE_URL: String get() = activeEndpoint.extraBaseUrl

    const val GRANT_TYPE_DEVICE_CODE = "device_code"
    const val GRANT_TYPE_DEVICE_TOKEN = "device_token"
    const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"

    fun setDomainOverride(domain: String?) {
        domainOverride = domain
    }

    private fun resolveEndpoint(domain: String): ApiEndpointPreset {
        return BUILT_IN_ENDPOINTS.firstOrNull { endpoint ->
            endpoint.domain == domain || endpoint.apiHost == domain
        } ?: buildCustomMirrorEndpoint(domain)
    }

    private fun buildCustomMirrorEndpoint(domain: String): ApiEndpointPreset {
        val defaultEndpoint = defaultEndpoint
        return ApiEndpointPreset(
            domain = domain,
            apiHost = domain,
            mainBaseUrl = "https://$domain/v1/",
            oauthBaseUrl = defaultEndpoint.oauthBaseUrl,
            extraBaseUrl = defaultEndpoint.extraBaseUrl,
        )
    }
}
