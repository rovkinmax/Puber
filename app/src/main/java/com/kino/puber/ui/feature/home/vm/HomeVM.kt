package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState
import kotlinx.coroutines.async

internal class HomeVM(
    router: AppRouter,
    private val interactor: HomeInteractor,
    private val mapper: HomeUIMapper,
) : PuberVM<HomeViewState>(router) {

    override val initialViewState: HomeViewState = HomeViewState.Loading

    override fun onStart() {
        loadHome()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                router.navigateTo(router.screens.details(item.id))
            }
            else -> super.onAction(action)
        }
    }

    fun onHeroClick(itemId: Int) {
        router.navigateTo(router.screens.details(itemId))
    }

    private fun loadHome() {
        updateViewState(HomeViewState.Loading)
        launch {
            val hero = async { interactor.getHotItems() }
            val cw = async { interactor.getContinueWatching() }
            val fresh = async { interactor.getFreshItems() }
            val popMovies = async { interactor.getPopularByType("movie") }
            val popSeries = async { interactor.getPopularByType("serial") }
            val bookmarks = async { interactor.getBookmarkFolders() }
            val collections = async { interactor.getCollections() }
            val hot = async { interactor.getHotItems(20) }

            val content = HomeViewState.Content()
            updateViewState(content)

            hero.await().onSuccess { items ->
                updateViewState<HomeViewState.Content> { copy(heroItems = mapper.mapHeroItems(items)) }
            }

            cw.await().onSuccess { items ->
                mapper.mapHistorySection(items)?.let { addSection(it) }
            }

            fresh.await().onSuccess { items ->
                mapper.mapItemSection(items, HomeSectionType.Fresh)?.let { addSection(it) }
            }

            popMovies.await().onSuccess { items ->
                mapper.mapItemSection(items, HomeSectionType.PopularMovies)?.let { addSection(it) }
            }

            popSeries.await().onSuccess { items ->
                mapper.mapItemSection(items, HomeSectionType.PopularSeries)?.let { addSection(it) }
            }

            bookmarks.await().onSuccess { folders ->
                val firstFolder = folders.firstOrNull() ?: return@onSuccess
                interactor.getBookmarkItems(firstFolder.id).onSuccess { items ->
                    mapper.mapItemSection(items, HomeSectionType.Bookmarks)?.let { addSection(it) }
                }
            }

            collections.await().onSuccess { items ->
                mapper.mapCollectionSection(items)?.let { addSection(it) }
            }

            hot.await().onSuccess { items ->
                mapper.mapItemSection(items, HomeSectionType.Hot)?.let { addSection(it) }
            }
        }
    }

    private fun addSection(section: HomeSectionState) {
        updateViewState<HomeViewState.Content> {
            copy(sections = (sections + section).sortedBy { it.type.ordinal })
        }
    }
}
