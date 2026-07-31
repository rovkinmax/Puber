package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Duration
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Posters
import com.kino.puber.data.api.models.Season
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoItemUIMapperTest {

    private val mapper = VideoItemUIMapper(FakeResourceProvider())

    // region isWatched mapping

    @Test
    fun mapShortItem_isWatched_true_whenMovieWatched() {
        val item = testItem(type = ItemType.MOVIE, watched = 1, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenMovieNotWatched() {
        val item = testItem(type = ItemType.MOVIE, watched = 0, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenWatchedNull() {
        val item = testItem(type = ItemType.MOVIE, watched = null, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_true_whenSeriesAllWatched() {
        // Series with watched > 0 and no new episodes
        val item = testItem(type = ItemType.SERIAL, watched = 10, new = 0)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenSeriesHasNewEpisodes() {
        val item = testItem(type = ItemType.SERIAL, watched = 8, new = 3)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_true_whenSeriesWatchedAndNewNull() {
        // new=null means API didn't report new episodes (all watched)
        val item = testItem(type = ItemType.SERIAL, watched = 10, new = null)
        val result = mapper.mapShortItem(item)
        assertEquals(true, result.isWatched)
    }

    @Test
    fun mapShortItem_isWatched_false_whenWatchedZeroEvenIfNewZero() {
        val item = testItem(type = ItemType.SERIAL, watched = 0, new = 0)
        val result = mapper.mapShortItem(item)
        assertEquals(false, result.isWatched)
    }

    // endregion

    // region basic mapping

    @Test
    fun mapShortItem_mapsTitle() {
        val item = testItem(title = "Breaking Bad")
        assertEquals("Breaking Bad", mapper.mapShortItem(item).title)
    }

    @Test
    fun mapShortItem_mapsUnwatchedCount() {
        val item = testItem(new = 5)
        assertEquals(5, mapper.mapShortItem(item).unwatchedCount)
    }

    @Test
    fun mapShortItem_mapsId() {
        val item = testItem(id = 42)
        assertEquals(42, mapper.mapShortItem(item).id)
    }

    @Test
    fun mapShortItem_marksMovieSaved_whenBookmarksExist() {
        val item = testItem(type = ItemType.MOVIE, bookmarks = listOf(Bookmark(id = 1, title = "Saved")))
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_marksSeriesSaved_whenInWatchlist() {
        val item = testItem(type = ItemType.SERIAL, inWatchlist = true)
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_marksSeriesSaved_whenSubscribed() {
        val item = testItem(type = ItemType.SERIAL, subscribed = true)
        assertEquals(true, mapper.mapShortItem(item).isSaved)
    }

    @Test
    fun mapShortItem_doesNotTreatSeriesBookmarkFoldersAsWatchlist() {
        val item = testItem(type = ItemType.SERIAL, bookmarks = listOf(Bookmark(id = 1, title = "Folder")))
        assertEquals(false, mapper.mapShortItem(item).isSaved)
    }

    // endregion

    // region hero mapping

    @Test
    fun mapHeroItems_prefersWidePosterAndFallsBackToBigPosters() {
        val item = testItem(
            posters = Posters(
                wide = "http://wide.example/hero.jpg",
                big = "http://big.example/hero.jpg",
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals("https://wide.example/hero.jpg", result.wideImageUrl)
        assertEquals("https://big.example/hero.jpg", result.fallbackImageUrl)
        assertEquals(emptyList<String>(), result.fallbackImageUrls)
    }

    @Test
    fun mapHeroItems_mapsMovieMetadata() {
        val item = testItem(
            id = 42,
            title = "Movie",
            year = 2024,
            genres = listOf(Genre(1, "Anime"), Genre(2, "Action")),
            countries = listOf(Country(1, "Japan")),
            duration = Duration(total = 5 * 60),
            kinopoiskRating = "8.1",
            imdbRating = "7.9",
            ratingPercentage = 86,
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(42, result.id)
        assertEquals("Movie", result.title)
        assertEquals("2024", result.year)
        assertEquals("Anime, Action", result.genres)
        assertEquals("Japan", result.country)
        assertEquals(mapper.buildDuration(item), result.duration)
        assertEquals(3, result.ratings.size)
    }

    @Test
    fun mapHeroItems_mapsSeriesSeasonCount() {
        val item = testItem(
            type = ItemType.SERIAL,
            seasons = listOf(
                Season(id = 1, number = 1),
                Season(id = 2, number = 2),
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(
            "string_${R.string.video_details_label_seasons}_2",
            result.duration,
        )
    }

    @Test
    fun mapHeroItems_leavesDurationEmptyForSeriesWithoutSeasons() {
        val item = testItem(
            type = ItemType.SERIAL,
            duration = Duration(total = 5 * 60),
            seasons = null,
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals("", result.duration)
    }

    @Test
    fun mapHeroItems_usesMovieDurationForNonSeriesWithSeasons() {
        val item = testItem(
            type = ItemType.MOVIE,
            duration = Duration(total = 5 * 60),
            seasons = listOf(
                Season(id = 1, number = 1),
                Season(id = 2, number = 2),
            ),
        )

        val result = mapper.mapHeroItems(listOf(item)).single()

        assertEquals(
            "string_${R.string.video_details_label_duration}_" +
                "string_${R.string.duration_minutes_only}_5",
            result.duration,
        )
    }

    // endregion

    private fun testItem(
        id: Int = 1,
        title: String = "Test",
        type: ItemType = ItemType.MOVIE,
        year: Int? = null,
        genres: List<Genre>? = null,
        countries: List<Country>? = null,
        duration: Duration? = null,
        posters: Posters? = null,
        seasons: List<Season>? = null,
        kinopoiskRating: String? = null,
        imdbRating: String? = null,
        ratingPercentage: Int? = null,
        watched: Int? = null,
        new: Int? = null,
        subscribed: Boolean? = null,
        inWatchlist: Boolean? = null,
        bookmarks: List<Bookmark>? = null,
    ) = Item(
        id = id,
        title = title,
        type = type,
        year = year,
        genres = genres,
        countries = countries,
        duration = duration,
        posters = posters,
        seasons = seasons,
        kinopoiskRating = kinopoiskRating,
        imdbRating = imdbRating,
        ratingPercentage = ratingPercentage,
        watched = watched,
        new = new,
        subscribed = subscribed,
        inWatchlist = inWatchlist,
        bookmarks = bookmarks,
    )
}
