package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.WatchingToggleResponse
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.TmdbCastRepository
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor

internal class DetailsInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val bookmarkFolderInteractor: BookmarkFolderInteractor,
    private val tmdbCastRepository: TmdbCastRepository,
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

    suspend fun getTmdbCast(imdbId: String): List<TmdbCastMember> {
        return tmdbCastRepository.getCast(imdbId)
    }

    suspend fun isInWatchLaterFolder(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        return bookmarkFolderInteractor.isInQuickFolder(item.id)
    }

    suspend fun isBookmarked(item: Item, mode: BookmarkMode): Boolean {
        return when {
            mode == BookmarkMode.Simple && item.type.isSeriesLike() -> false
            mode == BookmarkMode.Simple -> bookmarkFolderInteractor.isInQuickFolder(item.id)
            else -> bookmarkFolderInteractor.getItemFolders(item.id).isNotEmpty()
        }
    }

    suspend fun setMovieBookmarked(id: Int, bookmarked: Boolean): MovieBookmarkUpdate {
        val update = bookmarkFolderInteractor.setQuickSaved(id, bookmarked)
        return MovieBookmarkUpdate(
            isBookmarked = update.isSaved,
            folderTitle = update.folder?.title,
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

    private fun WatchingToggleResponse.confirmedWatchedOr(requested: Boolean): Boolean {
        return when {
            watched != null -> watched == WATCHED_STATUS
            watching?.status != null -> watching.status == WATCHED_STATUS
            else -> requested
        }
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
