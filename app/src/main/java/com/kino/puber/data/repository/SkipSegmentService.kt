package com.kino.puber.data.repository

import com.kino.puber.core.logger.log
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
                log("SkipSegments: got ${theIntroDbResult.size} from TheIntroDB")
                return@getOrLoad theIntroDbResult
            }

            // 2. Fallback: IntroDB.app (works with IMDb ID directly)
            val introDbAppResult = introDbAppClient.getSegments(imdbId, season, episode).getOrDefault(emptyList())
            if (introDbAppResult.isNotEmpty()) {
                log("SkipSegments: got ${introDbAppResult.size} from IntroDB.app")
            } else {
                log("SkipSegments: no data from any source for imdb=$imdbId")
            }
            introDbAppResult
        }
    }

    private suspend fun tryTheIntroDB(imdbId: String, season: Int?, episode: Int?): List<SkipSegment> {
        val tmdbId = resolveTmdbId(imdbId) ?: return emptyList()
        return introDbClient.getSegments(tmdbId, season, episode).getOrDefault(emptyList())
    }

    private suspend fun resolveTmdbId(imdbId: String): Int? {
        tmdbIdRepository.getTmdbId(imdbId)?.let {
            log("SkipSegments: cached tmdbId=$it for imdb=$imdbId")
            return it
        }
        log("SkipSegments: resolving TMDB ID for imdb=$imdbId via API...")
        val result = tmdbApiClient.findByImdbId(imdbId)
        if (result.isFailure) {
            log("SkipSegments: TMDB API error: ${result.exceptionOrNull()?.message}")
            return null
        }
        val tmdbId = result.getOrNull() ?: run {
            log("SkipSegments: TMDB returned no match for imdb=$imdbId")
            return null
        }
        log("SkipSegments: resolved tmdbId=$tmdbId for imdb=$imdbId")
        tmdbIdRepository.saveTmdbId(imdbId, tmdbId)
        return tmdbId
    }
}
