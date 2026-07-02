package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor

internal class DetailsInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun getSimilarItems(id: Int): List<Item> {
        return api.getSimilarItems(id).getOrThrow().items.orEmpty()
    }

    suspend fun toggleWatchlist(id: Int): Item {
        api.toggleWatchlist(id).getOrThrow()
        return itemDetailsRepository.refresh(id)
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
            return MovieBookmarkUpdate(
                item = itemDetailsRepository.refresh(id),
                isBookmarked = true,
                folderTitle = folder.title,
            )
        }

        val folder = getMovieBookmarkFolders(id).firstOrNull()
        val remainingFolders = if (folder != null) {
            api.removeBookmarkItem(itemId = id, folderId = folder.id).getOrThrow()
            getMovieBookmarkFolders(id)
        } else {
            emptyList()
        }
        return MovieBookmarkUpdate(
            item = itemDetailsRepository.refresh(id),
            isBookmarked = remainingFolders.isNotEmpty(),
            folderTitle = folder?.title,
        )
    }

    suspend fun setMovieWatched(id: Int, watched: Boolean): MovieWatchedUpdate {
        val response = api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
        ).getOrThrow()
        return MovieWatchedUpdate(
            item = itemDetailsRepository.refresh(id),
            isWatched = when {
                response.watched != null -> response.watched == WATCHED_STATUS
                response.watching?.status != null -> response.watching.status == WATCHED_STATUS
                else -> watched
            },
        )
    }

    suspend fun setEpisodeWatched(id: Int, season: Int, episode: Int, watched: Boolean): Item {
        api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
            video = episode,
        ).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    suspend fun setSeasonWatched(id: Int, season: Int, watched: Boolean): Item {
        api.toggleWatchingStatus(
            id = id,
            status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS,
            season = season,
        ).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    private companion object {
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }

    private suspend fun getMovieBookmarkFolders(id: Int): List<BookmarkFolder> {
        return api.getItemBookmarkFolders(id).getOrThrow()
    }
}

internal data class MovieWatchedUpdate(
    val item: Item,
    val isWatched: Boolean,
)

internal data class MovieBookmarkUpdate(
    val item: Item,
    val isBookmarked: Boolean,
    val folderTitle: String?,
)
