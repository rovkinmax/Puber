package com.kino.puber.domain.interactor.search

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item

internal class SearchInteractor(
    private val api: KinoPubApiClient,
) {

    suspend fun search(query: String): List<Item> {
        return api.searchItems(query, perpage = SEARCH_PER_PAGE).getOrThrow().items
    }

    private companion object {
        const val SEARCH_PER_PAGE = 50
    }
}
