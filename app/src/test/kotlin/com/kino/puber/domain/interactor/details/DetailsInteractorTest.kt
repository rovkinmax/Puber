package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.WatchingToggleResponse
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.TmdbCastRepository
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor
import com.kino.puber.domain.interactor.bookmarks.QuickBookmarkUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailsInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true)
    private val bookmarkFolderInteractor = mockk<BookmarkFolderInteractor>()
    private val tmdbCastRepository = mockk<TmdbCastRepository>(relaxed = true)
    private val interactor = DetailsInteractor(
        api,
        itemDetailsRepository,
        bookmarkFolderInteractor,
        tmdbCastRepository,
    )

    @Test
    fun isInWatchLaterFolder_usesConfiguredQuickFolderIdentity() = runTest {
        val item = movie(bookmarks = listOf(Bookmark(id = 7, title = "Renamed")))
        coEvery { bookmarkFolderInteractor.isInQuickFolder(item.id) } returns true

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 1) { bookmarkFolderInteractor.isInQuickFolder(item.id) }
    }

    @Test
    fun isBookmarked_simpleSeriesRemainsIndependentFromFolderBookmarks() = runTest {
        val item = Item(id = 42, title = "Series", type = ItemType.SERIAL)

        val result = interactor.isBookmarked(item, BookmarkMode.Simple)

        assertEquals(false, result)
        coVerify(exactly = 0) { bookmarkFolderInteractor.getItemFolders(any()) }
    }

    @Test
    fun isBookmarked_extendedChecksAllFolderMemberships() = runTest {
        val item = movie(bookmarks = emptyList())
        coEvery { bookmarkFolderInteractor.getItemFolders(item.id) } returns listOf(
            com.kino.puber.data.api.models.BookmarkFolder(id = 8, title = "Other")
        )

        val result = interactor.isBookmarked(item, BookmarkMode.Extended)

        assertEquals(true, result)
        coVerify(exactly = 1) { bookmarkFolderInteractor.getItemFolders(item.id) }
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
    fun getTmdbCast_delegatesToRepository() = runTest {
        val cast = listOf(TmdbCastMember(name = "Actor", profileUrl = "https://image"))
        coEvery { tmdbCastRepository.getCast("tt123") } returns cast

        assertEquals(cast, interactor.getTmdbCast("tt123"))
        coVerify(exactly = 1) { tmdbCastRepository.getCast("tt123") }
    }

    @Test
    fun setMovieBookmarked_removesOnlyConfiguredQuickFolder() = runTest {
        val folder = Bookmark(id = 9, title = "Renamed quick folder")
        coEvery { bookmarkFolderInteractor.setQuickSaved(42, false) } returns
            QuickBookmarkUpdate(isSaved = false, folder = folder)

        val result = interactor.setMovieBookmarked(id = 42, bookmarked = false)

        assertEquals(false, result.isBookmarked)
        assertEquals(folder.title, result.folderTitle)
        coVerify(exactly = 1) { bookmarkFolderInteractor.setQuickSaved(42, false) }
        coVerify(exactly = 0) { api.removeBookmarkItem(any(), any()) }
        coVerify(exactly = 0) { itemDetailsRepository.refresh(any()) }
    }

    @Test
    fun setMovieBookmarked_addsItemToConfiguredQuickFolder() = runTest {
        val folder = Bookmark(id = 7, title = "My quick folder")
        coEvery { bookmarkFolderInteractor.setQuickSaved(42, true) } returns
            QuickBookmarkUpdate(isSaved = true, folder = folder)

        val result = interactor.setMovieBookmarked(id = 42, bookmarked = true)

        assertEquals(true, result.isBookmarked)
        assertEquals(folder.title, result.folderTitle)
        coVerify(exactly = 0) { itemDetailsRepository.refresh(any()) }
    }
    @Test
    fun setMovieWatched_returnsApiConfirmedState_withoutRefreshingDetails() = runTest {
        coEvery { api.toggleWatchingStatus(42, status = 1) } returns Result.success(
            WatchingToggleResponse(status = 200, watched = 1)
        )

        val result = interactor.setMovieWatched(42, watched = true)

        assertEquals(true, result.isWatched)
        coVerify(exactly = 0) { itemDetailsRepository.refresh(any()) }
    }

    @Test
    fun setEpisodeWatched_returnsRequestedStateWhenApiOmitsConfirmation_withoutRefreshingDetails() = runTest {
        coEvery { api.toggleWatchingStatus(42, status = 1, season = 1, video = 2) } returns Result.success(
            WatchingToggleResponse(status = 200)
        )

        val result = interactor.setEpisodeWatched(42, season = 1, episode = 2, watched = true)

        assertEquals(true, result.isWatched)
        coVerify(exactly = 0) { itemDetailsRepository.refresh(any()) }
    }

    @Test
    fun setSeasonWatched_returnsApiConfirmedState_withoutRefreshingDetails() = runTest {
        coEvery { api.toggleWatchingStatus(42, status = 0, season = 1) } returns Result.success(
            WatchingToggleResponse(status = 200, watched = 0)
        )

        val result = interactor.setSeasonWatched(42, season = 1, watched = false)

        assertEquals(false, result.isWatched)
        coVerify(exactly = 0) { itemDetailsRepository.refresh(any()) }
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
