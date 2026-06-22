package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
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
    fun isInWatchLaterFolder_fallsBackToLiveLookup_whenLocalBookmarksAreEmpty() = runTest {
        val item = movie(bookmarks = emptyList())
        coEvery { watchLaterBookmarkInteractor.isBookmarked(item.id) } returns Result.success(true)

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 1) { watchLaterBookmarkInteractor.isBookmarked(item.id) }
    }

    @Test
    fun isInWatchLaterFolder_fallsBackToLiveLookup_whenLocalBookmarksDoNotContainWatchLater() = runTest {
        val item = movie(bookmarks = listOf(Bookmark(id = 8, title = "Other")))
        coEvery { watchLaterBookmarkInteractor.isBookmarked(item.id) } returns Result.success(true)

        val result = interactor.isInWatchLaterFolder(item)

        assertEquals(true, result)
        coVerify(exactly = 1) { watchLaterBookmarkInteractor.isBookmarked(item.id) }
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
