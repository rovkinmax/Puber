package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient

class SavedItemInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
) {

    suspend fun setSaved(itemId: Int, isSeriesLike: Boolean, saved: Boolean): Result<Boolean> {
        return if (isSeriesLike) {
            setSeriesSaved(itemId, saved)
        } else if (saved) {
            watchLaterBookmarkInteractor.add(itemId).map { true }
        } else {
            removeFromAnyBookmark(itemId)
        }
    }

    private suspend fun setSeriesSaved(itemId: Int, saved: Boolean): Result<Boolean> {
        return api.getItemDetails(itemId).mapCatching { response ->
            val current = response.item?.inWatchlist
            if (current == saved) {
                saved
            } else {
                api.toggleWatchlist(itemId).getOrThrow().watching
            }
        }
    }

    private suspend fun removeFromAnyBookmark(itemId: Int): Result<Boolean> {
        return api.getItemBookmarkFolders(itemId).mapCatching { folders ->
            val folder = folders.firstOrNull() ?: return@mapCatching false
            api.removeBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
            api.getItemBookmarkFolders(itemId).getOrThrow().isNotEmpty()
        }
    }
}
