package com.kino.puber.data.api.config

internal object TmdbImageConfig {

    fun resolveStillUrl(stillPath: String?): String {
        return resolveUrl(
            baseUrl = DEFAULT_IMAGE_BASE_URL,
            size = STILL_SIZE,
            path = stillPath,
        ).orEmpty()
    }

    fun resolveProfileUrl(
        secureBaseUrl: String?,
        profileSizes: List<String>,
        profilePath: String?,
    ): String? {
        val availableSizes = profileSizes.filter(String::isNotBlank)
        val profileSize = PREFERRED_PROFILE_SIZES.firstOrNull(availableSizes::contains)
            ?: availableSizes.firstOrNull()
        return resolveUrl(
            baseUrl = secureBaseUrl,
            size = profileSize,
            path = profilePath,
        )
    }

    private fun resolveUrl(
        baseUrl: String?,
        size: String?,
        path: String?,
    ): String? {
        val normalizedPath = path?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (normalizedPath.startsWith("http://", ignoreCase = true) ||
            normalizedPath.startsWith("https://", ignoreCase = true)
        ) {
            return normalizedPath
        }
        val normalizedBaseUrl = baseUrl?.trim()?.trimEnd('/')
        val normalizedSize = size?.trim()?.trim('/')?.takeIf(String::isNotEmpty)
        return if (normalizedBaseUrl == null || normalizedSize == null) {
            null
        } else {
            "$normalizedBaseUrl/$normalizedSize/${normalizedPath.trimStart('/')}"
        }
    }

    private const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    private const val STILL_SIZE = "w500"
    private val PREFERRED_PROFILE_SIZES = listOf("w185", "w342", "h632", "w500", "original")
}
