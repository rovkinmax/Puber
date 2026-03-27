package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.repository.ItemDetailsRepository

internal class FavoritesInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun getWatchlist(): List<Item> {
        val result = api.getWatchingList(onlySubscribed = true)
        return result.getOrThrow().items.orEmpty()
    }

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }
}