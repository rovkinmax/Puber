package com.kino.puber.domain.interactor.genre

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Genre

class GenreInteractor(private val api: KinoPubApiClient) {

    private var cachedGenres: List<Genre>? = null
    private var cacheTimestamp: Long = 0L

    suspend fun getGenres(): Result<List<Genre>> {
        val cached = cachedGenres
        if (cached != null && !isCacheExpired()) {
            return Result.success(cached)
        }
        return api.getGenres().onSuccess { genres ->
            cachedGenres = genres
            cacheTimestamp = System.currentTimeMillis()
        }
    }

    private fun isCacheExpired(): Boolean {
        return System.currentTimeMillis() - cacheTimestamp > CACHE_TTL_MS
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
