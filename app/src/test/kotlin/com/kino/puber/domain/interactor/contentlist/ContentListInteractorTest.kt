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
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.main.model.TabType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun heroConfig_routesShortcutRequestWithGenreAndRetainsOnlyAnime() = runTest {
        val config = TabTypeConfig.heroConfigsFor(TabType.Anime)
            .single { it.type == "serial" }
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("hot", "serial", 1, ANIME_GENRE_ID.toString())
        } returns Result.success(
            page(
                anime,
                item(id = 2, title = "Movie"),
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(anime), result.items)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "serial", 1, ANIME_GENRE_ID.toString())
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun cartoonHeroConfig_refillsAcrossShortcutPagesAndSuppressesDuplicateIds() = runTest {
        val config = TabTypeConfig.heroConfigsFor(TabType.Cartoons)
            .single { it.type == "movie" }
        val firstCartoon = item(id = 2, title = "Cartoon 1", CARTOON_GENRE_ID)
        val secondCartoon = item(id = 3, title = "Cartoon 2", CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("hot", "movie", 1, CARTOON_GENRE_ID.toString())
        } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                firstCartoon,
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("hot", "movie", 2, CARTOON_GENRE_ID.toString())
        } returns Result.success(
            page(
                firstCartoon.copy(title = "Duplicate"),
                secondCartoon,
                current = 2,
                total = 2,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstCartoon, secondCartoon), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "movie", 1, CARTOON_GENRE_ID.toString())
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "movie", 2, CARTOON_GENRE_ID.toString())
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun filteredPage_refillsBatchAcrossServerPagesAndReturnsLastConsumedPagination() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val config = config(AnimeFilterMode.FollowPreference)
        val firstVisible = item(id = 2, title = "Movie 1")
        val secondVisible = item(id = 3, title = "Movie 2")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                firstVisible,
                current = 1,
                total = 3,
                perpage = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 4, title = "Anime 2", ANIME_GENRE_ID),
                secondVisible,
                current = 2,
                total = 3,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstVisible, secondVisible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 3, null, null) }
    }

    @Test
    fun filteredPage_failsBoundedly_whenCurrentDoesNotAdvanceToRequestedPage() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                current = 0,
                total = 3,
                perpage = 1,
            )
        )

        val error = try {
            interactor.loadPage(config, page = 1)
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(
            "Content pagination current 0 did not match requested page 1",
            error?.message,
        )
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 2, null, null) }
    }

    @Test
    fun filteredPage_failsBoundedly_whenFollowUpCurrentDoesNotAdvanceToRequestedPage() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                current = 1,
                total = 3,
                perpage = 1,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 2, title = "Anime 2", ANIME_GENRE_ID),
                current = 1,
                total = 3,
                perpage = 1,
            )
        )

        val error = try {
            interactor.loadPage(config, page = 1)
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(
            "Content pagination current 1 did not match requested page 2",
            error?.message,
        )
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 3, null, null) }
    }

    @Test
    fun filteredPage_preservesFinalConsumedPageOverflow() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val firstVisible = item(id = 1, title = "Cartoon 1", CARTOON_GENRE_ID)
        val secondVisible = item(id = 2, title = "Cartoon 2", CARTOON_GENRE_ID)
        val overflowVisible = item(id = 3, title = "Cartoon 3", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                firstVisible,
                item(id = 4, title = "Anime", ANIME_GENRE_ID),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                secondVisible,
                overflowVisible,
                current = 2,
                total = 2,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstVisible, secondVisible, overflowVisible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
    }

    @Test
    fun filteredPages_continueBeyondFivePagesUntilVisibleBatchIsRefilled() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        (1..6).forEach { pageNumber ->
            coEvery {
                api.getItems("movie", "updated", pageNumber, null, null)
            } returns Result.success(
                page(
                    item(id = pageNumber, title = "Anime $pageNumber", ANIME_GENRE_ID),
                    current = pageNumber,
                    total = 7,
                    perpage = 1,
                )
            )
        }
        val visible = item(id = 7, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 7, null, null) } returns Result.success(
            page(visible, current = 7, total = 7, perpage = 1)
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(7, result.pagination.current)
        (1..7).forEach { pageNumber ->
            coVerify(exactly = 1) {
                api.getItems("movie", "updated", pageNumber, null, null)
            }
        }
    }

    @Test
    fun filteredPages_stopAtServerEndAndSuppressDuplicateIds() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val visible = item(id = 10, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime 1", ANIME_GENRE_ID),
                visible,
                current = 1,
                total = 2,
                perpage = 3,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                visible.copy(title = "Duplicate"),
                item(id = 2, title = "Anime 2", ANIME_GENRE_ID),
                current = 2,
                total = 2,
                perpage = 3,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
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
        perpage: Int = items.size,
    ) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = current, perpage = perpage, total = total),
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
