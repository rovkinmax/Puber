package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContentListInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val interactor = ContentListInteractor(api)

    @BeforeEach
    fun setup() {
        mockkObject(KinoPubConfig)
        every { KinoPubConfig.CURRENT_API_DOMAIN } returns "unit.test"
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(KinoPubConfig)
    }

    @Test
    fun invalidateFirstPageCache_clearsCachedFirstPages() = runTest {
        val config = SectionConfig(id = "fresh", title = "Fresh", type = "movie", sort = "updated")
        val firstPage = page(item(id = 1, title = "Before"))
        val refreshedPage = page(item(id = 2, title = "After"))
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(firstPage) andThen
            Result.success(refreshedPage)
        interactor.invalidateFirstPageCache()

        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        interactor.invalidateFirstPageCache()
        assertEquals(refreshedPage, interactor.loadPage(config, page = 1))

        coVerify(exactly = 2) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun invalidateItemDetails_clearsCachedItemDetails() = runTest {
        val firstItem = item(id = 42, title = "Before")
        val refreshedItem = item(id = 42, title = "After")
        coEvery { api.getItemDetails(42) } returns Result.success(ApiResponse(item = firstItem)) andThen
            Result.success(ApiResponse(item = refreshedItem))

        assertEquals(firstItem, interactor.getItemDetails(42))
        assertEquals(firstItem, interactor.getItemDetails(42))
        interactor.invalidateItemDetails(42)
        assertEquals(refreshedItem, interactor.getItemDetails(42))

        coVerify(exactly = 2) { api.getItemDetails(42) }
    }

    private fun page(item: Item) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )

    private fun item(id: Int, title: String) = Item(
        id = id,
        title = title,
        type = ItemType.MOVIE,
    )
}
