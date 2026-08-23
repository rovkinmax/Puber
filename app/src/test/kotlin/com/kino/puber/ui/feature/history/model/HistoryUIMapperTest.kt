package com.kino.puber.ui.feature.history.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk

class HistoryUIMapperTest {

    private val mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider()))

    @Test
    fun mapMovie_keepsItemPlaybackAndDeletionIdentityWithoutInventingRecordIdentity() {
        val state = requireNotNull(
            mapper.map(
                movie(
                    recordId = null,
                    itemId = 72001,
                    videoId = 73001,
                    videoNumber = 2,
                    watching = WatchingInfo(time = 1_200, duration = 4_800, status = 0),
                    watched = 0,
                    updated = "2100-01-01T00:00:00Z",
                ),
            ),
        )

        assertEquals(72001, state.itemId)
        assertEquals(73001, state.deletionMediaId)
        assertEquals(
            HistoryRowKey.Media(
                HistorySemanticKey.Movie(itemId = 72001, videoNumber = 2),
            ),
            state.rowKey,
        )
        assertEquals(HistorySemanticKey.Movie(itemId = 72001, videoNumber = 2), state.semanticKey)
        assertEquals(2, state.videoNumber)
        assertNull(state.seasonNumber)
        assertNull(state.episodeNumber)
        assertEquals(HistoryPlaybackTarget.Movie(videoNumber = 2), state.playbackTarget)
        assertEquals(0.25F, state.progressPercent)
        assertFalse(state.isWatched)
        assertEquals("2100-01-01T00:00:00Z", state.lastViewedAt)
        assertEquals(72001, state.card.id)
        assertEquals("https://assets.example.test/wide.jpg", state.card.wideImageUrl)
        assertEquals(state.progressPercent, state.card.progressPercent)
        assertEquals(state.isWatched, state.card.isWatched)
        assertTrue(state.card.ratings.isEmpty())
        assertNull(state.card.unwatchedCount)
    }

    @Test
    fun mapSeries_preservesExactSeasonAndEpisodeIdentity() {
        val state = requireNotNull(
            mapper.map(
                episode(
                    recordId = null,
                    itemId = 82001,
                    videoId = 83001,
                    season = 4,
                    episode = 9,
                ),
            ),
        )

        assertEquals(
            HistorySemanticKey.Episode(itemId = 82001, seasonNumber = 4, episodeNumber = 9),
            state.semanticKey,
        )
        assertNull(state.videoNumber)
        assertEquals(4, state.seasonNumber)
        assertEquals(9, state.episodeNumber)
        assertEquals(
            HistoryPlaybackTarget.Episode(seasonNumber = 4, episodeNumber = 9),
            state.playbackTarget,
        )
        assertEquals(4, state.card.seasonNumber)
        assertEquals(9, state.card.episodeNumber)
    }

    @Test
    fun mapSeries_routesIncompleteCoordinatesToDetails() {
        val history = episode(
            recordId = null,
            itemId = 82001,
            videoId = 83001,
            season = null,
            episode = 9,
        )

        val state = requireNotNull(mapper.map(history))

        assertEquals(HistoryRowKey.DeletionMedia(mediaId = 83001), state.rowKey)
        assertNull(state.semanticKey)
        assertEquals(HistoryPlaybackTarget.Details, state.playbackTarget)
        assertNull(state.videoNumber)
        assertNull(state.seasonNumber)
        assertNull(state.episodeNumber)
    }

    @Test
    fun map_usesNestedWatchingDurationAndHidesProgressWhenItIsMissing() {
        val state = requireNotNull(
            mapper.map(
                movie(
                    recordId = null,
                    itemId = 2,
                    videoId = 3,
                    videoNumber = 1,
                    watching = WatchingInfo(time = 600, duration = 0, status = 0),
                    videoDuration = 2_400,
                ),
            ),
        )

        assertNull(state.progressPercent)
        assertNull(state.card.progressPercent)
    }

    @Test
    fun map_prefersMediaWatchedFlagAndFallsBackToWatchingStatusOnlyWhenAbsent() {
        val explicitNotWatched = requireNotNull(
            mapper.map(
                movie(
                    recordId = null,
                    itemId = 2,
                    videoId = 3,
                    videoNumber = 1,
                    watching = WatchingInfo(status = 1),
                    watched = 0,
                ),
            ),
        )
        val fallbackWatched = requireNotNull(
            mapper.map(
                movie(
                    recordId = null,
                    itemId = 2,
                    videoId = 4,
                    videoNumber = 2,
                    watching = WatchingInfo(status = 1),
                    watched = null,
                ),
            ),
        )

        assertFalse(explicitNotWatched.isWatched)
        assertTrue(fallbackWatched.isWatched)
    }

    @Test
    fun map_retainsCompletedEntries() {
        val completed = movie(
            recordId = null,
            itemId = 2,
            videoId = 3,
            videoNumber = 1,
            watching = WatchingInfo(time = 2_400, duration = 2_400, status = 1),
            watched = 1,
        )

        val states = mapper.map(listOf(completed))

        assertEquals(1, states.size)
        assertTrue(states.single().isWatched)
    }

    @Test
    fun map_completedEntryForcesWatchedIndicatorWhenSharedPreferenceIsOff() {
        val preferences = mockk<PlayerPreferencesRepository>()
        every { preferences.watchedIndicatorsEnabled } returns false
        val historyMapper = HistoryUIMapper(
            VideoItemUIMapper(
                resources = FakeResourceProvider(),
                playerPreferencesRepository = preferences,
            ),
        )
        val completed = movie(
            recordId = null,
            itemId = 2,
            videoId = 3,
            videoNumber = 1,
            watching = WatchingInfo(time = 2_400, duration = 2_400, status = 1),
            watched = 1,
        )

        val state = requireNotNull(historyMapper.map(completed))

        assertTrue(state.isWatched)
        assertTrue(state.card.isWatched == true)
        assertTrue(state.card.showWatchedIndicator)
    }

    @Test
    fun map_keepsDifferentEpisodesOfOneSeriesDistinct() {
        val states = mapper.map(
            listOf(
                episode(recordId = null, itemId = 2, videoId = 3, season = 1, episode = 4),
                episode(recordId = null, itemId = 2, videoId = 4, season = 1, episode = 5),
            ),
        )

        assertEquals(
            listOf(
                HistorySemanticKey.Episode(itemId = 2, seasonNumber = 1, episodeNumber = 4),
                HistorySemanticKey.Episode(itemId = 2, seasonNumber = 1, episodeNumber = 5),
            ),
            states.map(HistoryItemUIState::semanticKey),
        )
    }

    @Test
    fun map_filtersUnsupportedOrUnusableRows() {
        val unsupported = movie(
            recordId = null,
            itemId = 2,
            videoId = 3,
            videoNumber = 1,
            type = ItemType.UNKNOWN_VALUE,
        )
        val missingMovieNumber = movie(
            recordId = null,
            itemId = 2,
            videoId = 4,
            videoNumber = null,
        )
        val missingMedia = History(
            recordId = 3,
            item = item(id = 2, type = ItemType.MOVIE),
            video = null,
        )

        assertEquals(
            emptyList<HistoryItemUIState>(),
            mapper.map(listOf(unsupported, missingMovieNumber, missingMedia)),
        )
    }

    private fun movie(
        recordId: Int?,
        itemId: Int,
        videoId: Int,
        videoNumber: Int?,
        watching: WatchingInfo? = null,
        watched: Int? = null,
        updated: String? = null,
        videoDuration: Int? = null,
        type: ItemType = ItemType.MOVIE,
    ): History {
        return History(
            recordId = recordId,
            item = item(id = itemId, type = type),
            video = Video(
                id = videoId,
                number = videoNumber,
                duration = videoDuration,
                watched = watched,
                watching = watching,
            ),
            updated = updated,
        )
    }

    private fun episode(
        recordId: Int?,
        itemId: Int,
        videoId: Int,
        season: Int?,
        episode: Int?,
    ): History {
        return History(
            recordId = recordId,
            item = item(id = itemId, type = ItemType.SERIAL),
            video = Video(id = videoId, number = episode),
            season = season,
        )
    }

    private fun item(id: Int, type: ItemType): Item {
        return Item(
            id = id,
            title = "Synthetic title",
            type = type,
            posters = Posters(
                medium = "https://assets.example.test/medium.jpg",
                big = "https://assets.example.test/big.jpg",
                wide = "https://assets.example.test/wide.jpg",
            ),
            new = 3,
            kinopoiskRating = "7.2",
        )
    }
}
