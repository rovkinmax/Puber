package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailsInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true)
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>()
    private val interactor = DetailsInteractor(api, itemDetailsRepository, watchLaterBookmarkInteractor)

    @Test
    fun isInWatchLaterFolder_returnsTrueFromLocalBookmarks_withoutLiveLookup() = runTest {
        val item = movie(
            bookmarks = listOf(Bookmark(id = 7, title = WatchLaterBookmarkInteractor.FOLDER_TITLE))
        )

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 0) { watchLaterBookmarkInteractor.isBookmarked(any()) }
    }

    @Test
    fun isInWatchLaterFolder_fallsBackToLiveFolderLookup_whenLocalBookmarksAreEmpty() = runTest {
        val item = movie(bookmarks = emptyList())
        coEvery { api.getItemBookmarkFolders(item.id) } returns Result.success(
            listOf(BookmarkFolder(id = 8, title = "Other"))
        )

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 1) { api.getItemBookmarkFolders(item.id) }
    }

    @Test
    fun isInWatchLaterFolder_returnsTrueFromAnyLocalBookmark_withoutLiveLookup() = runTest {
        val item = movie(bookmarks = listOf(Bookmark(id = 8, title = "Other")))

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 0) { api.getItemBookmarkFolders(any()) }
    }

    @Test
    fun getSimilarItems_returnsApiItems() = runTest {
        val similar = Item(id = 100, title = "Similar", type = ItemType.MOVIE)
        coEvery { api.getSimilarItems(42) } returns Result.success(
            ApiResponseList(
                items = listOf(similar),
            )
        )

        val result = interactor.getSimilarItems(42)

        assertEquals(listOf(similar), result)
    }

    @Test
    fun setMovieBookmarked_removesItemFromActualBookmarkFolder() = runTest {
        val folder = BookmarkFolder(id = 9, title = "For weekend")
        val refreshed = movie(bookmarks = emptyList())
        coEvery { api.getItemBookmarkFolders(42) } returns Result.success(listOf(folder)) andThen Result.success(emptyList())
        coEvery { api.removeBookmarkItem(itemId = 42, folderId = folder.id) } returns Result.success(Unit)
        coEvery { itemDetailsRepository.refresh(42) } returns refreshed

        val result = interactor.setMovieBookmarked(id = 42, bookmarked = false)

        assertEquals(false, result.isBookmarked)
        assertEquals(folder.title, result.folderTitle)
        assertEquals(refreshed, result.item)
        coVerify(exactly = 1) { api.removeBookmarkItem(itemId = 42, folderId = folder.id) }
    }

    @Test
    fun setMovieBookmarked_keepsBookmarkActiveWhenItemRemainsInAnotherFolder() = runTest {
        val firstFolder = BookmarkFolder(id = 9, title = "For weekend")
        val secondFolder = BookmarkFolder(id = 10, title = "Favorites")
        val refreshed = movie(bookmarks = listOf(Bookmark(id = secondFolder.id, title = secondFolder.title)))
        coEvery { api.getItemBookmarkFolders(42) } returns Result.success(
            listOf(firstFolder, secondFolder),
        ) andThen Result.success(listOf(secondFolder))
        coEvery { api.removeBookmarkItem(itemId = 42, folderId = firstFolder.id) } returns Result.success(Unit)
        coEvery { itemDetailsRepository.refresh(42) } returns refreshed

        val result = interactor.setMovieBookmarked(id = 42, bookmarked = false)

        assertEquals(true, result.isBookmarked)
        assertEquals(firstFolder.title, result.folderTitle)
        assertEquals(refreshed, result.item)
        coVerify(exactly = 1) { api.removeBookmarkItem(itemId = 42, folderId = firstFolder.id) }
    }

    @Test
    fun setMovieBookmarked_addsItemToWatchLaterFolderByDefault() = runTest {
        val folder = Bookmark(id = 7, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        val refreshed = movie(bookmarks = listOf(folder))
        coEvery { watchLaterBookmarkInteractor.add(42) } returns Result.success(folder)
        coEvery { itemDetailsRepository.refresh(42) } returns refreshed

        val result = interactor.setMovieBookmarked(id = 42, bookmarked = true)

        assertEquals(true, result.isBookmarked)
        assertEquals(WatchLaterBookmarkInteractor.FOLDER_TITLE, result.folderTitle)
        assertEquals(refreshed, result.item)
    }

    private fun movie(bookmarks: List<Bookmark>?): Item {
        return Item(
            id = 42,
            title = "Movie",
            type = ItemType.MOVIE,
            bookmarks = bookmarks,
        )
    }
}
