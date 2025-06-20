package com.kino.puber.ui.feature.favorites.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
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
            updateViewState(FavoriteViewState.Content(favoriteItemUIMapper.mapList(items)))
        }
    }
}