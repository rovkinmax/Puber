package com.kino.puber.domain.interactor.favorites

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item

internal class FavoritesInteractor(
    val api: KinoPubApiClient,
) {

    private val detailedItemsCache: TypedTtlCache<Int, Item> = TypedTtlCacheImpl()

    suspend fun getWatchlist(): List<Item> {
        val result = api.getWatchingList(onlySubscribed = false)
        return result.getOrThrow().items.orEmpty()
    }

    suspend fun getItemDetails(id: Int): Item {
        return detailedItemsCache.getOrPut(id) { api.getItemDetails(id).getOrThrow().item!! }
    }
}