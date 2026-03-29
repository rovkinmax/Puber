package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.collections.detail.CollectionDetailScreen
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState

internal class HomeVM(
    router: AppRouter,
    private val interactor: HomeInteractor,
    private val mapper: HomeUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<HomeViewState>(router) {

    override val initialViewState: HomeViewState = HomeViewState.Loading

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is HomeViewState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(HomeViewState.Error(error.message))
        }
    }

    override fun onStart() {
        loadHome()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                router.navigateTo(router.screens.details(item.id))
            }
            is CommonAction.RetryClicked -> loadHome()
            else -> super.onAction(action)
        }
    }

    fun onHeroClick(itemId: Int) {
        router.navigateTo(router.screens.details(itemId))
    }

    fun onCollectionClick(id: Int, title: String) {
        router.navigateTo(CollectionDetailScreen(id, title))
    }

    private fun loadHome() {
        updateViewState(HomeViewState.Content())

        launch {
            val items = interactor.getHotItems()
            updateViewState<HomeViewState.Content> { copy(heroItems = mapper.mapHeroItems(items)) }
        }
        launch {
            val items = interactor.getWatchingItems()
            mapper.mapItemSection(items, HomeSectionType.ContinueWatching)?.let { addSection(it) }
        }
        launch {
            val items = interactor.getFreshItems()
            mapper.mapItemSection(items, HomeSectionType.Fresh)?.let { addSection(it) }
        }
        launch {
            val items = interactor.getPopularByType("movie")
            mapper.mapItemSection(items, HomeSectionType.PopularMovies)?.let { addSection(it) }
        }
        launch {
            val items = interactor.getPopularByType("serial")
            mapper.mapItemSection(items, HomeSectionType.PopularSeries)?.let { addSection(it) }
        }
        launch {
            val folders = interactor.getBookmarkFolders()
            val firstFolder = folders.firstOrNull() ?: return@launch
            val items = interactor.getBookmarkItems(firstFolder.id)
            mapper.mapItemSection(items, HomeSectionType.Bookmarks)?.let { addSection(it) }
        }
        launch {
            val items = interactor.getCollections()
            mapper.mapCollectionSection(items)?.let { addSection(it) }
        }
        launch {
            val items = interactor.getHotItems(20)
            mapper.mapItemSection(items, HomeSectionType.Hot)?.let { addSection(it) }
        }
    }

    private fun addSection(section: HomeSectionState) {
        updateViewState<HomeViewState.Content> {
            copy(sections = (sections + section).sortedBy { it.type.ordinal })
        }
    }
}
