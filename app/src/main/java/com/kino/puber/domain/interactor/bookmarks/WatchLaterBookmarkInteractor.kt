package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item

class WatchLaterBookmarkInteractor(
    private val api: KinoPubApiClient,
) {

    suspend fun getItems(): Result<List<Item>> {
        return api.getBookmarks().mapCatching { folders ->
            val folder = folders.findWatchLaterFolder() ?: return@mapCatching emptyList()
            api.getBookmarkItems(folder.id).getOrThrow().items
        }
    }

    suspend fun isBookmarked(itemId: Int): Result<Boolean> {
        return api.getItemBookmarkFolders(itemId).map { folders ->
            folders.any { it.title == FOLDER_TITLE }
        }
    }

    suspend fun add(itemId: Int): Result<Unit> {
        return ensureFolder().mapCatching { folder ->
            api.addBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
        }
    }

    suspend fun remove(itemId: Int): Result<Unit> {
        return api.getBookmarks().mapCatching { folders ->
            val folder = folders.findWatchLaterFolder() ?: return@mapCatching
            api.removeBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
        }
    }

    private suspend fun ensureFolder(): Result<Bookmark> {
        return api.getBookmarks().mapCatching { folders ->
            folders.findWatchLaterFolder() ?: api.createBookmark(FOLDER_TITLE).getOrThrow()
        }
    }

    private fun List<Bookmark>.findWatchLaterFolder(): Bookmark? {
        return firstOrNull { it.title == FOLDER_TITLE }
    }

    companion object {
        const val FOLDER_TITLE = "Буду смотреть"
    }
}
