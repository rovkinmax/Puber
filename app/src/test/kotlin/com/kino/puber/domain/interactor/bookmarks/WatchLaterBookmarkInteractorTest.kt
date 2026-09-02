package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WatchLaterBookmarkInteractorTest {

    private val bookmarkFolderInteractor = mockk<BookmarkFolderInteractor>()
    private lateinit var interactor: WatchLaterBookmarkInteractor

    @BeforeEach
    fun setup() {
        interactor = WatchLaterBookmarkInteractor(bookmarkFolderInteractor)
    }

    @Test
    fun getItems_returnsEmptyList_whenFolderDoesNotExist() = runTest {
        coEvery { bookmarkFolderInteractor.getQuickFolderItems() } returns
            QuickBookmarkItems(folder = null, items = emptyList())

        val result = interactor.getItems()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Item>(), result.getOrThrow())
        coVerify(exactly = 1) { bookmarkFolderInteractor.getQuickFolderItems() }
    }

    @Test
    fun getItems_loadsItemsFromWatchLaterFolder_whenFolderExists() = runTest {
        val folder = Bookmark(id = 7, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        val item = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        coEvery { bookmarkFolderInteractor.getQuickFolderItems() } returns
            QuickBookmarkItems(folder = folder, items = listOf(item))

        val result = interactor.getItems()

        assertEquals(listOf(item), result.getOrThrow())
    }

    @Test
    fun add_delegatesToQuickFolderMutation() = runTest {
        val createdFolder = Bookmark(id = 9, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        coEvery { bookmarkFolderInteractor.setQuickSaved(42, true) } returns
            QuickBookmarkUpdate(isSaved = true, folder = createdFolder)

        val result = interactor.add(itemId = 42)

        assertTrue(result.isSuccess)
        assertEquals(createdFolder, result.getOrThrow())
        coVerify(exactly = 1) { bookmarkFolderInteractor.setQuickSaved(42, true) }
    }

    @Test
    fun remove_isNoOp_whenQuickFolderDoesNotExist() = runTest {
        coEvery { bookmarkFolderInteractor.setQuickSaved(42, false) } returns
            QuickBookmarkUpdate(isSaved = false, folder = null)

        val result = interactor.remove(itemId = 42)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { bookmarkFolderInteractor.setQuickSaved(42, false) }
    }

    @Test
    fun isBookmarked_returnsTrue_whenItemBelongsToWatchLaterFolder() = runTest {
        coEvery { bookmarkFolderInteractor.isInQuickFolder(42) } returns true

        val result = interactor.isBookmarked(itemId = 42)

        assertEquals(true, result.getOrThrow())
    }
}
