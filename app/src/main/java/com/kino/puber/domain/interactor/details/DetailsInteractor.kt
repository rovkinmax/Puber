package com.kino.puber.domain.interactor.details

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.repository.ItemDetailsRepository

internal class DetailsInteractor(
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun toggleWatchlist(id: Int): Item {
        // TODO: call api.toggleWatchlist(id) before refresh
        return itemDetailsRepository.refresh(id)
    }
}