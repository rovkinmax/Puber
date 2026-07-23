package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.WatchingToggleResponse
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import kotlinx.coroutines.CancellationException

internal class DetailsInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun refreshItemDetails(id: Int): Item {
        return itemDetailsRepository.refresh(id)
    }

    suspend fun getSimilarItems(id: Int): List<Item> {
        return api.getSimilarItems(id).getOrThrow().items.orEmpty()
    }

    suspend fun isInWatchLaterFolder(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        if (item.bookmarks.orEmpty().isNotEmpty()) {
            return true
        }
        return getMovieBookmarkFolders(item.id).isNotEmpty()
    }

    suspend fun setMovieBookmarked(id: Int, bookmarked: Boolean): MovieBookmarkUpdate {
        if (bookmarked) {
            val folder = watchLaterBookmarkInteractor.add(id).getOrThrow().let { bookmark ->
                BookmarkFolder(id = bookmark.id, title = bookmark.title, count = bookmark.count ?: 0)
            }
            itemDetailsRepository.invalidate(id)
            return MovieBookmarkUpdate(
                isBookmarked = true,
                folderTitle = folder.title,
            )
        }

        val folders = getMovieBookmarkFolders(id)
        val folder = folders.firstOrNull()
        if (folder != null) {
            api.removeBookmarkItem(itemId = id, folderId = folder.id).getOrThrow()
            itemDetailsRepository.invalidate(id)
        }
        val remainingFolders = if (folder != null) {
            readRemainingBookmarkFolders(id, folders.drop(1))
        } else {
            emptyList()
        }
        return MovieBookmarkUpdate(
            isBookmarked = remainingFolders.isNotEmpty(),
            folderTitle = folder?.title,
        )
    }

    suspend fun setMovieWatched(id: Int, watched: Boolean): MovieWatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
        ).getOrThrow()
        itemDetailsRepository.invalidate(id)
        return MovieWatchedUpdate(isWatched = response.confirmedWatchedOr(watched))
    }

    suspend fun setEpisodeWatched(id: Int, season: Int, episode: Int, watched: Boolean): WatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
            video = episode,
        ).getOrThrow()
        itemDetailsRepository.invalidate(id)
        return WatchedUpdate(isWatched = response.confirmedWatchedOr(watched))
    }

    suspend fun setSeasonWatched(id: Int, season: Int, watched: Boolean): WatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
        ).getOrThrow()
        itemDetailsRepository.invalidate(id)
        return WatchedUpdate(isWatched = response.confirmedWatchedOr(watched))
    }

    private suspend fun readRemainingBookmarkFolders(
        id: Int,
        knownRemaining: List<BookmarkFolder>,
    ): List<BookmarkFolder> {
        return try {
            getMovieBookmarkFolders(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            knownRemaining
        }
    }

    private fun WatchingToggleResponse.confirmedWatchedOr(requested: Boolean): Boolean {
        return when {
            watched != null -> watched == WATCHED_STATUS
            watching?.status != null -> watching.status == WATCHED_STATUS
            else -> requested
        }
    }

    private suspend fun getMovieBookmarkFolders(id: Int): List<BookmarkFolder> {
        return api.getItemBookmarkFolders(id).getOrThrow()
    }

    private companion object {
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }
}

internal data class MovieWatchedUpdate(
    val isWatched: Boolean,
)

internal data class MovieBookmarkUpdate(
    val isBookmarked: Boolean,
    val folderTitle: String?,
)

internal data class WatchedUpdate(
    val isWatched: Boolean,
)
