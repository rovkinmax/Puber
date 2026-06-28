package com.kino.puber.ui.feature.details.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.R
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.Video
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailsScreenUIMapperTest {

    private val mapper = DetailsScreenUIMapper(
        resources = FakeResourceProvider(),
        itemMapper = VideoItemUIMapper(FakeResourceProvider()),
    )

    @Test
    fun map_movieButtons_includeTrailerWatchlistAndWatchedActions() {
        val state = mapper.map(movie(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.TrailerClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun map_seriesButtons_doNotIncludeWatchedActionOrDuplicateTrailerAction() {
        val state = mapper.map(series(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.SelectSeasonClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.IconOnly>(DetailsAction.TrailerClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun mapSimilarItems_enablesTitlesForRelatedCards() {
        val items = mapper.mapSimilarItems(listOf(movie(trailer = null)))

        assertEquals(true, items.single().showTitle)
    }

    @Test
    fun map_movieInfo_usesPlayableVideoAudioCount() {
        val state = mapper.map(
            movie(
                trailer = null,
                videos = listOf(Video(id = 1, audios = listOf(audio("rus"), audio("eng")))),
            )
        )

        assertEquals("2", state.audioTracksRowValue())
    }

    @Test
    fun map_seriesInfo_usesFirstUnwatchedEpisodeAudioCount() {
        val state = mapper.map(
            series(
                trailer = null,
                seasons = listOf(
                    Season(
                        id = 1,
                        number = 1,
                        episodes = listOf(
                            Episode(id = 1, number = 1, watched = 1, audios = listOf(audio("rus"))),
                            Episode(id = 2, number = 2, watched = 0, audios = listOf(audio("rus"), audio("eng"))),
                        ),
                    )
                ),
            )
        )

        assertEquals("2", state.audioTracksRowValue())
    }

    private inline fun <reified T : DetailsButtonUIState> List<DetailsButtonUIState>.count(
        action: DetailsAction,
    ): Int {
        return filterIsInstance<T>().count { button ->
            when (button) {
                is DetailsButtonUIState.TextButton -> button.action == action
                is DetailsButtonUIState.IconOnly -> button.action == action
                is DetailsButtonUIState.WatchlistToggle -> button.action == action
                is DetailsButtonUIState.WatchedToggle -> button.action == action
            }
        }
    }

    private fun DetailsScreenState.Content.audioTracksRowValue(): String? {
        return info.secondaryRows.firstOrNull { row ->
            row.label == "string_${R.string.video_details_info_audio_tracks}"
        }?.value
    }

    private fun audio(lang: String): Audio {
        return Audio(id = lang.hashCode(), lang = lang)
    }

    private fun movie(
        trailer: Trailer?,
        videos: List<Video>? = null,
    ): Item {
        return Item(id = 1, title = "Movie", type = ItemType.MOVIE, trailer = trailer, videos = videos)
    }

    private fun series(
        trailer: Trailer?,
        seasons: List<Season>? = null,
    ): Item {
        return Item(id = 2, title = "Series", type = ItemType.SERIAL, trailer = trailer, seasons = seasons)
    }
}
