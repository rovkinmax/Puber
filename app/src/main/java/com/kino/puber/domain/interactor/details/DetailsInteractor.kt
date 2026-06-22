package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
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

    suspend fun toggleWatchlist(id: Int): Item {
        api.toggleWatchlist(id).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    suspend fun isInWatchLaterFolder(item: Item): Boolean {
        if (item.type.isSeriesLike()) return item.inWatchlist ?: false
        if (item.bookmarks.orEmpty().any { bookmark -> bookmark.title == WatchLaterBookmarkInteractor.FOLDER_TITLE }) {
            return true
        }
        return watchLaterBookmarkInteractor.isBookmarked(item.id).getOrThrow()
    }

    suspend fun setWatchLater(id: Int, inWatchLater: Boolean): Item {
        if (inWatchLater) {
            watchLaterBookmarkInteractor.add(id).getOrThrow()
        } else {
            watchLaterBookmarkInteractor.remove(id).getOrThrow()
        }
        return itemDetailsRepository.refresh(id)
    }

    suspend fun setMovieWatched(id: Int, watched: Boolean): Item {
        api.toggleWatchingStatus(id = id, status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    private companion object {
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }
}
