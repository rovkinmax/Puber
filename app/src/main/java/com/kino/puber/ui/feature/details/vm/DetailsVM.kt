package com.kino.puber.ui.feature.details.vm

import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
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
    private val resources: ResourceProvider,
    override val errorHandler: ErrorHandler,
) : PuberVM<DetailsScreenState>(router) {

    override val initialViewState = DetailsScreenState.Loading

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is DetailsScreenState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(DetailsScreenState.Error(error.message))
        }
    }
    private var currentItem: Item? = null

    override fun onStart() {
        loadData()
    }

    private fun loadData() {
        launch {
            val item = interactor.getItemDetails(params.itemId)
            currentItem = item
            updateViewState(mapper.map(item))
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is DetailsAction.PlayClicked -> router.navigateTo(router.screens.player(params.itemId))
            is DetailsAction.TrailerClicked -> showTrailer()
            is DetailsAction.CloseTrailer -> hideTrailer()
            is DetailsAction.SelectSeasonClicked -> showSeasonsPanel()
            is DetailsAction.WatchlistToggleClicked -> onWatchlistToggle()
            is DetailsAction.EpisodeSelected -> onEpisodeSelected(action.item)
            is DetailsAction.CloseSeasonsPanel -> hideSeasonsPanel()
            is CommonAction.RetryClicked -> loadData()
            else -> super.onAction(action)
        }
    }

    private fun showSeasonsPanel() {
        updateViewState<DetailsScreenState.Content> {
            copy(seasonsPanelVisible = true)
        }
    }

    private fun hideSeasonsPanel() {
        updateViewState<DetailsScreenState.Content> {
            copy(seasonsPanelVisible = false)
        }
    }

    private fun onEpisodeSelected(episodeItem: VideoItemUIState) {
        val item = currentItem ?: return
        val seasons = item.seasons ?: return
        for (season in seasons) {
            val episode = season.episodes?.find { it.id == episodeItem.id }
            if (episode != null) {
                router.navigateTo(
                    router.screens.player(params.itemId, season.number, episode.number)
                )
                return
            }
        }
    }

    private fun showTrailer() {
        val trailerUrl = currentItem?.trailer?.url ?: currentItem?.trailer?.file ?: return
        updateViewState<DetailsScreenState.Content> {
            copy(trailerUrl = trailerUrl)
        }
    }

    private fun hideTrailer() {
        updateViewState<DetailsScreenState.Content> {
            copy(trailerUrl = null)
        }
    }

    override fun onBackPressed() {
        val state = stateValue as? DetailsScreenState.Content
        when {
            state?.trailerUrl != null -> hideTrailer()
            state?.seasonsPanelVisible == true -> hideSeasonsPanel()
            else -> {
                router.back()
                return
            }
        }
        router.addBackDispatcher(this)
    }

    private fun onWatchlistToggle() {
        val previous = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: return
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = !isInWatchlist)
        }
        launch {
            try {
                val item = interactor.toggleWatchlist(params.itemId)
                currentItem = item
                val inWatchlist = item.inWatchlist ?: !previous
                updateViewState<DetailsScreenState.Content> {
                    copy(isInWatchlist = inWatchlist)
                }
                val messageRes = if (inWatchlist) {
                    R.string.video_details_watchlist_added
                } else {
                    R.string.video_details_watchlist_removed
                }
                showMessage(resources.getString(messageRes))
            } catch (e: Exception) {
                updateViewState<DetailsScreenState.Content> {
                    copy(isInWatchlist = previous)
                }
                throw e
            }
        }
    }
}