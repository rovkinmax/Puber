package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContentListInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val contentPreferences = MutableStateFlow(defaultContentPreferences())
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository> {
        every { contentPreferences } returns this@ContentListInteractorTest.contentPreferences
    }
    private val interactor = ContentListInteractor(api, navigationPreferencesRepository)

    @BeforeEach
    fun setup() {
        mockkObject(KinoPubConfig)
        every { KinoPubConfig.CURRENT_API_DOMAIN } returns "unit.test"
        contentPreferences.value = defaultContentPreferences()
        interactor.invalidateFirstPageCache()
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

        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        interactor.invalidateFirstPageCache()
        assertEquals(refreshedPage, interactor.loadPage(config, page = 1))

        coVerify(exactly = 2) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun noneMode_returnsUnfilteredPageWithOneServerCall() = runTest {
        val config = config(AnimeFilterMode.None)
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            item(id = 2, title = "Unknown"),
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        assertEquals(response, interactor.loadPage(config, page = 1))

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun followPreference_whenAnimeIsShown_returnsUnfilteredPageWithOneServerCall() = runTest {
        val config = config(AnimeFilterMode.FollowPreference)
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            item(id = 2, title = "Movie"),
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        assertEquals(response, interactor.loadPage(config, page = 1))

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun activeAnimeFilter_matchesSectionModeAndPreference() {
        assertFalse(interactor.hasActiveAnimeFilter(config(AnimeFilterMode.None)))
        assertFalse(interactor.hasActiveAnimeFilter(config(AnimeFilterMode.FollowPreference)))
        assertTrue(interactor.hasActiveAnimeFilter(config(AnimeFilterMode.Exclude)))
        assertTrue(interactor.hasActiveAnimeFilter(config(AnimeFilterMode.Only)))

        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)

        assertTrue(interactor.hasActiveAnimeFilter(config(AnimeFilterMode.FollowPreference)))
    }

    @Test
    fun followPreference_whenAnimeIsHidden_excludesAnimeAndRetainsMissingGenres() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val config = config(AnimeFilterMode.FollowPreference)
        val visible = item(id = 2, title = "Movie")
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            visible,
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(response.pagination, result.pagination)
    }

    @Test
    fun excludeMode_removesMixedCartoonAnimeItems() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val cartoon = item(id = 2, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(
                    id = 1,
                    title = "Anime cartoon",
                    CARTOON_GENRE_ID,
                    ANIME_GENRE_ID,
                ),
                cartoon,
            )
        )

        assertEquals(listOf(cartoon), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun onlyMode_retainsAnimeAndDropsUnclassifiedItems() = runTest {
        val config = config(AnimeFilterMode.Only)
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                anime,
                item(id = 2, title = "Movie"),
            )
        )

        assertEquals(listOf(anime), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun emptyFilteredPage_advancesUntilVisibleItemsAndReturnsLastConsumedPagination() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val config = config(AnimeFilterMode.FollowPreference)
        val visible = item(id = 2, title = "Movie")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                current = 1,
                total = 4,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(visible, current = 2, total = 4)
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
    }

    @Test
    fun emptyFilteredPages_stopAfterFiveConsecutiveServerPages() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        (1..5).forEach { pageNumber ->
            coEvery {
                api.getItems("movie", "updated", pageNumber, null, null)
            } returns Result.success(
                page(
                    item(id = pageNumber, title = "Anime $pageNumber", ANIME_GENRE_ID),
                    current = pageNumber,
                    total = 10,
                )
            )
        }

        val result = interactor.loadPage(config, page = 1)

        assertEquals(emptyList<Item>(), result.items)
        assertEquals(5, result.pagination.current)
        (1..5).forEach { pageNumber ->
            coVerify(exactly = 1) {
                api.getItems("movie", "updated", pageNumber, null, null)
            }
        }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 6, null, null) }
    }

    @Test
    fun emptyFilteredPages_stopAtServerEnd() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime 1", ANIME_GENRE_ID),
                current = 1,
                total = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 2, title = "Anime 2", ANIME_GENRE_ID),
                current = 2,
                total = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(emptyList<Item>(), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 2) { api.getItems("movie", "updated", any(), null, null) }
    }

    @Test
    fun firstPageCache_isSeparatedByFilterModeAndAnimePreference() = runTest {
        val followPreference = config(AnimeFilterMode.FollowPreference)
        val exclude = followPreference.copy(animeFilterMode = AnimeFilterMode.Exclude)
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        val movie = item(id = 2, title = "Movie")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns
            Result.success(page(anime)) andThen
            Result.success(page(movie)) andThen
            Result.success(page(movie))

        assertEquals(listOf(anime), interactor.loadPage(followPreference, page = 1).items)
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        assertEquals(listOf(movie), interactor.loadPage(followPreference, page = 1).items)
        assertEquals(listOf(movie), interactor.loadPage(exclude, page = 1).items)

        coVerify(exactly = 3) { api.getItems("movie", "updated", 1, null, null) }
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

    private fun config(filterMode: AnimeFilterMode) = SectionConfig(
        id = "section_${filterMode.name}",
        title = filterMode.name,
        type = "movie",
        sort = "updated",
        animeFilterMode = filterMode,
    )

    private fun page(
        vararg items: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )

    private fun item(
        id: Int,
        title: String,
        vararg genreIds: Int,
    ) = Item(
        id = id,
        title = title,
        type = ItemType.MOVIE,
        genres = genreIds
            .map { genreId -> Genre(id = genreId, title = "Genre $genreId") }
            .takeIf(List<Genre>::isNotEmpty),
    )

    private fun defaultContentPreferences() = ContentPreferences(
        showCartoonsTab = false,
        showAnimeTab = false,
        showAnime = true,
    )
}
