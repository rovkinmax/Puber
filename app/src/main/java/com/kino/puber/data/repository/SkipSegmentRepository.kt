package com.kino.puber.data.repository

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.models.SkipSegment
import kotlin.time.Duration.Companion.hours

class SkipSegmentRepository {

    private val cache: TypedTtlCache<String, List<SkipSegment>> = TypedTtlCacheImpl(defaultTtl = 24.hours)

    suspend fun getOrLoad(
        imdbId: String,
        season: Int?,
        episode: Int?,
        loader: suspend () -> List<SkipSegment>,
    ): List<SkipSegment> {
        val key = buildKey(imdbId, season, episode)
        return cache.getOrPut(key) {
            val result = loader()
            if (result.isEmpty()) {
                cache.put(key, emptyList(), ttl = 1.hours)
                emptyList()
            } else {
                result
            }
        }
    }

    fun clear() {
        cache.clear()
    }

    private fun buildKey(imdbId: String, season: Int?, episode: Int?): String {
        return if (season != null && episode != null) {
            "${imdbId}_s${season}_e${episode}"
        } else {
            imdbId
        }
    }
}
