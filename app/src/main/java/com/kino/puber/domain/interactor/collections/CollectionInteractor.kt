package com.kino.puber.domain.interactor.collections

import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import kotlin.time.Duration.Companion.minutes

class CollectionInteractor(private val api: KinoPubApiClient) {

    suspend fun getCollections(page: Int): PaginatedResponse<KCollection> {
        if (page == 1) {
            return firstPageCache.getOrPut(FIRST_PAGE_KEY) {
                api.getCollections(page = page).getOrThrow()
            }
        }
        return api.getCollections(page = page).getOrThrow()
    }

    suspend fun getCollectionItems(id: Int): List<Item> {
        return api.getCollectionItems(id).getOrThrow().items
    }

    companion object {
        private const val FIRST_PAGE_KEY = "collections_p1"
        private val firstPageCache = TypedTtlCacheImpl<String, PaginatedResponse<KCollection>>(
            defaultTtl = 3.minutes,
        )
    }
}
