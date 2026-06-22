package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WatchLaterBookmarkInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private lateinit var interactor: WatchLaterBookmarkInteractor

    @BeforeEach
    fun setup() {
        interactor = WatchLaterBookmarkInteractor(api)
    }

    @Test
    fun getItems_returnsEmptyList_whenFolderDoesNotExist() = runTest {
        coEvery { api.getBookmarks() } returns Result.success(emptyList())

        val result = interactor.getItems()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Item>(), result.getOrThrow())
        coVerify(exactly = 0) { api.createBookmark(any()) }
        coVerify(exactly = 0) { api.getBookmarkItems(any(), any()) }
    }

    @Test
    fun getItems_loadsItemsFromWatchLaterFolder_whenFolderExists() = runTest {
        val folder = Bookmark(id = 7, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        val item = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(folder))
        coEvery { api.getBookmarkItems(folder.id, null) } returns Result.success(
            PaginatedResponse(
                items = listOf(item),
                pagination = Pagination(current = 1, perpage = 20, total = 1),
            )
        )

        val result = interactor.getItems()

        assertEquals(listOf(item), result.getOrThrow())
    }

    @Test
    fun add_createsFolderBeforeAddingItem_whenFolderDoesNotExist() = runTest {
        val createdFolder = Bookmark(id = 9, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        coEvery { api.getBookmarks() } returns Result.success(emptyList())
        coEvery { api.createBookmark(WatchLaterBookmarkInteractor.FOLDER_TITLE) } returns Result.success(createdFolder)
        coEvery { api.addBookmarkItem(itemId = 42, folderId = createdFolder.id) } returns Result.success(Unit)

        val result = interactor.add(itemId = 42)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { api.createBookmark(WatchLaterBookmarkInteractor.FOLDER_TITLE) }
        coVerify(exactly = 1) { api.addBookmarkItem(itemId = 42, folderId = createdFolder.id) }
    }

    @Test
    fun remove_isNoOp_whenFolderDoesNotExist() = runTest {
        coEvery { api.getBookmarks() } returns Result.success(emptyList())

        val result = interactor.remove(itemId = 42)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { api.removeBookmarkItem(any(), any()) }
    }

    @Test
    fun isBookmarked_returnsTrue_whenItemBelongsToWatchLaterFolder() = runTest {
        coEvery { api.getItemBookmarkFolders(itemId = 42) } returns Result.success(
            listOf(BookmarkFolder(id = 7, title = WatchLaterBookmarkInteractor.FOLDER_TITLE))
        )

        val result = interactor.isBookmarked(itemId = 42)

        assertEquals(true, result.getOrThrow())
    }
}
