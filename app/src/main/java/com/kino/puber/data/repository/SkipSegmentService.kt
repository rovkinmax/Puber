package com.kino.puber.data.repository

import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.SkipSegment

class SkipSegmentService(
    private val tmdbApiClient: TmdbApiClient,
    private val introDbClient: TheIntroDbApiClient,
    private val introDbAppClient: IntroDbAppApiClient,
    private val tmdbIdRepository: TmdbIdRepository,
    private val segmentRepository: SkipSegmentRepository,
) {

    suspend fun getSegments(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        return segmentRepository.getOrLoad(imdbId, season, episode) {
            // 1. Try TheIntroDB (needs TMDB ID)
            val theIntroDbResult = tryTheIntroDB(imdbId, season, episode)
            if (theIntroDbResult.isNotEmpty()) {
                return@getOrLoad theIntroDbResult
            }

            // 2. Fallback: IntroDB.app (works with IMDb ID directly)
            introDbAppClient.getSegments(imdbId, season, episode).getOrDefault(emptyList())
        }
    }

    private suspend fun tryTheIntroDB(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        val tmdbId = resolveTmdbId(imdbId) ?: return emptyList()
        return introDbClient.getSegments(tmdbId, season, episode).getOrDefault(emptyList())
    }

    private suspend fun resolveTmdbId(imdbId: String): Int? {
        val cachedTmdbId = tmdbIdRepository.getTmdbId(imdbId)
        if (cachedTmdbId != null) {
            return cachedTmdbId
        }

        return tmdbApiClient.findByImdbId(imdbId).getOrNull()?.also { tmdbId ->
            tmdbIdRepository.saveTmdbId(imdbId, tmdbId)
        }
    }
}
