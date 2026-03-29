package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.ui.feature.contentlist.model.SectionState

internal class SectionVM(
    paginator: Paginator.Store<Item>,
    private val config: SectionConfig,
    private val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
) : PagingVM<Item, SectionState>(paginator, router, errorHandler) {

    private var currentPage = 0
    private var cachedInput: List<Item>? = null
    private var cachedOutput: List<VideoItemUIState> = emptyList()

    override val initialViewState = SectionState.Loading

    override fun onStart() = init()

    override fun onLoadFirstPage() {
        currentPage = 0
        pagingLaunch(errorHandlerGeneral) {
            val response = interactor.loadPage(config, page = 1)
            currentPage = response.pagination.current
            isFullDataNext = currentPage >= response.pagination.total
            replace(response.items)
        }
    }

    override fun onLoadNextPage(key: Item?) {
        pagingLaunch(errorHandlerPaging) {
            val response = interactor.loadPage(config, page = currentPage + 1)
            currentPage = response.pagination.current
            isFullDataNext = currentPage >= response.pagination.total
            setNextPage(response.items)
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.RetryClicked -> resetPaging()
        }
    }

    private fun mapItems(items: List<Item>): List<VideoItemUIState> {
        if (items === cachedInput) return cachedOutput
        return mapper.mapShortItemList(items).also {
            cachedInput = items
            cachedOutput = it
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        val newState = when (state) {
            is Paginator.State.Loading -> SectionState.Loading
            is Paginator.State.Empty -> SectionState.Empty
            is Paginator.State.ErrorEmpty -> SectionState.Error(state.error.message)
            is Paginator.State.Data<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingNext<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
                isLoadingMore = true,
            )
            is Paginator.State.Error<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.PageErrorNext<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.Refreshing<*> -> SectionState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingPrev<*>,
            is Paginator.State.PageErrorPrev<*> -> return
        }
        updateViewState(newState)
    }
}
