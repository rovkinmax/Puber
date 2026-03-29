package com.kino.puber.ui.feature.search.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.search.SearchInteractor
import com.kino.puber.ui.feature.search.model.SearchViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal class SearchVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val interactor: SearchInteractor,
    private val mapper: VideoItemUIMapper,
) : PuberVM<SearchViewState>(router) {

    override val initialViewState = SearchViewState.Idle

    private var query: String = ""
    private var searchJob: Job? = null

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.TextChanged -> onQueryChanged(action.text)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.RetryClicked -> executeSearch()
            else -> super.onAction(action)
        }
    }

    private fun onQueryChanged(text: String) {
        query = text
        searchJob?.cancel()
        if (query.length < MIN_QUERY_LENGTH) {
            updateViewState(SearchViewState.Idle)
            return
        }
        searchJob = launch {
            delay(DEBOUNCE_DELAY_MS)
            executeSearch()
        }
    }

    private fun executeSearch() {
        if (query.length < MIN_QUERY_LENGTH) return
        launch {
            updateViewState(SearchViewState.Loading)
            val items = interactor.search(query)
            if (items.isEmpty()) {
                updateViewState(SearchViewState.Empty)
            } else {
                updateViewState(SearchViewState.Content(mapper.mapShortItemList(items)))
            }
        }
    }

    private fun onItemSelected(item: VideoItemUIState) {
        router.navigateTo(router.screens.details(itemId = item.id))
    }

    override fun dispatchError(error: ErrorEntity) {
        when (stateValue) {
            is SearchViewState.Loading -> updateViewState(SearchViewState.Error(error.message))
            is SearchViewState.Content -> showMessage(error.message)
            else -> showMessage(error.message)
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val DEBOUNCE_DELAY_MS = 1500L
    }
}
