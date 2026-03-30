package com.kino.puber.core.ui.model

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
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

    // endregion

    private fun testItem(
        id: Int = 1,
        title: String = "Test",
        type: ItemType = ItemType.MOVIE,
        watched: Int? = null,
        new: Int? = null,
    ) = Item(
        id = id,
        title = title,
        type = type,
        watched = watched,
        new = new,
    )
}
