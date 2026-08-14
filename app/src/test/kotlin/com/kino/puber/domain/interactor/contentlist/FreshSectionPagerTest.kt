package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreshSectionPagerTest {

    private val api = mockk<KinoPubApiClient>()

    @Test
    fun loadPage_filtersDeduplicatesAndAlternatesWhilePreservingSourceOrder() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie1 = item(1, "Movie 1", ItemType.MOVIE, CARTOON_GENRE_ID)
        val movie2 = item(2, "Movie 2", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial1 = item(3, "Serial 1", ItemType.SERIAL, CARTOON_GENRE_ID)
        val serial2 = item(4, "Serial 2", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(
            page(
                movie1,
                item(10, "Anime movie", ItemType.MOVIE, CARTOON_GENRE_ID, ANIME_GENRE_ID),
                movie2,
                perpage = 4,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(
            page(
                serial1,
                movie1.copy(type = ItemType.SERIAL, title = "Duplicate"),
                item(11, "Wrong genre", ItemType.SERIAL),
                serial2,
                perpage = 4,
            )
        )

        val result = pager.loadPage(1)

        assertEquals(listOf(movie1, serial1, movie2, serial2), result.items)
        assertEquals(Pagination(current = 1, perpage = 4, total = 1), result.pagination)
        verifyFreshPageCalls(moviePage = 1, serialPage = 1)
    }

    @Test
    fun loadPage_pagesSourcesIndependentlyAndStopsCallingExhaustedSource() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie = item(1, "Movie", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial1 = item(2, "Serial 1", ItemType.SERIAL, CARTOON_GENRE_ID)
        val serial2 = item(3, "Serial 2", ItemType.SERIAL, CARTOON_GENRE_ID)
        val serial3 = item(4, "Serial 3", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(movie, total = 1, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(serial1, current = 1, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        } returns Result.success(page(serial2, serial3, current = 2, total = 2, perpage = 2))

        val first = pager.loadPage(1)
        val second = pager.loadPage(2)

        assertEquals(listOf(movie, serial1), first.items)
        assertEquals(Pagination(current = 1, perpage = 2, total = 2), first.pagination)
        assertEquals(listOf(serial2, serial3), second.items)
        assertEquals(Pagination(current = 2, perpage = 2, total = 2), second.pagination)
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        }
    }

    @Test
    fun loadPage_continuesMovieAfterSerialIsExhausted() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie1 = item(1, "Movie 1", ItemType.MOVIE, CARTOON_GENRE_ID)
        val movie2 = item(2, "Movie 2", ItemType.MOVIE, CARTOON_GENRE_ID)
        val movie3 = item(3, "Movie 3", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial = item(4, "Serial", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(movie1, current = 1, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        } returns Result.success(page(movie2, movie3, current = 2, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(serial, total = 1, perpage = 2))

        val first = pager.loadPage(1)
        val second = pager.loadPage(2)

        assertEquals(listOf(movie1, serial), first.items)
        assertEquals(listOf(movie2, movie3), second.items)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        }
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        }
    }

    @Test
    fun loadPage_refillsFilteredEmptyPagesUntilLogicalPageIsFull() = runTest {
        val pager = FreshSectionPager(api, animeConfig())
        val animeMovie = item(3, "Anime movie", ItemType.MOVIE, ANIME_GENRE_ID)
        val animeSerial = item(4, "Anime serial", ItemType.SERIAL, ANIME_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(
            page(
                item(1, "Cartoon", ItemType.MOVIE, CARTOON_GENRE_ID),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(
            page(
                item(2, "Unknown", ItemType.SERIAL),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        } returns Result.success(page(animeMovie, current = 2, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        } returns Result.success(page(animeSerial, current = 2, total = 2, perpage = 2))

        val result = pager.loadPage(1)

        assertEquals(listOf(animeMovie, animeSerial), result.items)
        assertEquals(Pagination(current = 1, perpage = 2, total = 1), result.pagination)
        verifyFreshPageCalls(moviePage = 2, serialPage = 2)
    }

    @Test
    fun loadPage_movieFailureIsAtomicAndRetryMatchesCleanAttempt() = runTest {
        assertFailureAtomicRetry(failingType = ItemType.MOVIE)
    }

    @Test
    fun loadPage_serialFailureIsAtomicAndRetryMatchesCleanAttempt() = runTest {
        assertFailureAtomicRetry(failingType = ItemType.SERIAL)
    }

    @Test
    fun loadPage_invalidPaginationIsAtomicAndRetryRefetchesBothSources() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie = item(1, "Movie", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial = item(2, "Serial", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(movie, current = 0, total = 1, perpage = 2)) andThen
            Result.success(page(movie, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(serial, perpage = 2))

        val error = runCatching { pager.loadPage(1) }.exceptionOrNull()
        val retry = pager.loadPage(1)

        assertEquals(
            "Fresh movie pagination current 0 did not match requested page 1",
            error?.message,
        )
        assertEquals(listOf(movie, serial), retry.items)
        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
    }

    @Test
    fun loadPage_cancellationIsAtomicAndRetryRefetchesBothSources() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie = item(1, "Movie", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial = item(2, "Serial", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(movie, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } throws CancellationException("cancelled") andThen
            Result.success(page(serial, perpage = 2))

        val cancellation = try {
            pager.loadPage(1)
            null
        } catch (error: CancellationException) {
            error
        }
        val retry = pager.loadPage(1)

        assertEquals("cancelled", cancellation?.message)
        assertEquals(listOf(movie, serial), retry.items)
        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
    }

    @Test
    fun pageOneAndReset_restartCursorsSeenIdsAndMergeTurn() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie1 = item(1, "Movie 1", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial1 = item(2, "Serial 1", ItemType.SERIAL, CARTOON_GENRE_ID)
        val movie2 = item(3, "Movie 2", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial2 = item(4, "Serial 2", ItemType.SERIAL, CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(movie1, perpage = 2)) andThen
            Result.success(page(movie2, perpage = 2)) andThen
            Result.success(page(movie1, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(serial1, perpage = 2)) andThen
            Result.success(page(serial2, perpage = 2)) andThen
            Result.success(page(serial1, perpage = 2))

        assertEquals(listOf(movie1, serial1), pager.loadPage(1).items)
        assertEquals(listOf(movie2, serial2), pager.loadPage(1).items)
        pager.reset()
        assertEquals(listOf(movie1, serial1), pager.loadPage(1).items)

        coVerify(exactly = 3) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 3) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
    }

    @Test
    fun concurrentLogicalPages_areSerializedAgainstCommittedState() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())
        val movie1 = item(1, "Movie 1", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial1 = item(2, "Serial 1", ItemType.SERIAL, CARTOON_GENRE_ID)
        val movie2 = item(3, "Movie 2", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial2 = item(4, "Serial 2", ItemType.SERIAL, CARTOON_GENRE_ID)
        val movieStarted = CompletableDeferred<Unit>()
        val releaseMovie = CompletableDeferred<Unit>()
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } coAnswers {
            movieStarted.complete(Unit)
            releaseMovie.await()
            Result.success(page(movie1, current = 1, total = 2, perpage = 2))
        }
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(serial1, current = 1, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        } returns Result.success(page(movie2, current = 2, total = 2, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        } returns Result.success(page(serial2, current = 2, total = 2, perpage = 2))

        val firstPage = async { pager.loadPage(1) }
        movieStarted.await()
        val secondPage = async { pager.loadPage(2) }
        releaseMovie.complete(Unit)

        assertEquals(listOf(movie1, serial1), firstPage.await().items)
        assertEquals(listOf(movie2, serial2), secondPage.await().items)
        verifyFreshPageCalls(moviePage = 1, serialPage = 1)
        verifyFreshPageCalls(moviePage = 2, serialPage = 2)
    }

    @Test
    fun loadPage_rejectsSkippedLogicalPageWithoutMutatingState() = runTest {
        val pager = FreshSectionPager(api, cartoonConfig())

        val error = runCatching { pager.loadPage(2) }.exceptionOrNull()

        assertEquals("Fresh logical page 2 did not match expected page 1", error?.message)
        coVerify(exactly = 0) {
            api.getItemsByShortcut(any(), any(), any(), any())
        }
    }

    private suspend fun assertFailureAtomicRetry(failingType: ItemType) {
        val failedApi = mockk<KinoPubApiClient>()
        val failedPager = FreshSectionPager(failedApi, cartoonConfig())
        val cleanApi = mockk<KinoPubApiClient>()
        val cleanPager = FreshSectionPager(cleanApi, cartoonConfig())
        val movie1 = item(1, "Movie 1", ItemType.MOVIE, CARTOON_GENRE_ID)
        val movie2 = item(2, "Movie 2", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial1 = item(3, "Serial 1", ItemType.SERIAL, CARTOON_GENRE_ID)
        val serial2 = item(4, "Serial 2", ItemType.SERIAL, CARTOON_GENRE_ID)
        val movie3 = item(5, "Movie 3", ItemType.MOVIE, CARTOON_GENRE_ID)
        val serial3 = item(6, "Serial 3", ItemType.SERIAL, CARTOON_GENRE_ID)
        ItemType.entries
            .filter { it == ItemType.MOVIE || it == ItemType.SERIAL }
            .forEach { type ->
                val firstPage = Result.success(
                    page(
                        if (type == ItemType.MOVIE) movie1 else serial1,
                        if (type == ItemType.MOVIE) movie2 else serial2,
                        current = 1,
                        total = 2,
                        perpage = 3,
                    )
                )
                val secondPage = Result.success(
                    page(
                        if (type == ItemType.MOVIE) movie3 else serial3,
                        current = 2,
                        total = 2,
                        perpage = 3,
                    )
                )
                coEvery {
                    failedApi.getItemsByShortcut("fresh", type.value, 1, null)
                } returns firstPage
                coEvery {
                    failedApi.getItemsByShortcut("fresh", type.value, 2, null)
                } returns if (type == failingType) {
                    Result.failure(IllegalStateException("${type.value} failed"))
                } else {
                    secondPage
                } andThen secondPage
                coEvery {
                    cleanApi.getItemsByShortcut("fresh", type.value, 1, null)
                } returns firstPage
                coEvery {
                    cleanApi.getItemsByShortcut("fresh", type.value, 2, null)
                } returns secondPage
            }

        val failedFirstPage = failedPager.loadPage(1)
        val cleanFirstPage = cleanPager.loadPage(1)
        val failure = runCatching { failedPager.loadPage(2) }.exceptionOrNull()
        val retry = failedPager.loadPage(2)
        val clean = cleanPager.loadPage(2)

        assertEquals(cleanFirstPage.items.map(Item::id), failedFirstPage.items.map(Item::id))
        assertEquals("${failingType.value} failed", failure?.message)
        assertEquals(clean.items.map(Item::id), retry.items.map(Item::id))
        assertEquals(clean.pagination, retry.pagination)
        coVerify(exactly = 1) {
            failedApi.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 1) {
            failedApi.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
        coVerify(exactly = 2) {
            failedApi.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        }
        coVerify(exactly = 2) {
            failedApi.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        }
    }

    private fun cartoonConfig() = SectionConfig(
        id = "fresh_cartoon",
        title = "Новинки",
        shortcut = "fresh",
        shortcutTypes = listOf(ItemType.MOVIE, ItemType.SERIAL),
        requiredGenreId = CARTOON_GENRE_ID,
        animeFilterMode = AnimeFilterMode.Exclude,
    )

    private fun animeConfig() = SectionConfig(
        id = "fresh_anime",
        title = "Новинки",
        shortcut = "fresh",
        shortcutTypes = listOf(ItemType.MOVIE, ItemType.SERIAL),
        requiredGenreId = ANIME_GENRE_ID,
        animeFilterMode = AnimeFilterMode.Only,
    )

    private fun verifyFreshPageCalls(moviePage: Int, serialPage: Int) {
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, moviePage, null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, serialPage, null)
        }
    }

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
        type: ItemType,
        vararg genreIds: Int,
    ) = Item(
        id = id,
        title = title,
        type = type,
        genres = genreIds
            .map { genreId -> Genre(id = genreId, title = "Genre $genreId") }
            .takeIf(List<Genre>::isNotEmpty),
    )
}
