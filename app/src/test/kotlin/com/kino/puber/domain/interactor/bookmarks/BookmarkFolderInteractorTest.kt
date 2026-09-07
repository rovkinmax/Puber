package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BookmarkFolderInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true)

    @Test
    fun getQuickFolder_usesConfiguredIdAfterFolderWasRenamed() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 8)
        val renamed = Bookmark(id = 8, title = "Later")
        coEvery { api.getBookmarks() } returns Result.success(listOf(renamed))

        val folder = interactor(preferences).getQuickFolder()

        assertEquals(renamed, folder)
        assertEquals(8, preferences.quickFolderId.value)
    }

    @Test
    fun getQuickFolder_replacesStaleIdWithLegacyFolderId() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 99)
        val legacy = Bookmark(id = 7, title = BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(legacy))

        val folder = interactor(preferences).getQuickFolder()

        assertEquals(legacy, folder)
        assertEquals(7, preferences.quickFolderId.value)
    }

    @Test
    fun ensureQuickFolder_createsAndPersistsFolderWhenNoLegacyFolderExists() = runTest {
        val preferences = BookmarkPreferencesRepository()
        val created = Bookmark(id = 12, title = BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE)
        coEvery { api.getBookmarks() } returns Result.success(emptyList())
        coEvery {
            api.createBookmark(BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE)
        } returns Result.success(created)

        val folder = interactor(preferences).ensureQuickFolder()

        assertEquals(created, folder)
        assertEquals(12, preferences.quickFolderId.value)
    }

    @Test
    fun setQuickSaved_removesOnlyConfiguredFolder() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 7)
        val quick = Bookmark(id = 7, title = "Quick")
        val other = Bookmark(id = 9, title = "Family")
        coEvery { api.getBookmarks() } returns Result.success(listOf(quick, other))
        coEvery { api.removeBookmarkItem(itemId = 42, folderId = quick.id) } returns Result.success(Unit)

        val update = interactor(preferences).setQuickSaved(itemId = 42, saved = false)

        assertFalse(update.isSaved)
        assertEquals(quick, update.folder)
        coVerify(exactly = 1) { api.removeBookmarkItem(itemId = 42, folderId = 7) }
        coVerify(exactly = 0) { api.removeBookmarkItem(itemId = 42, folderId = 9) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(42) }
    }

    @Test
    fun isInQuickFolder_matchesByFolderIdInsteadOfTitle() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 7)
        coEvery { api.getBookmarks() } returns Result.success(listOf(Bookmark(7, "Renamed")))
        coEvery { api.getItemBookmarkFolders(42) } returns Result.success(
            listOf(
                BookmarkFolder(id = 7, title = "Different response title"),
                BookmarkFolder(id = 9, title = BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE),
            )
        )

        val saved = interactor(preferences).isInQuickFolder(42)

        assertTrue(saved)
    }

    @Test
    fun createFolder_trimsTitle() = runTest {
        val preferences = BookmarkPreferencesRepository()
        val created = Bookmark(id = 13, title = "Family")
        coEvery { api.createBookmark("Family") } returns Result.success(created)

        val folder = interactor(preferences).createFolder("  Family  ")

        assertEquals(created, folder)
        coVerify(exactly = 1) { api.createBookmark("Family") }
    }

    @Test
    fun setItemInFolder_propagatesCancellationWithoutInvalidatingDetails() {
        val preferences = BookmarkPreferencesRepository()
        coEvery { api.addBookmarkItem(itemId = 42, folderId = 7) } throws CancellationException()

        assertThrows(CancellationException::class.java) {
            runTest {
                interactor(preferences).setItemInFolder(42, 7, selected = true)
            }
        }
        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
    }

    @Test
    fun getOtherFolderItems_skipsTheQuickFolderAndEmptyFoldersAndDeduplicates() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 7)
        coEvery { api.getBookmarks() } returns Result.success(
            listOf(
                Bookmark(id = 7, title = "Буду смотреть", count = 3),
                Bookmark(id = 8, title = "Family", count = 2),
                Bookmark(id = 9, title = "Empty", count = 0),
                Bookmark(id = 10, title = "Later", count = 1),
            )
        )
        coEvery { api.getBookmarkItems(8, null) } returns Result.success(page(item(1), item(2)))
        coEvery { api.getBookmarkItems(10, null) } returns Result.success(page(item(2), item(3)))

        val items = interactor(preferences).getOtherFolderItems(limit = 20)

        assertEquals(listOf(1, 2, 3), items.map { it.id })
        coVerify(exactly = 0) { api.getBookmarkItems(7, any()) }
        coVerify(exactly = 0) { api.getBookmarkItems(9, any()) }
    }

    @Test
    fun getOtherFolderItems_stopsRequestingFoldersOnceTheLimitIsReached() = runTest {
        val preferences = BookmarkPreferencesRepository(quickFolderId = 7)
        coEvery { api.getBookmarks() } returns Result.success(
            listOf(
                Bookmark(id = 8, title = "Family", count = 2),
                Bookmark(id = 10, title = "Later", count = 5),
            )
        )
        coEvery { api.getBookmarkItems(8, null) } returns Result.success(page(item(1), item(2)))

        val items = interactor(preferences).getOtherFolderItems(limit = 2)

        assertEquals(listOf(1, 2), items.map { it.id })
        coVerify(exactly = 0) { api.getBookmarkItems(10, any()) }
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun page(vararg items: Item) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = 1, perpage = items.size, total = 1),
    )

    private fun interactor(preferences: BookmarkPreferencesRepository) = BookmarkFolderInteractor(
        api = api,
        preferences = preferences,
        itemDetailsRepository = itemDetailsRepository,
    )
}
