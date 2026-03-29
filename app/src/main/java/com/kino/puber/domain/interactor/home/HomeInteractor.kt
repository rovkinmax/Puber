package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection

class HomeInteractor(private val api: KinoPubApiClient) {

    suspend fun getHotItems(limit: Int = 10): Result<List<Item>> {
        return api.getItemsByShortcut("hot").map { it.items.take(limit) }
    }

    suspend fun getContinueWatching(): Result<List<History>> {
        return api.getHistoryData(page = 1).map { it.items }
    }

    suspend fun getFreshItems(): Result<List<Item>> {
        return api.getItemsByShortcut("fresh").map { it.items }
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

    suspend fun getCollections(): Result<List<KCollection>> {
        return api.getCollections(page = 1).map { it.items }
    }
}
