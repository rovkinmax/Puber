package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item

internal class FavoritesInteractor(
    val api: KinoPubApiClient,
) {
    suspend fun getWatchlist(): List<Item> {
        val result = api.getWatchingList(onlySubscribed = false)
        return result.getOrThrow().items.orEmpty()
    }
}