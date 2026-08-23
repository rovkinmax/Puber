package com.kino.puber.ui.feature.episodeschedule.model

import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.ScheduledEpisode
import com.kino.puber.domain.model.ScheduledSeason
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.util.FakeResourceProvider
import java.util.Locale
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EpisodeScheduleUIMapperTest {

    private val mapper = EpisodeScheduleUIMapper(FakeResourceProvider())
    private val originalLocale = Locale.getDefault()

    @BeforeEach
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun mapToContent_sortsSeasonsAndEpisodes_andKeepsTypedDates() {
        val content = mapper.mapToContent(
            title = "Series",
            schedule = EpisodeSchedule(
                provider = ScheduleProvider.TMDB,
                seasons = listOf(
                    ScheduledSeason(
                        seasonNumber = 2,
                        episodes = listOf(
                            ScheduledEpisode(
                                seasonNumber = 2,
                                episodeNumber = 1,
                                title = "Second season",
                                airDate = LocalDate(2026, 9, 1),
                            ),
                        ),
                    ),
                    ScheduledSeason(
                        seasonNumber = 1,
                        episodes = listOf(
                            ScheduledEpisode(
                                seasonNumber = 1,
                                episodeNumber = 2,
                                title = "Later",
                                airDate = LocalDate(2026, 8, 22),
                            ),
                            ScheduledEpisode(
                                seasonNumber = 1,
                                episodeNumber = 1,
                                title = "Earlier",
                                airDate = LocalDate(2026, 8, 23),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Series", content.title)
        assertEquals(ScheduleProvider.TMDB, content.provider)
        assertEquals(listOf(1, 2), content.seasons.map { it.seasonNumber })
        assertEquals(listOf(1, 2), content.seasons.first().episodes.map { it.episodeNumber })
        assertEquals(
            listOf(LocalDate(2026, 8, 23), LocalDate(2026, 8, 22)),
            content.seasons.first().episodes.map { it.airDate },
        )
        assertEquals(
            content.seasons.first().episodes.first().airDate,
            content.seasons.first().episodes.first().releaseDate,
        )
        assertNotNull(content.seasons.first().episodes.first().airDateLabel)
        assertTrue(content.seasons.first().episodes.first().airDateLabel.isNotBlank())

        val rows = content.grid.list.filterIsInstance<VideoGridItemUIState.Items>()
        assertEquals(listOf("season_1", "season_2"), rows.map { it.rowKey })
        assertEquals(listOf(1, 2), rows.first().items.mapNotNull { it.episodeNumber })
        assertTrue(rows.flatMap { it.items }.all { it.presentation == VideoItemPresentation.Scheduled })
        assertEquals(rows.first().items.first().id, content.initialFocusedItemId)
    }

    @Test
    fun mapToContent_mapsSeasonAnnouncement_withoutInventingEpisodeRows() {
        val content = mapper.mapToContent(
            title = "Series",
            schedule = EpisodeSchedule(
                provider = ScheduleProvider.TMDB,
                seasons = listOf(
                    ScheduledSeason(
                        seasonNumber = 3,
                        announcementDate = LocalDate(2026, 10, 1),
                    ),
                ),
            ),
        )

        val season = content.seasons.single()
        assertEquals(LocalDate(2026, 10, 1), season.announcementDate)
        assertFalse(season.announcementDateLabel.isNullOrBlank())
        assertTrue(season.episodes.isEmpty())

        val card = content.grid.list
            .filterIsInstance<VideoGridItemUIState.Items>()
            .single()
            .items
            .single()
        assertEquals(VideoItemPresentation.Scheduled, card.presentation)
        assertEquals(3, card.seasonNumber)
        assertEquals(null, card.episodeNumber)
        assertTrue(card.scheduledReleaseDate?.isNotBlank() == true)
    }

    @Test
    fun mapToContent_omitsSeasonAnnouncement_whenDatedEpisodesExist() {
        val content = mapper.mapToContent(
            title = "Series",
            schedule = EpisodeSchedule(
                provider = ScheduleProvider.TMDB,
                seasons = listOf(
                    ScheduledSeason(
                        seasonNumber = 4,
                        announcementDate = LocalDate(2026, 10, 1),
                        episodes = listOf(
                            ScheduledEpisode(
                                seasonNumber = 4,
                                episodeNumber = 1,
                                title = "Premiere",
                                airDate = LocalDate(2026, 10, 1),
                            ),
                            ScheduledEpisode(
                                seasonNumber = 4,
                                episodeNumber = 2,
                                title = "Follow-up",
                                airDate = LocalDate(2026, 10, 8),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val title = content.grid.list
            .filterIsInstance<VideoGridItemUIState.Title>()
            .single()
        val row = content.grid.list
            .filterIsInstance<VideoGridItemUIState.Items>()
            .single()

        assertEquals(
            "string_${com.kino.puber.R.string.player_season_episodes_count}_4_2",
            title.title,
        )
        assertEquals("season_4", row.rowKey)
        assertEquals(listOf(1, 2), row.items.map { it.episodeNumber })
        assertEquals(
            listOf("1. Premiere", "2. Follow-up"),
            row.items.map { it.title },
        )
    }

    @Test
    fun mapError_usesGenericLocalizedMessage_withoutProviderDetails() {
        val error = mapper.mapError(IllegalStateException("token=secret"))

        assertEquals("string_${com.kino.puber.R.string.error_generic}", error.message)
        assertFalse(error.message.contains("secret"))
    }
}
