package com.kino.puber.domain.interactor.history

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.ItemDetailsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class HistoryInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true)
    private val interactor = HistoryInteractor(api, itemDetailsRepository)

    @Test
    fun getPage_returnsVerifiedPaginatedResponse() = runTest {
        val expected = PaginatedResponse(
            items = listOf(movie(recordId = 1, videoId = 11, videoNumber = 2)),
            pagination = Pagination(current = 2, perpage = 20, total = 3, totalItems = 41),
        )
        coEvery { api.getHistoryData(page = 2) } returns Result.success(expected)

        assertEquals(expected, interactor.getPage(page = 2))
    }

    @Test
    fun clearExactMediaHistory_invalidatesExactItemOnlyAfterSuccessfulMutation() = runTest {
        val history = movie(recordId = 1, videoId = 73001, videoNumber = 2)
        coEvery { api.clearExactMediaHistory(73001) } returns Result.success(Unit)

        interactor.clearExactMediaHistory(
            mediaId = requireNotNull(history.video).id,
            itemId = 72001,
        )

        coVerifyOrder {
            api.clearExactMediaHistory(73001)
            itemDetailsRepository.invalidate(72001)
        }
        verify(exactly = 1) { itemDetailsRepository.invalidate(72001) }
    }

    @Test
    fun clearExactMediaHistory_doesNotInvalidateItemWhenMutationFails() = runTest {
        coEvery { api.clearExactMediaHistory(73001) } returns
            Result.failure(IOException("delete failure"))

        val failure = runCatching {
            interactor.clearExactMediaHistory(mediaId = 73001, itemId = 72001)
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        coVerify(exactly = 1) { api.clearExactMediaHistory(73001) }
        verify(exactly = 0) { itemDetailsRepository.invalidate(any()) }
    }

    @Test
    fun invalidateItemDetails_invalidatesSelectedItemCacheEntry() {
        interactor.invalidateItemDetails(itemId = 72001)

        verify(exactly = 1) { itemDetailsRepository.invalidate(72001) }
    }

    @Test
    fun filterFirstOccurrences_preservesFirstOccurrenceWithinAndAcrossPages() {
        val traversal = HistoryTraversal()
        val firstMovie = movie(recordId = 1, videoId = 11, videoNumber = 2)
        val duplicateMovie = movie(recordId = 2, videoId = 12, videoNumber = 2)
        val firstEpisode = episode(recordId = 3, videoId = 13, season = 1, episode = 4)
        val secondEpisode = episode(recordId = 4, videoId = 14, season = 1, episode = 5)

        val firstPage = traversal.filterFirstOccurrences(
            listOf(firstMovie, duplicateMovie, firstEpisode, secondEpisode),
        )
        val laterPage = traversal.filterFirstOccurrences(
            listOf(
                movie(recordId = 5, videoId = 15, videoNumber = 2),
                episode(recordId = 6, videoId = 16, season = 1, episode = 4),
                movie(recordId = 7, videoId = 17, videoNumber = 3),
            ),
        )

        assertEquals(listOf(firstMovie, firstEpisode, secondEpisode), firstPage)
        assertEquals(listOf(movie(recordId = 7, videoId = 17, videoNumber = 3)), laterPage)
    }

    @Test
    fun newTraversal_allowsAuthoritativePageOneToRebuildIdentity() {
        val history = movie(recordId = 1, videoId = 11, videoNumber = 2)
        val firstTraversal = HistoryTraversal()
        assertEquals(listOf(history), firstTraversal.filterFirstOccurrences(listOf(history)))
        assertEquals(emptyList<History>(), firstTraversal.filterFirstOccurrences(listOf(history)))

        val refreshedTraversal = HistoryTraversal()

        assertEquals(listOf(history), refreshedTraversal.filterFirstOccurrences(listOf(history)))
    }

    @Test
    fun seededTraversal_rebuildsSeenKeysFromStableContent() {
        val seededMovie = movie(recordId = 1, videoId = 11, videoNumber = 2)
        val seededEpisode = episode(recordId = 2, videoId = 12, season = 2, episode = 3)
        val traversal = HistoryTraversal(listOf(seededMovie, seededEpisode))

        val filtered = traversal.filterFirstOccurrences(
            listOf(
                movie(recordId = 3, videoId = 13, videoNumber = 2),
                episode(recordId = 4, videoId = 14, season = 2, episode = 3),
                episode(recordId = 5, videoId = 15, season = 2, episode = 4),
            ),
        )

        assertEquals(listOf(episode(recordId = 5, videoId = 15, season = 2, episode = 4)), filtered)
    }

    @Test
    fun filterFirstOccurrences_keepsIncompleteSeriesByDeletionMediaIdentityForDetailsFallback() {
        val traversal = HistoryTraversal()
        val first = episode(recordId = null, videoId = 11, season = null, episode = 3)
        val second = episode(recordId = null, videoId = 12, season = null, episode = 3)

        assertEquals(listOf(first, second), traversal.filterFirstOccurrences(listOf(first, second)))
        assertEquals(null, first.semanticKeyOrNull())
        assertEquals(HistoryRowKey.DeletionMedia(mediaId = 11), first.rowKeyOrNull())
    }

    @Test
    fun filterFirstOccurrences_deduplicatesFallbackRowsByDeletionMediaIdAcrossPages() {
        val traversal = HistoryTraversal()
        val first = episode(recordId = null, videoId = 11, season = null, episode = 3)
        val duplicate = episode(recordId = null, videoId = 11, season = null, episode = 4)

        assertEquals(listOf(first), traversal.filterFirstOccurrences(listOf(first, duplicate)))
        assertEquals(emptyList<History>(), traversal.filterFirstOccurrences(listOf(duplicate)))
    }

    @Test
    fun filterFirstOccurrences_filtersUnsupportedOrUnusableRows() {
        val traversal = HistoryTraversal()
        val unsupported = movie(
            recordId = 1,
            videoId = 11,
            videoNumber = 2,
            type = ItemType.UNKNOWN_VALUE,
        )
        val missingMovieNumber = movie(recordId = 2, videoId = 12, videoNumber = null)
        val missingMedia = movie(recordId = 3, videoId = null, videoNumber = null)

        assertEquals(
            emptyList<History>(),
            traversal.filterFirstOccurrences(listOf(unsupported, missingMovieNumber, missingMedia)),
        )
    }

    private fun movie(
        recordId: Int?,
        videoId: Int?,
        videoNumber: Int?,
        type: ItemType = ItemType.MOVIE,
    ): History {
        return history(
            recordId = recordId,
            videoId = videoId,
            videoNumber = videoNumber,
            type = type,
        )
    }

    private fun episode(
        recordId: Int?,
        videoId: Int,
        season: Int?,
        episode: Int?,
    ): History {
        return history(
            recordId = recordId,
            videoId = videoId,
            videoNumber = episode,
            type = ItemType.SERIAL,
            season = season,
        )
    }

    private fun history(
        recordId: Int?,
        videoId: Int?,
        videoNumber: Int?,
        type: ItemType,
        season: Int? = null,
    ): History {
        return History(
            recordId = recordId,
            item = Item(id = 100, title = "Synthetic", type = type),
            video = videoId?.let { Video(id = it, number = videoNumber) },
            season = season,
        )
    }
}
