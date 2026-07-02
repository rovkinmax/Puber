package com.kino.puber.ui.feature.home.model

import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.util.FakeResourceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class HomeUIMapperTest {

    private val resources = FakeResourceProvider()
    private val mapper = HomeUIMapper(
        videoItemMapper = VideoItemUIMapper(resources),
        resources = resources,
    )

    @Test
    fun mapItemSection_marksContinueWatchingItemsAsSaved() {
        val item = testItem(id = 42, type = ItemType.SERIAL)

        val section = mapper.mapItemSection(listOf(item), HomeSectionType.ContinueWatching)

        assertNotNull(section)
        assertEquals(true, section!!.items.single().isSaved)
    }

    @Test
    fun mapItemSection_marksBookmarkSectionsAsSaved() {
        val item = testItem(id = 42, type = ItemType.MOVIE)

        val watchLater = mapper.mapItemSection(listOf(item), HomeSectionType.WatchLater)
        val bookmarks = mapper.mapItemSection(listOf(item), HomeSectionType.Bookmarks)

        assertEquals(true, watchLater!!.items.single().isSaved)
        assertEquals(true, bookmarks!!.items.single().isSaved)
    }

    @Test
    fun mapItemSection_preservesSavedStateForRegularSections() {
        val item = testItem(id = 42, type = ItemType.SERIAL)

        val section = mapper.mapItemSection(listOf(item), HomeSectionType.Fresh)

        assertNotNull(section)
        assertEquals(false, section!!.items.single().isSaved)
    }

    private fun testItem(
        id: Int,
        type: ItemType,
    ): Item = Item(
        id = id,
        title = "Test",
        type = type,
    )
}
