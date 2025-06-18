package com.kino.puber.domain.interactor.favorites

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item

internal class FavoritesInteractor(
    val api: KinoPubApiClient,
) {
    suspend fun getWatchlist(): List<Item> {
        val result = api.getWatchingList()
        return result.getOrThrow().items.orEmpty()
        /*return buildList {
            val list = result.getOrThrow().items.orEmpty()
            repeat(10) {
                addAll(list)
            }
        }*/
    }
}