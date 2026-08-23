package com.kino.puber.ui.feature.search.vm

import com.kino.puber.R
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.search.SearchInteractor
import com.kino.puber.ui.feature.search.model.SearchScreenParams
import com.kino.puber.ui.feature.search.model.SearchScreenParams.SearchMode
import com.kino.puber.ui.feature.search.model.SearchPresentation
import com.kino.puber.ui.feature.search.model.SearchViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal class SearchVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val interactor: SearchInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val mapper: VideoItemUIMapper,
    private val resources: ResourceProvider,
    private val params: SearchScreenParams = SearchScreenParams(),
) : PuberVM<SearchViewState>(router) {

    private var query: String = ""
    private var searchJob: Job? = null
    private val actorQuery: String? = (params.mode as? SearchMode.Actor)?.actorQuery
    private val presentation = createPresentation(params.mode)

    override val initialViewState = SearchViewState.Idle(presentation)

    override fun onStart() {
        if (actorQuery != null) {
            executeActorSearch(actorQuery)
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.TextChanged -> if (actorQuery == null) onQueryChanged(action.text)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
            is CommonAction.RetryClicked -> executeCurrentSearch()
            else -> super.onAction(action)
        }
    }

    private fun onQueryChanged(text: String) {
        query = text
        searchJob?.cancel()
        if (query.length < MIN_QUERY_LENGTH) {
            updateViewState(SearchViewState.Idle(presentation))
            return
        }
        searchJob = launch {
            delay(DEBOUNCE_DELAY_MS)
            executeSearch()
        }
    }

    private fun executeCurrentSearch() {
        actorQuery?.let(::executeActorSearch) ?: executeSearch()
    }

    private fun executeSearch() {
        if (query.length < MIN_QUERY_LENGTH) return
        launch {
            updateViewState(SearchViewState.Loading(presentation))
            val items = interactor.search(query)
            if (items.isEmpty()) {
                updateViewState(SearchViewState.Empty(presentation))
            } else {
                updateViewState(
                    SearchViewState.Content(
                        items = mapper.mapShortItemList(items),
                        presentation = presentation,
                    ),
                )
            }
        }
    }

    private fun executeActorSearch(actorQuery: String) {
        launch {
            updateViewState(SearchViewState.Loading(presentation))
            val items = interactor.searchByActor(actorQuery)
            if (items.isEmpty()) {
                updateViewState(SearchViewState.Empty(presentation))
            } else {
                updateViewState(
                    SearchViewState.Content(
                        items = mapper.mapShortItemList(items),
                        presentation = presentation,
                    ),
                )
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
        if (changes == null || changes.isEmpty) return
        if (actorQuery == null && query.length < MIN_QUERY_LENGTH) return
        executeCurrentSearch()
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
            is SearchViewState.Loading -> updateViewState(
                SearchViewState.Error(
                    message = error.message,
                    presentation = presentation,
                ),
            )
            is SearchViewState.Content -> showMessage(error.message)
            else -> showMessage(error.message)
        }
    }

    private fun createPresentation(mode: SearchMode): SearchPresentation = when (mode) {
        SearchMode.Title -> SearchPresentation(
            title = null,
            inputHint = resources.getString(R.string.search_hint),
            emptyMessage = resources.getString(R.string.search_no_results),
            showSearchInput = true,
            focusResultsOnContent = false,
            showRetryOnError = false,
        )
        is SearchMode.Actor -> SearchPresentation(
            title = resources.getString(R.string.search_actor_title, mode.actorQuery),
            inputHint = "",
            emptyMessage = resources.getString(R.string.search_actor_no_results),
            showSearchInput = false,
            focusResultsOnContent = true,
            showRetryOnError = true,
        )
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val DEBOUNCE_DELAY_MS = 1500L
    }
}
