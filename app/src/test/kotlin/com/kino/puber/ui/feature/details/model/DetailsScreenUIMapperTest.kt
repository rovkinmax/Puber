package com.kino.puber.ui.feature.details.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.R
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailsScreenUIMapperTest {

    private val mapper = DetailsScreenUIMapper(
        resources = FakeResourceProvider(),
        itemMapper = VideoItemUIMapper(FakeResourceProvider()),
        bookmarkPreferencesRepository = BookmarkPreferencesRepository(),
    )

    @Test
    fun map_movieButtons_includeTrailerBookmarkAndWatchedActions() {
        val state = mapper.map(movie(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.TrailerClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.BookmarkToggle>(DetailsAction.BookmarkToggleClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun map_seriesButtons_doNotIncludeWatchedActionOrDuplicateTrailerAction() {
        val state = mapper.map(series(trailer = Trailer(url = "https://trailer")))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.PlayClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.SelectSeasonClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.ScheduleClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.IconOnly>(DetailsAction.TrailerClicked))
        assertEquals(1, state.buttons.count<DetailsButtonUIState.WatchlistToggle>(DetailsAction.WatchlistToggleClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.BookmarkToggle>(DetailsAction.BookmarkToggleClicked))
        assertEquals(0, state.buttons.count<DetailsButtonUIState.WatchedToggle>(DetailsAction.WatchedToggleClicked))
    }

    @Test
    fun map_extendedSeriesIncludesIndependentWatchlistAndBookmarkActions() {
        val extendedMapper = DetailsScreenUIMapper(
            resources = FakeResourceProvider(),
            itemMapper = VideoItemUIMapper(FakeResourceProvider()),
            bookmarkPreferencesRepository = BookmarkPreferencesRepository(BookmarkMode.Extended),
        )

        val state = extendedMapper.map(series(trailer = null))

        assertEquals(
            1,
            state.buttons.count<DetailsButtonUIState.WatchlistToggle>(
                DetailsAction.WatchlistToggleClicked
            ),
        )
        assertEquals(
            1,
            state.buttons.count<DetailsButtonUIState.BookmarkToggle>(
                DetailsAction.BookmarkToggleClicked
            ),
        )
        assertEquals(BookmarkMode.Extended, state.bookmarkMode)
    }

    @Test
    fun map_seriesWithImdb_addsScheduleButton() {
        val state = mapper.map(series(trailer = null, imdb = " tt123 "))

        assertEquals(1, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.ScheduleClicked))
        val scheduleButton = state.buttons
            .filterIsInstance<DetailsButtonUIState.TextButton>()
            .single { it.action == DetailsAction.ScheduleClicked }
        assertEquals(R.string.video_details_button_schedule, scheduleButton.textRes)
    }

    @Test
    fun map_movieWithImdb_doesNotAddScheduleButton() {
        val state = mapper.map(movie(trailer = null, imdb = "tt123"))

        assertEquals(0, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.ScheduleClicked))
    }

    @Test
    fun map_seriesWithBlankImdb_doesNotAddScheduleButton() {
        val state = mapper.map(series(trailer = null, imdb = "   "))

        assertEquals(0, state.buttons.count<DetailsButtonUIState.TextButton>(DetailsAction.ScheduleClicked))
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

    @Test
    fun map_initialEpisodeSelectsExactEpisodeForPanelFocus() {
        val state = mapper.map(
            item = series(
                trailer = null,
                seasons = listOf(
                    Season(
                        id = 1,
                        number = 1,
                        episodes = listOf(Episode(id = 101, number = 1)),
                    ),
                    Season(
                        id = 2,
                        number = 2,
                        episodes = listOf(
                            Episode(id = 201, number = 1),
                            Episode(id = 204, number = 4),
                        ),
                    ),
                ),
            ),
            isInWatchlist = false,
            initialEpisode = DetailsEpisodeTarget(
                seasonNumber = 2,
                episodeNumber = 4,
            ),
        )

        assertEquals(204, state.currentEpisode?.id)
        assertEquals(204, state.initialEpisodeFocusId)
    }

    @Test
    fun map_seriesStatus_mapsFinishedOngoingAndUnknownOnlyForSeries() {
        assertEquals(
            "string_${R.string.video_details_series_status_finished}",
            mapper.map(series(trailer = null, finished = true)).seriesStatus,
        )
        assertEquals(
            "string_${R.string.video_details_series_status_ongoing}",
            mapper.map(series(trailer = null, finished = false)).seriesStatus,
        )
        assertEquals(null, mapper.map(series(trailer = null, finished = null)).seriesStatus)
        assertEquals(null, mapper.map(movie(trailer = null, finished = true)).seriesStatus)
    }

    @Test
    fun map_seriesStatus_addsStatusRowOnlyWhenKnown() {
        val state = mapper.map(series(trailer = null, finished = false))

        assertEquals(
            "string_${R.string.video_details_info_status}" to
                "string_${R.string.video_details_series_status_ongoing}",
            state.info.secondaryRows.last().let { it.label to it.value },
        )
        assertEquals(
            false,
            mapper.map(series(trailer = null, finished = null)).info.secondaryRows.any {
                it.label == "string_${R.string.video_details_info_status}"
            },
        )
    }

    @Test
    fun map_castCards_preservesOrderAndOriginalActorQueries() {
        val state = mapper.map(
            movie(
                trailer = null,
                cast = " Actor One, Actor Two, Actor One ,, ",
            ),
        )

        assertEquals(
            listOf(
                DetailsCastMemberUIState("Actor One", "Actor One"),
                DetailsCastMemberUIState("Actor Two", "Actor Two"),
                DetailsCastMemberUIState("Actor One", "Actor One"),
            ),
            state.info.castCards,
        )
    }

    @Test
    fun enrichCastCards_attachesOnlyUniqueNormalizedExactMatches() {
        val cards = listOf(
            DetailsCastMemberUIState("Anne-Marie O'Neil", "Anne-Marie O'Neil"),
            DetailsCastMemberUIState("Unknown Actor", "Unknown Actor"),
            DetailsCastMemberUIState("Duplicate", "Duplicate"),
            DetailsCastMemberUIState("No Photo", "No Photo"),
        )

        val enriched = mapper.enrichCastCards(
            castCards = cards,
            tmdbCast = listOf(
                TmdbCastMember(" Anne Marie O Neil ", "https://image/one"),
                TmdbCastMember("Duplicate", "https://image/a"),
                TmdbCastMember("duplicate", "https://image/b"),
                TmdbCastMember("No Photo", null),
                TmdbCastMember("Unmatched", "https://image/unmatched"),
            ),
        )

        assertEquals(
            listOf("https://image/one", null, null, null),
            enriched.map { it.photoUrl },
        )
        assertEquals(cards.map { it.actorQuery }, enriched.map { it.actorQuery })
        assertEquals(cards.map { it.displayName }, enriched.map { it.displayName })
    }

    @Test
    fun enrichCastCards_matchesLocalizedReorderedNamesAndKeepsUnsupportedFallback() {
        val cards = listOf(
            DetailsCastMemberUIState("Сираиси Харука", "Сираиси Харука"),
            DetailsCastMemberUIState("Тамура Муцуми", "Тамура Муцуми"),
            DetailsCastMemberUIState("Накамура Юити", "Накамура Юити"),
            DetailsCastMemberUIState("Айдзава Сая", "Айдзава Сая"),
            DetailsCastMemberUIState("Юки Аой", "Юки Аой"),
            DetailsCastMemberUIState("Неизвестный Актёр", "Неизвестный Актёр"),
        )

        val enriched = mapper.enrichCastCards(
            castCards = cards,
            tmdbCast = listOf(
                TmdbCastMember("Haruka Shiraishi", "https://image/shiraishi"),
                TmdbCastMember("Mutsumi Tamura", "https://image/tamura"),
                TmdbCastMember("Yuichi Nakamura", "https://image/nakamura"),
                TmdbCastMember("Saya Aizawa", "https://image/aizawa"),
                TmdbCastMember("Aoi Yuki", "https://image/yuki"),
            ),
        )

        assertEquals(
            listOf(
                "https://image/shiraishi",
                "https://image/tamura",
                "https://image/nakamura",
                "https://image/aizawa",
                "https://image/yuki",
                null,
            ),
            enriched.map { it.photoUrl },
        )
        assertEquals(cards.map { it.actorQuery }, enriched.map { it.actorQuery })
        assertEquals(cards.map { it.displayName }, enriched.map { it.displayName })
    }

    @Test
    fun map_seriesWithEmptySeasonsAndUnavailableSchedule_preservesEmptyEpisodeGrid() {
        val state = mapper.map(
            item = series(
                trailer = null,
                seasons = emptyList(),
            ),
            isInWatchlist = false,
            schedule = null,
        )

        assertEquals(emptyList<Any>(), state.episodes?.list)
    }

    private inline fun <reified T : DetailsButtonUIState> List<DetailsButtonUIState>.count(
        action: DetailsAction,
    ): Int {
        return filterIsInstance<T>().count { button ->
            when (button) {
                is DetailsButtonUIState.TextButton -> button.action == action
                is DetailsButtonUIState.IconOnly -> button.action == action
                is DetailsButtonUIState.WatchlistToggle -> button.action == action
                is DetailsButtonUIState.BookmarkToggle -> button.action == action
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
        cast: String? = null,
        finished: Boolean? = null,
        imdb: String? = null,
    ): Item {
        return Item(
            id = 1,
            title = "Movie",
            type = ItemType.MOVIE,
            trailer = trailer,
            videos = videos,
            cast = cast,
            finished = finished,
            imdb = imdb,
        )
    }

    private fun series(
        trailer: Trailer?,
        seasons: List<Season>? = null,
        finished: Boolean? = null,
        imdb: String? = null,
    ): Item {
        return Item(
            id = 2,
            title = "Series",
            type = ItemType.SERIAL,
            trailer = trailer,
            seasons = seasons,
            finished = finished,
            imdb = imdb,
        )
    }
}
