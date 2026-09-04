package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.WatchlistToggleResponse
import com.kino.puber.data.repository.ItemDetailsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SavedItemInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val bookmarkFolderInteractor = mockk<BookmarkFolderInteractor>()
    private val itemDetailsRepository = mockk<ItemDetailsRepository>()
    private lateinit var interactor: SavedItemInteractor

    @BeforeEach
    fun setup() {
        interactor = SavedItemInteractor(
            api = api,
            bookmarkFolderInteractor = bookmarkFolderInteractor,
            itemDetailsRepository = itemDetailsRepository,
        )
        every { itemDetailsRepository.invalidate(any()) } returns Unit
    }

    @Test
    fun setSaved_doesNotToggleSeries_whenAlreadyInRequestedState() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            ApiResponseList(items = listOf(testSeries(id = 42)))
        )

        val result = interactor.setSaved(itemId = 42, isSeriesLike = true, saved = true)

        assertEquals(true, result.getOrThrow())
        coVerify(exactly = 0) { api.toggleWatchlist(any()) }
    }

    @Test
    fun setSaved_togglesSeries_whenCurrentStateDiffers() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            ApiResponseList(items = listOf(testSeries(id = 42)))
        )
        coEvery { api.toggleWatchlist(42) } returns Result.success(
            WatchlistToggleResponse(status = 200, watching = false)
        )

        val result = interactor.setSaved(itemId = 42, isSeriesLike = true, saved = false)

        assertEquals(false, result.getOrThrow())
        coVerify(exactly = 1) { api.toggleWatchlist(42) }
        verify(exactly = 1) { itemDetailsRepository.invalidate(42) }
    }

    @Test
    fun removeMovie_updatesOnlyConfiguredQuickFolder() = runTest {
        coEvery {
            bookmarkFolderInteractor.setQuickSaved(itemId = 42, saved = false)
        } returns QuickBookmarkUpdate(isSaved = false, folder = null)

        val result = interactor.setSaved(itemId = 42, isSeriesLike = false, saved = false)

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow())
        coVerify(exactly = 1) {
            bookmarkFolderInteractor.setQuickSaved(itemId = 42, saved = false)
        }
        coVerify(exactly = 0) { api.getItemBookmarkFolders(any()) }
        coVerify(exactly = 0) { api.removeBookmarkItem(any(), any()) }
    }

    @Test
    fun removeMovie_rethrowsCancellationFromQuickFolderMutation() = runTest {
        coEvery {
            bookmarkFolderInteractor.setQuickSaved(itemId = 42, saved = false)
        } throws kotlinx.coroutines.CancellationException("cancelled")

        var cancelled = false
        try {
            interactor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        coVerify(exactly = 1) {
            bookmarkFolderInteractor.setQuickSaved(itemId = 42, saved = false)
        }
    }

    @Test
    fun directBookmarkMutation_invalidatesItemDetailsAfterWriteSuccess() = runTest {
        coEvery { api.removeBookmarkItem(itemId = 42, folderId = 7) } returns Result.success(Unit)
        val bookmarkInteractor = BookmarkInteractor(api, itemDetailsRepository)

        bookmarkInteractor.setItemSaved(itemId = 42, folderId = 7, saved = false)

        verify(exactly = 1) { itemDetailsRepository.invalidate(42) }
    }

    @Test
    fun directBookmarkMutation_doesNotInvalidateItemDetailsAfterWriteFailure() = runTest {
        coEvery { api.removeBookmarkItem(itemId = 42, folderId = 7) } returns
            Result.failure(IllegalStateException("write failed"))
        val bookmarkInteractor = BookmarkInteractor(api, itemDetailsRepository)

        runCatching {
            bookmarkInteractor.setItemSaved(itemId = 42, folderId = 7, saved = false)
        }

        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
    }

    private fun testSeries(id: Int): Item = Item(
        id = id,
        title = "Series",
        type = ItemType.SERIAL,
    )
}
