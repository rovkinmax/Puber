package com.kino.puber.data.repository

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.SkipSegment

class SkipSegmentService(
    private val tmdbApiClient: TmdbApiClient,
    private val introDbClient: TheIntroDbApiClient,
    private val tmdbIdRepository: TmdbIdRepository,
    private val segmentRepository: SkipSegmentRepository,
) {

    suspend fun getSegments(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        val tmdbId = resolveTmdbId(imdbId) ?: return emptyList()
        return segmentRepository.getOrLoad(tmdbId, season, episode) {
            introDbClient.getSegments(tmdbId, season, episode).getOrDefault(emptyList())
        }
    }

    private suspend fun resolveTmdbId(imdbId: String): Int? {
        tmdbIdRepository.getTmdbId(imdbId)?.let { return it }
        val tmdbId = tmdbApiClient.findByImdbId(imdbId).getOrNull() ?: run {
            log("TMDB ID not found for imdb=$imdbId")
            return null
        }
        tmdbIdRepository.saveTmdbId(imdbId, tmdbId)
        return tmdbId
    }
}
