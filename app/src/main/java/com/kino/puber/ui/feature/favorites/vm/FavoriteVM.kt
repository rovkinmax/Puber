package com.kino.puber.ui.feature.favorites.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.favorites.FavoritesInteractor
import com.kino.puber.ui.feature.favorites.model.FavoriteItemUIMapper
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState

internal class FavoriteVM(
    router: AppRouter,
    private val interactor: FavoritesInteractor,
    private val favoriteItemUIMapper: FavoriteItemUIMapper,
) : PuberVM<FavoriteViewState>(router) {
    override val initialViewState = FavoriteViewState.Loading

    override fun onStart() {
        loadData()
    }

    private fun loadData() {
        launch {
            val items = interactor.getWatchlist()
            val selectedItem = items.firstOrNull()?.let { item ->
                interactor.getItemDetails(item.id)
            }
            updateViewState(
                favoriteItemUIMapper.mapToState(
                    items = items,
                    selectedItem = selectedItem,
                )
            )
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
        }
    }

    private fun onItemSelected(state: VideoItemUIState) {

    }

    private fun onItemFocused(selectedItem: VideoItemUIState) {
        launch {
            val details = interactor.getItemDetails(selectedItem.id)
            updateViewState<FavoriteViewState.Content> {
                copy(selectedItem = favoriteItemUIMapper.mapDetailedItem(details))
            }
        }
    }
}