package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.repository.ItemDetailsRepository

internal class DetailsInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun toggleWatchlist(id: Int): Item {
        api.toggleWatchlist(id).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    suspend fun isInWatchLaterFolder(item: Item): Boolean {
        return item.bookmarks.orEmpty().any(::isWatchLaterBookmark) ||
            api.getItemBookmarkFolders(item.id).getOrThrow().any(::isWatchLaterBookmark)
    }

    suspend fun setWatchLater(id: Int, inWatchLater: Boolean): Item {
        val folderId = watchLaterFolderId()
        if (inWatchLater) {
            api.addBookmarkItem(itemId = id, folderId = folderId).getOrThrow()
        } else {
            api.removeBookmarkItem(itemId = id, folderId = folderId).getOrThrow()
        }
        return itemDetailsRepository.refresh(id)
    }

    suspend fun setMovieWatched(id: Int, watched: Boolean): Item {
        api.toggleWatchingStatus(id = id, status = if (watched) WATCHED_STATUS else UNWATCHED_STATUS).getOrThrow()
        return itemDetailsRepository.refresh(id)
    }

    private suspend fun watchLaterFolderId(): Int {
        val existing = api.getBookmarks()
            .getOrThrow()
            .firstOrNull(::isWatchLaterBookmark)
        return existing?.id ?: api.createBookmark(WATCH_LATER_FOLDER_TITLE).getOrThrow().id
    }

    private fun isWatchLaterBookmark(bookmark: Bookmark): Boolean {
        return bookmark.title == WATCH_LATER_FOLDER_TITLE
    }

    private fun isWatchLaterBookmark(folder: BookmarkFolder): Boolean {
        return folder.title == WATCH_LATER_FOLDER_TITLE
    }

    private companion object {
        const val WATCH_LATER_FOLDER_TITLE = "Буду смотреть"
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }
}
