package com.kino.puber.ui.feature.details.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.ScheduledEpisode
import com.kino.puber.domain.model.ScheduledSeason
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.util.FakeResourceProvider
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DetailsEpisodeScheduleMapperTest {

    private val mapper = DetailsScreenUIMapper(
        resources = FakeResourceProvider(),
        itemMapper = VideoItemUIMapper(FakeResourceProvider()),
        bookmarkPreferencesRepository = BookmarkPreferencesRepository(),
    )

    @Test
    fun map_mergesBySeasonAndEpisode_existingKinoPubEpisodeWins() {
        val item = Item(
            id = 42,
            title = "Series",
            type = ItemType.SERIAL,
            seasons = listOf(
                Season(
                    id = 1,
                    number = 1,
                    episodes = listOf(
                        com.kino.puber.data.api.models.Episode(
                            id = 700,
                            number = 1,
                            title = "Playable episode",
                        ),
                    ),
                ),
            ),
        )
        val schedule = schedule(
            ScheduledSeason(
                seasonNumber = 1,
                episodes = listOf(
                    ScheduledEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        title = "Provider duplicate",
                        airDate = LocalDate(2026, 8, 23),
                    ),
                    ScheduledEpisode(
                        seasonNumber = 1,
                        episodeNumber = 2,
                        title = "Future episode",
                        airDate = LocalDate(2026, 8, 24),
                    ),
                ),
            ),
        )

        val episodes = mapper.map(item, isInWatchlist = false, schedule = schedule)
            .episodes
            ?.list
            ?.filterIsInstance<VideoGridItemUIState.Items>()
            ?.single()
            ?.items
            .orEmpty()

        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        assertEquals(700, episodes.first().id)
        assertEquals("1. Playable episode", episodes.first().title)
        assertNotEquals(700, episodes.last().id)
    }

    @Test
    fun map_scheduledCards_areInformational_andUseStableDisjointIdentity() {
        val item = Item(id = 42, title = "Series", type = ItemType.SERIAL)
        val schedule = schedule(
            ScheduledSeason(
                seasonNumber = 2,
                announcementDate = LocalDate(2026, 10, 1),
                episodes = emptyList(),
            ),
        )

        val card = mapper.map(item, isInWatchlist = false, schedule = schedule)
            .episodes
            ?.list
            ?.filterIsInstance<VideoGridItemUIState.Items>()
            ?.single()
            ?.items
            ?.single()
            ?: error("expected scheduled announcement card")

        assertTrue(card.id < 0)
        assertEquals(VideoItemPresentation.Scheduled, card.presentation)
        assertEquals(null, card.isWatched)
        assertEquals(null, card.progressPercent)
    }

    @Test
    fun map_scheduledEpisode_resolvesRelativeTmdbStillPath() {
        val card = scheduledEpisodeCard(stillPath = "/episode-still.jpg")

        assertEquals("https://image.tmdb.org/t/p/w500/episode-still.jpg", card.imageUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/episode-still.jpg", card.bigImageUrl)
        assertNotEquals("/episode-still.jpg", card.imageUrl)
        assertNotEquals("/episode-still.jpg", card.bigImageUrl)
    }

    @Test
    fun map_scheduledEpisode_preservesAbsoluteStillUrl() {
        val absoluteUrl = "https://cdn.example.com/episode-still.jpg"

        val card = scheduledEpisodeCard(stillPath = absoluteUrl)

        assertEquals(absoluteUrl, card.imageUrl)
        assertEquals(absoluteUrl, card.bigImageUrl)
    }

    @Test
    fun map_scheduledEpisode_usesEmptyImageFallbackForBlankStillPath() {
        val card = scheduledEpisodeCard(stillPath = "   ")

        assertEquals("", card.imageUrl)
        assertEquals("", card.bigImageUrl)
    }

    private fun scheduledEpisodeCard(stillPath: String?): VideoItemUIState {
        val item = Item(id = 42, title = "Series", type = ItemType.SERIAL)
        val schedule = schedule(
            ScheduledSeason(
                seasonNumber = 1,
                episodes = listOf(
                    ScheduledEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        title = "Future episode",
                        airDate = LocalDate(2026, 8, 23),
                        stillPath = stillPath,
                    ),
                ),
            ),
        )

        return mapper.map(item, isInWatchlist = false, schedule = schedule)
            .episodes
            ?.list
            ?.filterIsInstance<VideoGridItemUIState.Items>()
            ?.single()
            ?.items
            ?.single()
            ?: error("expected scheduled episode card")
    }

    private fun schedule(vararg seasons: ScheduledSeason): EpisodeSchedule {
        return EpisodeSchedule(
            provider = ScheduleProvider.TMDB,
            seasons = seasons.toList(),
        )
    }
}
