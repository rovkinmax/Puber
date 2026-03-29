package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection

class HomeInteractor(private val api: KinoPubApiClient) {

    suspend fun getHotItems(limit: Int = 10): List<Item> {
        return api.getItemsByShortcut("hot").getOrThrow().items.take(limit)
    }

    suspend fun getWatchingItems(): List<Item> {
        return api.getWatchingList(onlySubscribed = true).getOrThrow().items.orEmpty()
    }

    suspend fun getFreshItems(): List<Item> {
        return api.getItemsByShortcut("fresh").getOrThrow().items
    }

    suspend fun getPopularByType(type: String): List<Item> {
        return api.getItemsByShortcut("popular", type = type).getOrThrow().items
    }

    suspend fun getBookmarkFolders(): List<Bookmark> {
        return api.getBookmarks().getOrThrow()
    }

    suspend fun getBookmarkItems(folderId: Int): List<Item> {
        return api.getBookmarkItems(folderId).getOrThrow().items
    }

    suspend fun getCollections(): List<KCollection> {
        return api.getCollections(page = 1).getOrThrow().items
    }
}
