package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.logger.log
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
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.openBookmarkPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

internal class ContentListVM(
    router: AppRouter,
    private val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
    private val genreInteractor: GenreInteractor,
    private val navPrefs: NavigationPreferencesRepository,
    private val contentListRefreshCoordinator: ContentListRefreshCoordinator,
    private val contentType: String? = null,
    private val heroConfigs: List<SectionConfig> = emptyList(),
) : PuberVM<ContentListViewState>(router) {

    override val initialViewState = ContentListViewState(
        isHeroLoading = heroConfigs.isNotEmpty(),
    )
    private var focusedItemJob: Job? = null
    private var heroLoadJob: Job? = null

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
        loadHero()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as VideoItemUIState)
            is CommonAction.ItemSelected<*> -> onItemSelected(action.item as VideoItemUIState)
            is CommonAction.ItemPlayed<*> -> onItemPlayed(action.item as VideoItemUIState)
            is CommonAction.ItemBookmarksRequested<*> ->
                router.openBookmarkPicker(
                    item = action.item as VideoItemUIState,
                    listener = ::onBookmarkPickerResult,
                )
            is ContentListAction.ShowAll -> openShowAll(action.config)
            is ContentListAction.GenreSelected -> onGenreSelected(action.genreId)
            is ContentListAction.HeroSelected -> openDetails(action.itemId)
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
        openDetails(item.id)
    }

    private fun openDetails(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
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

    private fun onBookmarkPickerResult(result: BookmarkPickerResult?) {
        result ?: return
        refreshContent(
            ContentChangeSet.single(
                itemId = result.itemId,
                type = ContentChangeType.Bookmark,
            )
        )
    }

    private fun refreshContent(changes: ContentChangeSet) {
        changes.itemIds.forEach(interactor::invalidateItemDetails)
        interactor.invalidateFirstPageCache()
        contentListRefreshCoordinator.requestRefresh()
        loadHero()
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

    private fun loadHero() {
        if (heroConfigs.isEmpty()) return
        heroLoadJob?.cancel()
        updateViewState<ContentListViewState> {
            copy(isHeroLoading = true)
        }
        heroLoadJob = launch {
            try {
                val items = supervisorScope {
                    heroConfigs
                        .map { config ->
                            async { loadHeroItems(config) }
                        }
                        .flatMap { it.await() }
                }
                    .distinctBy { it.id }
                    .sortedByDescending { it.ratingPercentage ?: 0 }
                    .take(HERO_ITEMS_COUNT)
                updateViewState<ContentListViewState> {
                    copy(
                        heroItems = mapper.mapHeroItems(items),
                        isHeroLoading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log(error, "Failed to load content-list hero")
                updateViewState<ContentListViewState> {
                    copy(
                        heroItems = emptyList(),
                        isHeroLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun loadHeroItems(config: SectionConfig) = try {
        interactor.loadPage(config, page = 1).items
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log(error, "Failed to load content-list hero ${config.type}")
        emptyList()
    }

    private companion object {
        const val FOCUS_DETAILS_DEBOUNCE_MS = 150L
        const val HERO_ITEMS_COUNT = 10
    }
}
