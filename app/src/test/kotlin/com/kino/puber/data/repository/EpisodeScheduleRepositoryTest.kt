package com.kino.puber.data.repository

import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.TmdbEpisodeResponse
import com.kino.puber.data.api.models.TmdbSeasonDetailsResponse
import com.kino.puber.data.api.models.TmdbSeasonSummaryResponse
import com.kino.puber.data.api.models.TmdbTvDetailsResponse
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.domain.model.ScheduledEpisode
import com.kino.puber.domain.model.ScheduledSeason
import com.kino.puber.domain.model.ScheduleProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class EpisodeScheduleRepositoryTest {

    private val api = mockk<TmdbApiClient>()
    private val today = LocalDate(2026, 8, 22)

    @Test
    fun getSchedule_keepsTodayAndFutureEpisodes_skipsPastMalformedAndSpecials() = runTest {
        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { api.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(
                id = 101,
                nextEpisodeToAir = null,
                seasons = listOf(
                    TmdbSeasonSummaryResponse(
                        id = 1,
                        seasonNumber = 0,
                        airDate = "2026-08-23",
                    ),
                    TmdbSeasonSummaryResponse(
                        id = 2,
                        seasonNumber = 2,
                        airDate = "2026-08-22",
                    ),
                ),
            ),
        )
        coEvery { api.getTvSeasonDetails(101, 2) } returns Result.success(
            TmdbSeasonDetailsResponse(
                id = 2,
                seasonNumber = 2,
                airDate = "2026-08-22",
                episodes = listOf(
                    TmdbEpisodeResponse(episodeNumber = 1, name = "Past", airDate = "2026-08-21"),
                    TmdbEpisodeResponse(episodeNumber = 2, name = "Today", airDate = "2026-08-22"),
                    TmdbEpisodeResponse(episodeNumber = 3, name = "Future", airDate = "2026-08-23"),
                    TmdbEpisodeResponse(episodeNumber = 4, name = "Malformed", airDate = "not-a-date"),
                ),
            ),
        )

        val result = repository().getSchedule("tt123")

        val schedule = assertInstanceOf(EpisodeScheduleResult.Available::class.java, result).schedule
        assertEquals(ScheduleProvider.TMDB, schedule.provider)
        assertEquals(
            listOf(
                ScheduledEpisode(seasonNumber = 2, episodeNumber = 2, title = "Today", airDate = today),
                ScheduledEpisode(
                    seasonNumber = 2,
                    episodeNumber = 3,
                    title = "Future",
                    airDate = LocalDate(2026, 8, 23),
                ),
            ),
            schedule.seasons.single().episodes,
        )
    }

    @Test
    fun getSchedule_retainsSeasonAnnouncement_whenNoEpisodeHasDate() = runTest {
        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { api.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(
                id = 101,
                nextEpisodeToAir = null,
                seasons = listOf(
                    TmdbSeasonSummaryResponse(
                        id = 3,
                        seasonNumber = 3,
                        airDate = "2026-10-01",
                    ),
                ),
            ),
        )
        coEvery { api.getTvSeasonDetails(101, 3) } returns Result.success(
            TmdbSeasonDetailsResponse(
                id = 3,
                seasonNumber = 3,
                airDate = "2026-10-01",
                episodes = emptyList(),
            ),
        )

        val schedule = assertInstanceOf(
            EpisodeScheduleResult.Available::class.java,
            repository().getSchedule("123"),
        ).schedule

        assertEquals(
            listOf(
                ScheduledSeason(
                    seasonNumber = 3,
                    announcementDate = LocalDate(2026, 10, 1),
                    episodes = emptyList(),
                ),
            ),
            schedule.seasons,
        )
    }

    @Test
    fun getSchedule_returnsTypedUnavailableOutcomes() = runTest {
        every { api.isConfigured } returns false
        assertEquals(
            EpisodeScheduleResult.MissingCredentials,
            repository().getSchedule("tt123"),
        )

        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(null)
        assertEquals(
            EpisodeScheduleResult.NoMatch,
            repository().getSchedule("tt123"),
        )
    }

    @Test
    fun getSchedule_returnsNoUpcomingReleases_whenAllCandidateRowsArePast() = runTest {
        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { api.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(
                id = 101,
                nextEpisodeToAir = null,
                seasons = listOf(
                    TmdbSeasonSummaryResponse(id = 2, seasonNumber = 2, airDate = "2026-08-01"),
                ),
            ),
        )

        assertEquals(
            EpisodeScheduleResult.NoUpcomingReleases,
            repository().getSchedule("tt123"),
        )
    }

    @Test
    fun getSchedule_cachesSuccessfulTypedOutcome_butDoesNotCacheFailures() = runTest {
        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { api.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(id = 101, nextEpisodeToAir = null, seasons = emptyList()),
        )

        val repository = repository()
        assertEquals(EpisodeScheduleResult.NoUpcomingReleases, repository.getSchedule("tt123"))
        assertEquals(EpisodeScheduleResult.NoUpcomingReleases, repository.getSchedule("123"))
        coVerify(exactly = 1) { api.findTvByImdbId("tt123") }

        val failingApi = mockk<TmdbApiClient>()
        every { failingApi.isConfigured } returns true
        coEvery { failingApi.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { failingApi.getTvDetails(101) } throws IllegalStateException("transport")
        val failingRepository = EpisodeScheduleRepository(
            tmdbApiClient = failingApi,
            today = { today },
        )

        val failure = try {
            failingRepository.getSchedule("tt123")
            null
        } catch (error: Throwable) {
            error
        }
        assertInstanceOf(IllegalStateException::class.java, failure)

        coEvery { failingApi.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(id = 101, nextEpisodeToAir = null, seasons = emptyList()),
        )
        assertEquals(
            EpisodeScheduleResult.NoUpcomingReleases,
            failingRepository.getSchedule("tt123"),
        )
        coVerify(exactly = 2) { failingApi.getTvDetails(101) }
    }

    @Test
    fun getSchedule_refreshesDateFilteredCache_afterLocalDateRollover() = runTest {
        var currentDate = LocalDate(2026, 8, 22)
        every { api.isConfigured } returns true
        coEvery { api.findTvByImdbId("tt123") } returns Result.success(101)
        coEvery { api.getTvDetails(101) } returns Result.success(
            TmdbTvDetailsResponse(
                id = 101,
                nextEpisodeToAir = TmdbEpisodeResponse(
                    seasonNumber = 2,
                    episodeNumber = 1,
                    airDate = "2026-08-22",
                ),
                seasons = emptyList(),
            ),
        )
        coEvery { api.getTvSeasonDetails(101, 2) } returns Result.success(
            TmdbSeasonDetailsResponse(
                id = 2,
                seasonNumber = 2,
                airDate = null,
                episodes = listOf(
                    TmdbEpisodeResponse(episodeNumber = 1, name = "D1", airDate = "2026-08-22"),
                    TmdbEpisodeResponse(episodeNumber = 2, name = "D2", airDate = "2026-08-23"),
                    TmdbEpisodeResponse(episodeNumber = 3, name = "Future", airDate = "2026-08-24"),
                ),
            ),
        )
        val repository = EpisodeScheduleRepository(
            tmdbApiClient = api,
            today = { currentDate },
        )

        assertEquals(
            listOf(1, 2, 3),
            repository.getSchedule("tt123").episodeNumbers(),
        )

        currentDate = LocalDate(2026, 8, 23)
        assertEquals(
            listOf(2, 3),
            repository.getSchedule("123").episodeNumbers(),
        )
        assertEquals(
            listOf(2, 3),
            repository.getSchedule("tt123").episodeNumbers(),
        )

        coVerify(exactly = 2) { api.findTvByImdbId("tt123") }
        coVerify(exactly = 2) { api.getTvDetails(101) }
        coVerify(exactly = 2) { api.getTvSeasonDetails(101, 2) }
    }

    private fun repository(): EpisodeScheduleRepository {
        return EpisodeScheduleRepository(
            tmdbApiClient = api,
            today = { today },
        )
    }

    private fun EpisodeScheduleResult.episodeNumbers(): List<Int> {
        return assertInstanceOf(EpisodeScheduleResult.Available::class.java, this)
            .schedule
            .seasons
            .single()
            .episodes
            .map(ScheduledEpisode::episodeNumber)
    }
}
