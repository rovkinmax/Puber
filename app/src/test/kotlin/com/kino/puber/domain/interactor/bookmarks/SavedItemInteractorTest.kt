package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.WatchlistToggleResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SavedItemInteractorTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>(relaxed = true)
    private lateinit var interactor: SavedItemInteractor

    @BeforeEach
    fun setup() {
        interactor = SavedItemInteractor(
            api = api,
            watchLaterBookmarkInteractor = watchLaterBookmarkInteractor,
        )
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
    }

    private fun testSeries(id: Int): Item = Item(
        id = id,
        title = "Series",
        type = ItemType.SERIAL,
    )
}
