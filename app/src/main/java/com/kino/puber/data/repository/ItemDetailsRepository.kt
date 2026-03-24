package com.kino.puber.data.repository

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import kotlin.time.Duration.Companion.minutes

class ItemDetailsRepository(private val api: KinoPubApiClient) {

    private val cache: TypedTtlCache<Int, Item> = TypedTtlCacheImpl(defaultTtl = 5.minutes)

    suspend fun getItemDetails(id: Int): Item {
        return cache.getOrPut(id) { api.getItemDetails(id).getOrThrow().item!! }
    }

    suspend fun refresh(id: Int): Item {
        cache.remove(id)
        return getItemDetails(id)
    }
}
