package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor

class HomeInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
) {

    suspend fun getHotItems(type: String = "movie", limit: Int = 10): Result<List<Item>> {
        return api.getItemsByShortcut("hot", type = type).map { it.items.take(limit) }
    }

    suspend fun getWatchingItems(): Result<List<Item>> {
        return api.getWatchingList(onlySubscribed = true).map { it.items.orEmpty() }
    }

    suspend fun getFreshItems(type: String): Result<List<Item>> {
        return api.getItemsByShortcut("fresh", type = type).map { it.items }
    }

    suspend fun getPopularByType(type: String): Result<List<Item>> {
        return api.getItemsByShortcut("popular", type = type).map { it.items }
    }

    suspend fun getBookmarkFolders(): Result<List<Bookmark>> {
        return api.getBookmarks()
    }

    suspend fun getBookmarkItems(folderId: Int): Result<List<Item>> {
        return api.getBookmarkItems(folderId).map { it.items }
    }

    suspend fun getGenericBookmarkItems(): Result<List<Item>> {
        return api.getBookmarks().mapCatching { folders ->
            val folder = folders.firstOrNull { it.title != WatchLaterBookmarkInteractor.FOLDER_TITLE }
                ?: return@mapCatching emptyList()
            api.getBookmarkItems(folder.id).getOrThrow().items
        }
    }

    suspend fun getWatchLaterItems(): Result<List<Item>> {
        return watchLaterBookmarkInteractor.getItems()
    }

    suspend fun getCollections(): Result<List<KCollection>> {
        return api.getCollections(page = 1).map { it.items }
    }
}
