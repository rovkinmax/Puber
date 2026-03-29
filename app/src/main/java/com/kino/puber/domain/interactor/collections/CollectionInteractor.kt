package com.kino.puber.domain.interactor.collections

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse

class CollectionInteractor(private val api: KinoPubApiClient) {

    suspend fun getCollections(page: Int): Result<PaginatedResponse<KCollection>> {
        return api.getCollections(page = page)
    }

    suspend fun getCollectionItems(id: Int): Result<PaginatedResponse<Item>> {
        return api.getCollectionItems(id)
    }
}
