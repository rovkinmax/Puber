package com.kino.puber.core.ui.model

import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.repository.PlayerPreferencesRepository
import java.net.URI

class PosterUrlMapper(
    private val preferences: PlayerPreferencesRepository,
) {

    fun map(url: String?): String {
        return mapWithFallback(url).firstOrNull().orEmpty()
    }

    fun mapWithFallback(url: String?): List<String> {
        val secureUrl = url.orEmpty().ensureHttps()
        if (secureUrl.isBlank()) return emptyList()
        if (!preferences.posterProxyEnabled) return listOf(secureUrl)

        return listOfNotNull(secureUrl.toProxyUrl(), secureUrl).distinct()
    }

    private fun String.toProxyUrl(): String? {
        val uri = runCatching { URI(this) }.getOrNull() ?: return null
        val path = uri.rawPath.takeUnless { it.isNullOrBlank() } ?: return null
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "https://${KinoPubConfig.CURRENT_API_HOST}/cdn$path$query"
    }
}

private fun String.ensureHttps(): String {
    return if (startsWith("http://")) replaceFirst("http://", "https://") else this
}
