package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>()
    private val interactor = HomeInteractor(api, watchLaterBookmarkInteractor)

    @Test
    fun getGenericBookmarkItems_skipsWatchLaterFolder() = runTest {
        val watchLaterFolder = Bookmark(id = 1, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        val genericFolder = Bookmark(id = 2, title = "Favorites")
        val item = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(watchLaterFolder, genericFolder))
        coEvery { api.getBookmarkItems(genericFolder.id, null) } returns Result.success(
            PaginatedResponse(
                items = listOf(item),
                pagination = Pagination(current = 1, perpage = 20, total = 1),
            )
        )

        val result = interactor.getGenericBookmarkItems()

        assertEquals(listOf(item), result.getOrThrow())
        coVerify(exactly = 0) { api.getBookmarkItems(watchLaterFolder.id, any()) }
        coVerify(exactly = 1) { api.getBookmarkItems(genericFolder.id, null) }
    }

    @Test
    fun getGenericBookmarkItems_returnsEmptyList_whenOnlyWatchLaterFolderExists() = runTest {
        val watchLaterFolder = Bookmark(id = 1, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(watchLaterFolder))

        val result = interactor.getGenericBookmarkItems()

        assertEquals(emptyList<Item>(), result.getOrThrow())
        coVerify(exactly = 0) { api.getBookmarkItems(any(), any()) }
    }
}
