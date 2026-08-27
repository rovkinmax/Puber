package com.kino.puber.data.repository

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.TmdbEpisodeResponse
import com.kino.puber.data.api.models.TmdbSeasonDetailsResponse
import com.kino.puber.data.api.models.TmdbSeasonSummaryResponse
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.domain.model.ScheduledEpisode
import com.kino.puber.domain.model.ScheduledSeason
import com.kino.puber.domain.model.ScheduleProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate as JavaLocalDate
import kotlin.time.Duration.Companion.hours

class EpisodeScheduleRepository(
    private val tmdbApiClient: TmdbApiClient,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: () -> LocalDate = {
        JavaLocalDate.now().toKotlinLocalDate()
    },
    private val cache: TypedTtlCache<String, EpisodeScheduleResult> = TypedTtlCacheImpl(
        defaultTtl = CACHE_TTL,
    ),
) {

    suspend fun getSchedule(imdbId: String): EpisodeScheduleResult {
        if (!tmdbApiClient.isConfigured) {
            return EpisodeScheduleResult.MissingCredentials
        }

        val normalizedImdbId = normalizeImdbId(imdbId)
        val currentDate = today()
        return cache.getOrPut(cacheKey(normalizedImdbId, currentDate)) {
            withContext(workerDispatcher) {
                loadSchedule(normalizedImdbId, currentDate)
            }
        }
    }

    private suspend fun loadSchedule(
        imdbId: String,
        currentDate: LocalDate,
    ): EpisodeScheduleResult = tmdbApiClient.findTvByImdbId(imdbId).getOrThrow()
        ?.let { seriesId ->
            loadMatchedSchedule(seriesId, currentDate)
        }
        ?: EpisodeScheduleResult.NoMatch

    private suspend fun loadMatchedSchedule(
        seriesId: Int,
        currentDate: LocalDate,
    ): EpisodeScheduleResult {
        val details = tmdbApiClient.getTvDetails(seriesId).getOrThrow()
        val candidateSeasons = candidateSeasons(details.seasons, details.nextEpisodeToAir, currentDate)
        return if (candidateSeasons.isEmpty()) {
            EpisodeScheduleResult.NoUpcomingReleases
        } else {
            candidateSeasons
                .map { candidate ->
                    val season = tmdbApiClient.getTvSeasonDetails(seriesId, candidate.seasonNumber).getOrThrow()
                    mapSeason(
                        candidate = candidate,
                        season = season,
                        currentDate = currentDate,
                    )
                }
                .filter { season ->
                    season.episodes.isNotEmpty() || season.announcementDate != null
                }
                .sortedBy { it.seasonNumber }
                .toScheduleResult()
        }
    }

    private fun candidateSeasons(
        summaries: List<TmdbSeasonSummaryResponse>,
        nextEpisode: TmdbEpisodeResponse?,
        currentDate: LocalDate,
    ): List<CandidateSeason> {
        val candidates = linkedMapOf<Int, CandidateSeason>()

        summaries.forEach { summary ->
            val seasonNumber = summary.seasonNumber ?: return@forEach
            if (seasonNumber <= 0) return@forEach
            val announcementDate = summary.airDate.parseDateOrNull()
                ?.takeIf { it >= currentDate }
            if (announcementDate != null) {
                candidates[seasonNumber] = CandidateSeason(seasonNumber, announcementDate)
            }
        }

        nextEpisode?.seasonNumber
            ?.takeIf { it > 0 }
            ?.let { seasonNumber ->
                val existing = candidates[seasonNumber]
                if (existing == null) {
                    candidates[seasonNumber] = CandidateSeason(seasonNumber, null)
                }
            }

        return candidates.values.toList()
    }

    private fun mapSeason(
        candidate: CandidateSeason,
        season: TmdbSeasonDetailsResponse,
        currentDate: LocalDate,
    ): ScheduledSeason {
        val announcementDate = listOfNotNull(
            candidate.announcementDate,
            season.airDate.parseDateOrNull()?.takeIf { it >= currentDate },
        ).minOrNull()
        val episodes = season.episodes
            .asSequence()
            .mapNotNull { episode ->
                episode.toScheduledEpisodeOrNull(
                    fallbackSeasonNumber = season.seasonNumber ?: candidate.seasonNumber,
                    currentDate = currentDate,
                )
            }
            .sortedWith(compareBy<ScheduledEpisode> { it.seasonNumber }.thenBy { it.episodeNumber })
            .distinctBy { it.seasonNumber to it.episodeNumber }
            .toList()

        return ScheduledSeason(
            seasonNumber = candidate.seasonNumber,
            announcementDate = announcementDate,
            episodes = episodes,
        )
    }

    private fun TmdbEpisodeResponse.toScheduledEpisodeOrNull(
        fallbackSeasonNumber: Int,
        currentDate: LocalDate,
    ): ScheduledEpisode? {
        val resolvedSeasonNumber = seasonNumber ?: fallbackSeasonNumber
        return episodeNumber
            ?.takeIf { resolvedSeasonNumber > 0 && it > 0 }
            ?.let { resolvedEpisodeNumber ->
                airDate.parseDateOrNull()
                    ?.takeIf { it >= currentDate }
                    ?.let { resolvedAirDate ->
                        ScheduledEpisode(
                            seasonNumber = resolvedSeasonNumber,
                            episodeNumber = resolvedEpisodeNumber,
                            title = name,
                            airDate = resolvedAirDate,
                            stillPath = stillPath,
                        )
                    }
            }
    }

    private fun List<ScheduledSeason>.toScheduleResult(): EpisodeScheduleResult {
        return takeIf { it.isNotEmpty() }
            ?.let { seasons ->
                EpisodeScheduleResult.Available(
                    EpisodeSchedule(
                        provider = ScheduleProvider.TMDB,
                        seasons = seasons,
                    ),
                )
            }
            ?: EpisodeScheduleResult.NoUpcomingReleases
    }

    private fun String?.parseDateOrNull(): LocalDate? {
        return this?.let { value ->
            runCatching { LocalDate.parse(value) }.getOrNull()
        }
    }

    private fun normalizeImdbId(imdbId: String): String {
        val value = imdbId.trim()
        return if (value.startsWith(IMDB_PREFIX, ignoreCase = true)) {
            IMDB_PREFIX + value.drop(IMDB_PREFIX.length)
        } else {
            IMDB_PREFIX + value
        }
    }

    private fun cacheKey(
        normalizedImdbId: String,
        currentDate: LocalDate,
    ): String = "$normalizedImdbId|$currentDate"

    private data class CandidateSeason(
        val seasonNumber: Int,
        val announcementDate: LocalDate?,
    )

    private companion object {
        const val IMDB_PREFIX = "tt"
        val CACHE_TTL = 6.hours
    }
}
