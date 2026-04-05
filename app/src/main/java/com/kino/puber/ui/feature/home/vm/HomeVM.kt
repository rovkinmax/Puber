package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.collections.detail.CollectionDetailScreen
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState

internal class HomeVM(
    router: AppRouter,
    private val interactor: HomeInteractor,
    private val mapper: HomeUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<HomeViewState>(router) {

    companion object {
        private const val HOT_ITEMS_COUNT = 20
        private const val HERO_ITEMS_COUNT = 10
    }

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
            is CommonAction.OnResume -> silentRefresh()
            else -> super.onAction(action)
        }
    }

    fun onHeroClick(itemId: Int) {
        router.navigateTo(router.screens.details(itemId))
    }

    fun onCollectionClick(id: Int, title: String) {
        router.navigateTo(CollectionDetailScreen(id, title))
    }

    private fun silentRefresh() {
        if (stateValue !is HomeViewState.Content) return
        loadHome()
    }

    private fun loadHome() {
        launch {
            supervisorScope {
                val hotMoviesDeferred = async { interactor.getHotItems("movie", HOT_ITEMS_COUNT).logFailure("hot movies") }
                val hotSeriesDeferred = async { interactor.getHotItems("serial", HOT_ITEMS_COUNT).logFailure("hot series") }
                val watchingDeferred = async { interactor.getWatchingItems().logFailure("watching") }
                val freshDeferred = async { interactor.getFreshItems().logFailure("fresh") }
                val popularMoviesDeferred = async { interactor.getPopularByType("movie").logFailure("popular movies") }
                val popularSeriesDeferred = async { interactor.getPopularByType("serial").logFailure("popular series") }
                val bookmarksDeferred = async { loadBookmarkSection() }
                val collectionsDeferred = async { interactor.getCollections().logFailure("collections") }

                val hotMovies = hotMoviesDeferred.await().orEmpty()
                val hotSeries = hotSeriesDeferred.await().orEmpty()
                val hotItems = (hotMovies + hotSeries).shuffled()

                val sections = listOfNotNull(
                    watchingDeferred.await()?.let { mapper.mapItemSection(it, HomeSectionType.ContinueWatching) },
                    freshDeferred.await()?.let { mapper.mapItemSection(it, HomeSectionType.Fresh) },
                    popularMoviesDeferred.await()?.let { mapper.mapItemSection(it, HomeSectionType.PopularMovies) },
                    popularSeriesDeferred.await()?.let { mapper.mapItemSection(it, HomeSectionType.PopularSeries) },
                    bookmarksDeferred.await()?.let { mapper.mapItemSection(it, HomeSectionType.Bookmarks) },
                    collectionsDeferred.await()?.let { mapper.mapCollectionSection(it) },
                    mapper.mapItemSection(hotItems, HomeSectionType.Hot),
                ).sortedBy { it.type.ordinal }

                updateViewState(
                    HomeViewState.Content(
                        heroItems = mapper.mapHeroItems(hotItems.take(HERO_ITEMS_COUNT)),
                        sections = sections,
                    )
                )
            }
        }
    }

    private suspend fun loadBookmarkSection(): List<Item>? {
        val folders = interactor.getBookmarkFolders().logFailure("bookmark folders") ?: return null
        val firstFolder = folders.firstOrNull() ?: return emptyList()
        return interactor.getBookmarkItems(firstFolder.id).logFailure("bookmark items")
    }

    private fun <T> Result<T>.logFailure(section: String): T? {
        onFailure { log(it, "Failed to load $section") }
        return getOrNull()
    }
}
