package com.kino.puber.data.api.config

internal object TmdbImageConfig {

    fun resolveStillUrl(stillPath: String?): String {
        if (stillPath.isNullOrBlank()) return ""
        if (stillPath.startsWith("http://", ignoreCase = true) ||
            stillPath.startsWith("https://", ignoreCase = true)
        ) {
            return stillPath
        }
        return "$W500_BASE_URL/${stillPath.trim().trimStart('/')}"
    }

    private const val W500_BASE_URL = "https://image.tmdb.org/t/p/w500"
}
