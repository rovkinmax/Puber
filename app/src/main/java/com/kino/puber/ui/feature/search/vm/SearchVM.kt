package com.kino.puber.ui.feature.search.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.search.SearchInteractor
import com.kino.puber.ui.feature.search.model.SearchViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal class SearchVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val interactor: SearchInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val mapper: VideoItemUIMapper,
) : PuberVM<SearchViewState>(router) {

    override val initialViewState = SearchViewState.Idle

    private var query: String = ""
    private var searchJob: Job? = null

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.TextChanged -> onQueryChanged(action.text)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
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
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId = item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onItemPlayed(item: VideoItemUIState) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId = item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty || query.length < MIN_QUERY_LENGTH) return
        executeSearch()
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        updateSavedItem(item.id, saved)
        launch {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).onSuccess { actualSaved ->
                updateSavedItem(item.id, actualSaved)
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<SearchViewState.Content> {
            copy(
                items = items.map { item ->
                    if (item.id == itemId) item.copy(isSaved = saved) else item
                },
            )
        }
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
