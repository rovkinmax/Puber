package com.kino.puber.ui.feature.showall.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll

internal class ShowAllVM(
    paginator: Paginator.Store<Item>,
    private val config: SectionConfig,
    private val interactor: ContentListInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
) : PagingVM<Item, ShowAllViewState>(paginator, router, errorHandler) {

    private var currentPage = 0
    private var cachedInput: List<Item>? = null
    private var cachedOutput: List<VideoItemUIState> = emptyList()
    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private var closing = false

    override val initialViewState = ShowAllViewState.Loading

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
        if (closing) return
        when (action) {
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.RetryClicked -> resetPaging()
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                openDetails(item.id)
            }
            is CommonAction.ItemPlayed<*> -> {
                val item = action.item as VideoItemUIState
                openPlayer(item.id)
            }
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setItemSaved(item, action.isSaved)
            }
        }
    }

    private fun mapItems(items: List<Item>): List<VideoItemUIState> {
        if (items === cachedInput) return cachedOutput
        return mapper.mapShortItemList(items).also {
            cachedInput = items
            cachedOutput = it
        }
    }

    private fun openDetails(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun openPlayer(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        contentChanges = contentChanges.merge(changes)
        interactor.invalidateFirstPageCache()
        resetPaging()
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        val newState = when (state) {
            is Paginator.State.Loading -> ShowAllViewState.Loading
            is Paginator.State.Empty -> ShowAllViewState.Empty
            is Paginator.State.ErrorEmpty -> ShowAllViewState.Error(state.error.message)
            is Paginator.State.Data<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingNext<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
                isLoadingMore = true,
            )
            is Paginator.State.Error<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.PageErrorNext<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.Refreshing<*> -> ShowAllViewState.Content(
                items = mapItems(state.data as List<Item>),
            )
            is Paginator.State.LoadingPrev<*>,
            is Paginator.State.PageErrorPrev<*> -> return
        }
        updateViewState(newState)
    }

    private fun setItemSaved(item: VideoItemUIState, saved: Boolean) {
        updateSavedItem(item.id, saved)
        launchMutation {
            savedItemInteractor.setSaved(
                itemId = item.id,
                isSeriesLike = item.isSeriesLike,
                saved = saved,
            ).onSuccess { actualSaved ->
                updateSavedItem(item.id, actualSaved)
                contentChanges = contentChanges.merge(
                    ContentChangeSet.single(
                        itemId = item.id,
                        type = if (item.isSeriesLike) {
                            ContentChangeType.Watchlist
                        } else {
                            ContentChangeType.Bookmark
                        },
                    )
                )
                interactor.invalidateFirstPageCache()
                resetPaging()
            }.onFailure {
                updateSavedItem(item.id, item.isSaved)
                throw it
            }
        }
    }

    private fun updateSavedItem(itemId: Int, saved: Boolean) {
        updateViewState<ShowAllViewState.Content> {
            copy(
                items = items.map { item ->
                    if (item.id == itemId) item.copy(isSaved = saved) else item
                },
            )
        }
    }

    override fun onBackPressed() {
        if (closing) {
            router.addBackDispatcher(this)
            return
        }
        closing = true
        router.addBackDispatcher(this)
        launch {
            awaitPendingMutations()
            router.removeBackDispatcher(this@ShowAllVM)
            router.back(RESULT_CONTENT_CHANGED, contentChanges)
        }
    }

    private fun launchMutation(block: suspend CoroutineScope.() -> Unit): Job {
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                pendingMutations.remove(job)
            }
        }
        pendingMutations += job
        job.start()
        return job
    }

    private suspend fun awaitPendingMutations() {
        while (true) {
            val activeJobs = pendingMutations.filter(Job::isActive)
            if (activeJobs.isEmpty()) return
            activeJobs.joinAll()
        }
    }
}
