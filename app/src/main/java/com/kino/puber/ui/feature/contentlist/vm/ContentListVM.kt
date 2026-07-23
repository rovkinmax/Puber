package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.ContentListViewState
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.ShowAllScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

internal class ContentListVM(
    router: AppRouter,
    private val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
    private val genreInteractor: GenreInteractor,
    private val navPrefs: NavigationPreferencesRepository,
    private val contentListRefreshCoordinator: ContentListRefreshCoordinator,
    private val contentType: String? = null,
) : PuberVM<ContentListViewState>(router) {

    override val initialViewState = ContentListViewState()
    private var focusedItemJob: Job? = null

    override fun onStart() {
        val isTopTabs = navPrefs.getNavigationMode() == NavigationMode.TopTabs
        updateViewState<ContentListViewState> {
            copy(
                showDetailPanel = !isTopTabs,
                showGenreChips = isTopTabs,
            )
        }
        if (isTopTabs) {
            loadGenres()
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is ContentListAction.ShowAll -> openShowAll(action.config)
            is ContentListAction.GenreSelected -> onGenreSelected(action.genreId)
        }
    }

    private fun loadGenres() {
        launch {
            genreInteractor.getGenres(type = contentType).onSuccess { genres ->
                updateViewState<ContentListViewState> { copy(genres = genres) }
            }
        }
    }

    private fun onGenreSelected(genreId: Int?) {
        updateViewState<ContentListViewState> { copy(selectedGenreId = genreId) }
    }

    private fun onItemFocused(item: VideoItemUIState) {
        if (!stateValue.showDetailPanel) return
        focusedItemJob?.cancel()
        focusedItemJob = launch {
            delay(FOCUS_DETAILS_DEBOUNCE_MS)
            updateViewState<ContentListViewState> { copy(selectedItem = VideoDetailsUIState.Loading) }
            val details = interactor.getItemDetails(item.id)
            updateViewState<ContentListViewState> { copy(selectedItem = mapper.mapDetailedItem(details)) }
        }
    }

    private fun onItemSelected(item: VideoItemUIState) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onItemPlayed(item: VideoItemUIState) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(item.id),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun openShowAll(config: SectionConfig) {
        router.navigateForResult<ContentChangeSet>(
            screen = ShowAllScreen(config),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedFromShowAll,
        )
    }

    private fun onReturnedFromShowAll(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        refreshContent(changes)
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        refreshContent(changes)
    }

    private fun refreshContent(changes: ContentChangeSet) {
        changes.itemIds.forEach(interactor::invalidateItemDetails)
        interactor.invalidateFirstPageCache()
        contentListRefreshCoordinator.requestRefresh()
        val selectedItemId = stateValue.selectedItem.id
        if (selectedItemId > 0 && changes.affectsItem(selectedItemId)) {
            focusedItemJob?.cancel()
            focusedItemJob = launch {
                val details = interactor.getItemDetails(selectedItemId)
                updateViewState<ContentListViewState> {
                    copy(selectedItem = mapper.mapDetailedItem(details))
                }
            }
        }
    }

    private companion object {
        const val FOCUS_DETAILS_DEBOUNCE_MS = 150L
    }
}
