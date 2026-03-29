package com.kino.puber.domain.interactor.genre

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Genre

class GenreInteractor(private val api: KinoPubApiClient) {

    private val cache = mutableMapOf<String?, CacheEntry>()

    suspend fun getGenres(type: String? = null): Result<List<Genre>> {
        val cached = cache[type]
        if (cached != null && !cached.isExpired()) {
            return Result.success(cached.genres)
        }
        return api.getGenres(type).onSuccess { genres ->
            cache[type] = CacheEntry(genres, System.currentTimeMillis())
        }
    }

    private data class CacheEntry(val genres: List<Genre>, val timestamp: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_TTL_MS
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
