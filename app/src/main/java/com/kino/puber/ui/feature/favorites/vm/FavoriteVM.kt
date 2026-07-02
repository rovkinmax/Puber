package com.kino.puber.ui.feature.favorites.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.favorites.FavoritesInteractor
import com.kino.puber.ui.feature.favorites.model.FavoriteItemUIMapper
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState
import kotlinx.coroutines.Job

internal class FavoriteVM(
    router: AppRouter,
    private val interactor: FavoritesInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val favoriteItemUIMapper: FavoriteItemUIMapper,
) : PuberVM<FavoriteViewState>(router) {

    override val initialViewState = FavoriteViewState.Loading
    private var focusedItemJob: Job? = null

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
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            is CommonAction.RetryClicked -> loadData()
            else -> super.onAction(action)
        }
    }

    private fun onItemSelected(state: VideoItemUIState) {
        router.navigateTo(router.screens.details(itemId = state.id))
    }

    private fun onItemPlayed(state: VideoItemUIState) {
        router.navigateTo(router.screens.player(itemId = state.id))
    }

    private fun onItemFocused(selectedItem: VideoItemUIState) {
        focusedItemJob?.cancel()
        focusedItemJob = launch {
            updateViewState<FavoriteViewState.Content> {
                copy(selectedItem = VideoDetailsUIState.Loading)
            }

            val details = interactor.getItemDetails(selectedItem.id)

            updateViewState<FavoriteViewState.Content> {
                copy(selectedItem = favoriteItemUIMapper.mapDetailedItem(details))
            }
        }
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        launch {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).getOrThrow()
            loadData()
        }
    }
}
