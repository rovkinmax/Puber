package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper

internal class DetailsVM(
    router: AppRouter,
    private val params: DetailsScreenParams,
    private val mapper: DetailsScreenUIMapper,
    private val interactor: DetailsInteractor,
) : PuberVM<DetailsScreenState>(router) {

    override val initialViewState = DetailsScreenState.Loading

    override fun onStart() {
        loadData()
    }

    private fun loadData() {
        launch {
            val item = interactor.getItemDetails(params.itemId)
            updateViewState(mapper.map(item))
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DetailsAction.PlayClicked -> { /* TODO: navigate to player */ }
            is DetailsAction.TrailerClicked -> { /* TODO: open trailer */ }
            is DetailsAction.SelectSeasonClicked -> { /* TODO: open season picker */ }
            is DetailsAction.WatchlistToggleClicked -> onWatchlistToggle()
            is CommonAction.RetryClicked -> loadData()
            else -> super.onAction(action)
        }
    }

    private fun onWatchlistToggle() {
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = !isInWatchlist)
            // todo add implementation for backend
        }
    }
}