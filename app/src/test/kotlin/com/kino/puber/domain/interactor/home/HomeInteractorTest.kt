package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeInteractorTest {

    private lateinit var api: KinoPubApiClient
    private lateinit var watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor
    private lateinit var navigationPreferencesRepository: NavigationPreferencesRepository
    private lateinit var contentPreferences: MutableStateFlow<ContentPreferences>
    private lateinit var interactor: HomeInteractor

    @BeforeEach
    fun setup() {
        api = mockk()
        watchLaterBookmarkInteractor = mockk()
        navigationPreferencesRepository = mockk()
        contentPreferences = MutableStateFlow(defaultContentPreferences())
        every { navigationPreferencesRepository.contentPreferences } returns contentPreferences
        interactor = HomeInteractor(
            api = api,
            watchLaterBookmarkInteractor = watchLaterBookmarkInteractor,
            navigationPreferencesRepository = navigationPreferencesRepository,
        )
    }

    @Test
    fun discovery_passthroughIncludesAnimeAndUsesOnePage_whenAnimeIsShown() = runTest {
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        val movie = item(id = 2)
        coEvery {
            api.getItemsByShortcut("fresh", type = "movie", page = null, genre = null)
        } returns Result.success(page(anime, movie, current = 1, total = 3))

        val result = interactor.getFreshItems("movie")

        assertEquals(listOf(anime, movie), result.getOrThrow())
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", type = "movie", page = null, genre = null)
        }
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", type = "movie", page = 2, genre = null)
        }
    }

    @Test
    fun discovery_filtersAnimeAndScansUntilFirstPageSizeIsRefilled() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        val firstVisible = item(id = 2)
        val secondVisible = item(id = 3)
        val missingGenres = item(id = 4)
        coEvery {
            api.getItemsByShortcut("popular", type = "serial", page = null, genre = null)
        } returns Result.success(page(anime, firstVisible, anime.copy(id = 5), current = 1, total = 3))
        coEvery {
            api.getItemsByShortcut("popular", type = "serial", page = 2, genre = null)
        } returns Result.success(page(anime.copy(id = 6), secondVisible, current = 2, total = 3))
        coEvery {
            api.getItemsByShortcut("popular", type = "serial", page = 3, genre = null)
        } returns Result.success(page(missingGenres, current = 3, total = 3))

        val result = interactor.getPopularByType("serial")

        assertEquals(listOf(firstVisible, secondVisible, missingGenres), result.getOrThrow())
        coVerify(exactly = 1) {
            api.getItemsByShortcut("popular", type = "serial", page = 2, genre = null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("popular", type = "serial", page = 3, genre = null)
        }
    }

    @Test
    fun hotDiscovery_refillsOnlyToExplicitLimit_whenAnimeIsHidden() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        val firstVisible = item(id = 2)
        val secondVisible = item(id = 3)
        coEvery {
            api.getItemsByShortcut("hot", type = "movie", page = null, genre = null)
        } returns Result.success(page(anime, firstVisible, anime.copy(id = 4), current = 1, total = 3))
        coEvery {
            api.getItemsByShortcut("hot", type = "movie", page = 2, genre = null)
        } returns Result.success(page(secondVisible, item(id = 5), current = 2, total = 3))

        val result = interactor.getHotItems(type = "movie", limit = 2)

        assertEquals(listOf(firstVisible, secondVisible), result.getOrThrow())
        coVerify(exactly = 0) {
            api.getItemsByShortcut("hot", type = "movie", page = 3, genre = null)
        }
    }

    @Test
    fun discovery_continuesBeyondFivePagesUntilVisibleBatchIsRefilled() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        coEvery {
            api.getItemsByShortcut("fresh", type = "movie", page = null, genre = null)
        } returns Result.success(page(anime, current = 1, total = 7))
        (2..6).forEach { pageNumber ->
            coEvery {
                api.getItemsByShortcut("fresh", type = "movie", page = pageNumber, genre = null)
            } returns Result.success(page(anime.copy(id = pageNumber), current = pageNumber, total = 7))
        }
        val visible = item(id = 7)
        coEvery {
            api.getItemsByShortcut("fresh", type = "movie", page = 7, genre = null)
        } returns Result.success(page(visible, current = 7, total = 7))

        val result = interactor.getFreshItems("movie")

        assertEquals(listOf(visible), result.getOrThrow())
        coVerify(exactly = 7) {
            api.getItemsByShortcut("fresh", type = "movie", page = any(), genre = null)
        }
    }

    @Test
    fun discovery_failsBoundedly_whenFollowUpCurrentDoesNotAdvanceToRequestedPage() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        coEvery {
            api.getItemsByShortcut("fresh", type = "movie", page = null, genre = null)
        } returns Result.success(page(anime, current = 1, total = 3))
        coEvery {
            api.getItemsByShortcut("fresh", type = "movie", page = 2, genre = null)
        } returns Result.success(page(anime.copy(id = 2), current = 1, total = 3))

        val result = interactor.getFreshItems("movie")

        assertEquals(
            "Home discovery pagination current 1 did not match requested page 2",
            result.exceptionOrNull()?.message,
        )
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", type = "movie", page = null, genre = null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", type = "movie", page = 2, genre = null)
        }
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", type = "movie", page = 3, genre = null)
        }
    }

    @Test
    fun discovery_stopsAtServerEndAndSuppressesDuplicateIds() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 1, genreIds = intArrayOf(ANIME_GENRE_ID))
        val visible = item(id = 2)
        coEvery {
            api.getItemsByShortcut("popular", type = "movie", page = null, genre = null)
        } returns Result.success(page(anime, visible, anime.copy(id = 3), current = 1, total = 2))
        coEvery {
            api.getItemsByShortcut("popular", type = "movie", page = 2, genre = null)
        } returns Result.success(page(visible, anime.copy(id = 4), current = 2, total = 2))

        val result = interactor.getPopularByType("movie")

        assertEquals(listOf(visible), result.getOrThrow())
        coVerify(exactly = 0) {
            api.getItemsByShortcut("popular", type = "movie", page = 3, genre = null)
        }
    }

    @Test
    fun personalListsAndCollectionsRemainUnfiltered_whenAnimeIsHidden() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val anime = item(id = 42, genreIds = intArrayOf(ANIME_GENRE_ID))
        val genericFolder = Bookmark(id = 2, title = "Favorites")
        val collection = KCollection(id = 7, title = "Collection")
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            ApiResponseList(items = listOf(anime))
        )
        coEvery { watchLaterBookmarkInteractor.getItems() } returns Result.success(listOf(anime))
        coEvery { api.getBookmarks() } returns Result.success(listOf(genericFolder))
        coEvery { api.getBookmarkItems(genericFolder.id, null) } returns Result.success(page(anime))
        coEvery { api.getCollections(sort = null, page = 1) } returns Result.success(
            PaginatedResponse(
                items = listOf(collection),
                pagination = Pagination(current = 1, perpage = 20, total = 1),
            )
        )

        assertEquals(listOf(anime), interactor.getWatchingItems().getOrThrow())
        assertEquals(listOf(anime), interactor.getWatchLaterItems().getOrThrow())
        assertEquals(listOf(anime), interactor.getGenericBookmarkItems().getOrThrow())
        assertEquals(listOf(collection), interactor.getCollections().getOrThrow())
    }

    @Test
    fun getGenericBookmarkItems_skipsWatchLaterFolder() = runTest {
        val watchLaterFolder = Bookmark(id = 1, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        val genericFolder = Bookmark(id = 2, title = "Favorites")
        val item = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(watchLaterFolder, genericFolder))
        coEvery { api.getBookmarkItems(genericFolder.id, null) } returns Result.success(
            PaginatedResponse(
                items = listOf(item),
                pagination = Pagination(current = 1, perpage = 20, total = 1),
            )
        )

        val result = interactor.getGenericBookmarkItems()

        assertEquals(listOf(item), result.getOrThrow())
        coVerify(exactly = 0) { api.getBookmarkItems(watchLaterFolder.id, any()) }
        coVerify(exactly = 1) { api.getBookmarkItems(genericFolder.id, null) }
    }

    @Test
    fun getGenericBookmarkItems_returnsEmptyList_whenOnlyWatchLaterFolderExists() = runTest {
        val watchLaterFolder = Bookmark(id = 1, title = WatchLaterBookmarkInteractor.FOLDER_TITLE)
        coEvery { api.getBookmarks() } returns Result.success(listOf(watchLaterFolder))

        val result = interactor.getGenericBookmarkItems()

        assertEquals(emptyList<Item>(), result.getOrThrow())
        coVerify(exactly = 0) { api.getBookmarkItems(any(), any()) }
    }

    private fun page(
        vararg items: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = current, perpage = items.size, total = total),
    )

    private fun item(
        id: Int,
        genreIds: IntArray = intArrayOf(),
    ) = Item(
        id = id,
        title = "Item $id",
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
