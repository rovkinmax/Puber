package com.kino.puber.ui.feature.history.vm

import androidx.annotation.VisibleForTesting
import com.kino.puber.core.collections.EquallyFunction
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.History
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistoryTraversal
import com.kino.puber.domain.interactor.history.rowKeyOrNull
import com.kino.puber.domain.interactor.history.semanticKeyOrNull
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import kotlinx.coroutines.CancellationException
import kotlin.math.min

private const val FIRST_PAGE = 1

internal val HistoryRowComparator = EquallyFunction<History> { oldItem, newItem ->
    oldItem.rowKeyOrNull()?.let { it == newItem.rowKeyOrNull() } ?: false
}

private fun rawItemForKey(key: HistoryRowKey?, history: List<History>): History? {
    return key?.let { target ->
        history.firstOrNull { it.rowKeyOrNull() == target }
    }
}

internal class HistoryVM(
    paginator: Paginator.Store<History>,
    private val interactor: HistoryInteractor,
    private val mapper: HistoryUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
) : PagingVM<History, HistoryViewState>(paginator, router, errorHandler) {

    override val initialViewState: HistoryViewState = HistoryViewState.Loading

    private val runtime = HistoryRuntimeStore()
    private val contentPublicationLock = Any()

    @VisibleForTesting
    internal val testRuntimeState: HistoryRuntimeState
        get() = runtime.snapshot()

    @VisibleForTesting
    internal var testAfterDeleteAvailabilityRead: (() -> Unit)? = null

    @VisibleForTesting
    internal var testBeforeFocusPublicationLockAcquire: (() -> Unit)? = null

    override fun onStart() = init()

    override fun onAction(action: UIAction) {
        if (action.isBlockedDuringDeletionFlow() && runtime.isDeletionFlowActive()) return
        when (action) {
            is CommonAction.ItemSelected<*> -> openDetails(action.item as HistoryItemUIState)
            is CommonAction.ItemFocused<*> -> onItemFocused(action.item as HistoryItemUIState)
            CommonAction.LoadMore,
            CommonAction.ReloadNextPage -> requestNextPage()
            CommonAction.Refresh -> requestRefresh()
            CommonAction.OnResume -> requestResumeRefresh()
            CommonAction.RetryClicked -> retry()
            is HistoryAction.OpenContextMenu -> openContextMenu(action.item)
            HistoryAction.DismissContextMenu -> dismissContextMenu()
            is HistoryAction.Play -> play(
                item = action.item,
                startMode = action.startMode,
            )
            is HistoryAction.OpenDetails -> openDetails(action.item)
            is HistoryAction.DeleteExactMedia -> deleteExactMedia(action.item)
            HistoryAction.RetryReconciliation -> retry()
            else -> super.onAction(action)
        }
    }

    override fun onLoadFirstPage() {
        val request = runtime.beginFirstPage() ?: return
        pagingLaunch {
            try {
                val result = loadHistoryPageDepth(request.loadedPageDepth)
                val accepted = runtime.acceptFirstPage(
                    operationId = request.operationId,
                    result = result,
                ) ?: return@pagingLaunch
                replace(
                    result.items,
                    rawItemForKey(accepted.focusedKey, result.items),
                )
            } catch (error: CancellationException) {
                runtime.cancelFirstPage(request.operationId)
                throw error
            } catch (error: Throwable) {
                handleFirstPageFailure(
                    operationId = request.operationId,
                    error = errorHandler.map(error),
                )
            }
        }
    }

    override fun onLoadNextPage(key: History?) {
        val request = runtime.currentNextPageRequest() ?: return
        pagingLaunch {
            try {
                val result = loadNextRenderablePage(runtime.snapshot().currentPage)
                val accepted = runtime.acceptNextPage(
                    operationId = request.operationId,
                    result = result,
                    merge = ::mergeStableHistory,
                )
                if (accepted) {
                    setNextPage(result.items)
                }
            } catch (error: CancellationException) {
                runtime.cancelNextPage(request.operationId)
                throw error
            } catch (error: Throwable) {
                val mappedError = errorHandler.map(error)
                val accepted = runtime.failNextPage(
                    operationId = request.operationId,
                    message = mappedError.message,
                )
                if (accepted) {
                    setPageError(mappedError)
                }
            }
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        val state = runtime.resetAfterUnhandledError()
        if (state.stableHistory.isEmpty()) {
            updateViewState(HistoryViewState.Error(error.message))
        } else {
            replace(
                state.stableHistory,
                rawItemForKey(state.focusedKey, state.stableHistory),
            )
            showMessage(error.message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        when (state) {
            Paginator.State.Loading -> dispatchLoadingState()
            Paginator.State.Empty -> dispatchEmptyState()
            is Paginator.State.ErrorEmpty -> {
                runtime.update { it.copy(operation = HistoryOperation.Idle) }
                updateViewState(HistoryViewState.Error(state.error.message))
            }
            is Paginator.State.Data<*> -> {
                showContent(
                    history = state.data as List<History>,
                    paginatorFocus = state.key as History?,
                )
                finishContentPublication(
                    HistoryPublicationKind.FIRST_PAGE,
                    HistoryPublicationKind.REFRESH_ERROR,
                    HistoryPublicationKind.NEXT_PAGE,
                )
            }
            is Paginator.State.Refreshing<*> -> showContent(
                history = state.data as List<History>,
                isRefreshing = true,
            )
            is Paginator.State.LoadingNext<*> -> showContent(
                history = state.data as List<History>,
                isLoadingMore = true,
            )
            is Paginator.State.LoadingPrev<*> -> showContent(state.data as List<History>)
            is Paginator.State.Error<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
                finishContentPublication(HistoryPublicationKind.REFRESH_ERROR)
            }
            is Paginator.State.PageErrorNext<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
                finishContentPublication(HistoryPublicationKind.NEXT_PAGE_ERROR)
            }
            is Paginator.State.PageErrorPrev<*> -> {
                showMessage(state.error.message)
                showContent(state.data as List<History>)
            }
        }
    }

    private fun dispatchLoadingState() {
        val runtimeState = runtime.snapshot()
        if (runtimeState.stableHistory.isEmpty()) {
            updateViewState(HistoryViewState.Loading)
        } else {
            showContent(
                history = runtimeState.stableHistory,
                isRefreshing = true,
            )
        }
    }

    private fun dispatchEmptyState() {
        if (!runtime.hasPendingEmptyPublication()) return
        updateViewState(HistoryViewState.Empty)
        runtime.completeEmptyPublication()
        runQueuedDeletionIfReady()
    }

    private fun completePublishedOperation(vararg kinds: HistoryPublicationKind) {
        runtime.completePublication(*kinds)
    }

    private fun finishContentPublication(vararg kinds: HistoryPublicationKind) {
        completePublishedOperation(*kinds)
        runQueuedDeletionIfReady()
        runDeferredRefreshIfReady()
        updateDeleteExactMediaAvailability()
    }

    private fun showContent(
        history: List<History>,
        isRefreshing: Boolean = false,
        isLoadingMore: Boolean = false,
        paginatorFocus: History? = null,
    ) {
        synchronized(contentPublicationLock) {
            val items = mapper.map(history)
            val availableKeys = items.mapTo(mutableSetOf(), HistoryItemUIState::rowKey)
            val paginatorFocusKey = paginatorFocus?.rowKeyOrNull()
            val runtimeState = runtime.prepareContentPublication(availableKeys, paginatorFocusKey)
            updateViewState(
                runtimeState.toContentViewState(
                    items = items,
                    availableKeys = availableKeys,
                    isRefreshing = isRefreshing,
                    isLoadingMore = isLoadingMore,
                ),
            )
        }
    }

    private fun requestNextPage() {
        val started = runtime.beginNextPage()
        if (!started) return
        updateDeleteExactMediaAvailability()
        notifyLoadNextPage()
    }

    private fun requestRefresh(deferIfBusy: Boolean = false) {
        if (stateValue !is HistoryViewState.Content) return
        val started = runtime.beginRefresh(deferIfBusy)
        if (!started) return
        enqueueRefresh()
    }

    private fun enqueueRefresh() {
        val state = runtime.snapshot()
        updateDeleteExactMediaAvailability()
        replace(
            state.stableHistory,
            rawItemForKey(state.focusedKey, state.stableHistory),
        )
        refresh()
    }

    private fun requestResumeRefresh() {
        if (!runtime.snapshot().hasCompletedInitialLoad) return
        when (stateValue) {
            HistoryViewState.Loading -> Unit
            HistoryViewState.Empty,
            is HistoryViewState.Error -> requestRestart()
            is HistoryViewState.Content -> requestRefresh(deferIfBusy = true)
        }
    }

    private fun retry() {
        val operation = runtime.snapshot().operation
        if (operation is HistoryOperation.ReconciliationFailed) {
            retryReconciliation(operation.reconciliation)
        } else {
            requestRestart()
        }
    }

    private fun requestRestart() {
        if (runtime.beginRestart()) {
            resetPaging()
        }
    }

    private fun onItemFocused(item: HistoryItemUIState) {
        testBeforeFocusPublicationLockAcquire?.invoke()
        synchronized(contentPublicationLock) {
            runtime.focus(item)
            updateViewState<HistoryViewState.Content> { copy(focusKey = item.rowKey) }
        }
    }

    private fun openContextMenu(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            val opened = runtime.openMenu(item)
            if (!opened) return
            updateViewState<HistoryViewState.Content> {
                copy(openMenuKey = item.rowKey, focusKey = item.rowKey)
            }
        }
    }

    private fun dismissContextMenu() {
        synchronized(contentPublicationLock) {
            runtime.dismissMenu()
            updateViewState<HistoryViewState.Content> { copy(openMenuKey = null) }
        }
    }

    private fun play(
        item: HistoryItemUIState,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
    ) {
        when (val target = item.playbackTarget) {
            is HistoryPlaybackTarget.Movie -> {
                interactor.invalidateItemDetails(item.itemId)
                router.navigateTo(
                    router.screens.player(
                        itemId = item.itemId,
                        videoNumber = target.videoNumber,
                        startMode = startMode,
                    ),
                )
            }
            is HistoryPlaybackTarget.Episode -> {
                interactor.invalidateItemDetails(item.itemId)
                router.navigateTo(
                    router.screens.player(
                        itemId = item.itemId,
                        seasonNumber = target.seasonNumber,
                        episodeNumber = target.episodeNumber,
                        startMode = startMode,
                    ),
                )
            }
            HistoryPlaybackTarget.Details -> openDetails(item)
        }
    }

    private fun openDetails(item: HistoryItemUIState) {
        dismissContextMenu()
        router.navigateTo(router.screens.historyDetails(item))
    }

    private fun deleteExactMedia(item: HistoryItemUIState) {
        val content = stateValue as? HistoryViewState.Content ?: return
        val currentItem = content.items.firstOrNull { it.rowKey == item.rowKey } ?: return
        val reconciliation = createReconciliationContext(content, currentItem) ?: return
        startDeletion(
            item = currentItem,
            reconciliation = reconciliation,
            queueIfBusy = true,
        )
    }

    private fun startDeletion(
        item: HistoryItemUIState,
        reconciliation: HistoryReconciliationContext,
        queueIfBusy: Boolean = false,
    ) {
        val transition = runtime.beginDeletion(item, reconciliation, queueIfBusy)
        if (transition.queued) {
            showQueuedDeletion(item)
            return
        }
        val deletion = transition.operation ?: return
        showDeletionPending(item)
        launch { performDeletion(deletion, item, reconciliation) }
    }

    private fun showQueuedDeletion(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            updateViewState<HistoryViewState.Content> {
                copy(
                    openMenuKey = null,
                    focusKey = item.rowKey,
                    isDeleteExactMediaAvailable = false,
                )
            }
        }
    }

    private fun showDeletionPending(item: HistoryItemUIState) {
        synchronized(contentPublicationLock) {
            val content = stateValue as? HistoryViewState.Content
            val deletingKeys = if (content?.items?.any { it.rowKey == item.rowKey } == true) {
                setOf(item.rowKey)
            } else {
                emptySet()
            }
            if (content != null) {
                updateViewState(
                    content.copy(
                        openMenuKey = null,
                        deletingKeys = deletingKeys,
                        nextPageErrorMessage = null,
                        focusKey = item.rowKey,
                        isDeleteExactMediaAvailable = false,
                    ),
                )
            }
        }
    }

    private suspend fun performDeletion(
        deletion: HistoryOperation.Deleting,
        item: HistoryItemUIState,
        reconciliation: HistoryReconciliationContext,
    ) {
        try {
            interactor.clearExactMediaHistory(
                mediaId = item.deletionMediaId,
                itemId = item.itemId,
            )
        } catch (error: CancellationException) {
            runtime.cancelDeletion(deletion.operationId)
            throw error
        } catch (error: Throwable) {
            onMutationFailure(
                operationId = deletion.operationId,
                error = errorHandler.map(error),
            )
            return
        }

        val retained = runtime.snapshot().stableHistory.filterNot { history ->
            if (item.semanticKey != null) {
                history.semanticKeyOrNull() == item.semanticKey
            } else {
                history.video?.id == item.deletionMediaId
            }
        }
        val requestedFocusKey = resolveHistoryFocusKey(
            items = mapper.map(retained),
            reconciliation = reconciliation,
        )
        val reconciliationStart = runtime.beginReconciliation(
            deletionId = deletion.operationId,
            reconciliation = reconciliation,
            retained = retained,
            requestedFocusKey = requestedFocusKey,
        ) ?: return
        showContent(reconciliationStart.retained, isRefreshing = true)
        reconcile(
            operationId = reconciliationStart.operationId,
            reconciliation = reconciliation,
        )
    }

    private fun onMutationFailure(
        operationId: Long,
        error: ErrorEntity,
    ) {
        val restored = runtime.failDeletion(operationId) ?: return
        showContent(restored.stableHistory)
        showMessage(error.message)
        runDeferredRefreshIfReady()
    }

    private fun retryReconciliation(reconciliation: HistoryReconciliationContext) {
        val operationId = runtime.beginReconciliationRetry(reconciliation) ?: return
        showContent(runtime.snapshot().stableHistory, isRefreshing = true)
        launch {
            reconcile(
                operationId = operationId,
                reconciliation = reconciliation,
            )
        }
    }

    private suspend fun reconcile(
        operationId: Long,
        reconciliation: HistoryReconciliationContext,
    ) {
        try {
            val result = loadHistoryPageDepth(reconciliation.loadedPageDepth)
            val requestedFocusKey = resolveHistoryFocusKey(
                items = mapper.map(result.items),
                reconciliation = reconciliation,
            )
            runtime.acceptReconciliation(
                operationId = operationId,
                reconciliation = reconciliation,
                result = result,
                requestedFocusKey = requestedFocusKey,
            ) ?: return
            if (result.items.isEmpty()) {
                updateViewState(HistoryViewState.Empty)
            } else {
                showContent(
                    history = result.items,
                    paginatorFocus = rawItemForKey(requestedFocusKey, result.items),
                )
            }
            val published = runtime.completeReconciliationPublication(operationId) ?: return
            replace(
                result.items,
                rawItemForKey(published.requestedFocusKey, result.items),
            )
            runQueuedDeletionIfReady()
            runDeferredRefreshIfReady()
            updateDeleteExactMediaAvailability()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val errorMessage = errorHandler.map(error).message
            val stableHistory = runtime.snapshot().stableHistory
            val requestedFocusKey = resolveHistoryFocusKey(
                items = mapper.map(stableHistory),
                reconciliation = reconciliation,
            )
            val failed = runtime.failReconciliation(
                operationId = operationId,
                reconciliation = reconciliation,
                message = errorMessage,
                requestedFocusKey = requestedFocusKey,
            ) ?: return
            showContent(failed.stableHistory)
            showMessage(errorMessage)
        }
    }

    private suspend fun loadHistoryPageDepth(loadedPageDepth: Int): HistoryPageDepthResult {
        val traversal = HistoryTraversal()
        val items = mutableListOf<History>()
        val requestedDepth = loadedPageDepth.coerceAtLeast(FIRST_PAGE)
        var page = FIRST_PAGE
        var current = FIRST_PAGE
        var totalPages = FIRST_PAGE
        var boundedDepth = FIRST_PAGE

        do {
            val response = interactor.getPage(page)
            check(response.pagination.current == page) {
                "History pagination did not match the requested page"
            }
            items += traversal.filterFirstOccurrences(response.items)
            current = response.pagination.current
            totalPages = response.pagination.total
            boundedDepth = min(
                requestedDepth,
                totalPages.coerceAtLeast(FIRST_PAGE),
            )
            page++
        } while (
            page <= boundedDepth ||
                (items.isEmpty() && current < totalPages)
        )

        return HistoryPageDepthResult(
            items = items,
            currentPage = current,
            totalPages = totalPages,
        )
    }

    private suspend fun loadNextRenderablePage(startPage: Int): HistoryNextPageResult {
        val traversal = HistoryTraversal(runtime.snapshot().stableHistory)
        var currentPage = startPage
        var totalPages = startPage + 1
        var items: List<History>
        do {
            val requestedPage = currentPage + 1
            val response = interactor.getPage(page = requestedPage)
            check(response.pagination.current == requestedPage) {
                "History pagination did not match the requested next page"
            }
            items = traversal.filterFirstOccurrences(response.items)
            currentPage = response.pagination.current
            totalPages = response.pagination.total
        } while (items.isEmpty() && currentPage < totalPages)
        return HistoryNextPageResult(
            items = items,
            currentPage = currentPage,
            totalPages = totalPages,
        )
    }

    private fun handleFirstPageFailure(
        operationId: Long,
        error: ErrorEntity,
    ) {
        val failed = runtime.failFirstPage(operationId) ?: return
        if (failed.stableHistory.isEmpty()) {
            setGeneralError(error)
            return
        }
        replace(
            failed.stableHistory,
            rawItemForKey(failed.focusedKey, failed.stableHistory),
        )
        showMessage(error.message)
    }

    private fun runDeferredRefreshIfReady() {
        if (stateValue !is HistoryViewState.Content) return
        if (runtime.beginDeferredRefresh()) {
            enqueueRefresh()
        }
    }

    private fun runQueuedDeletionIfReady() {
        val state = runtime.snapshot()
        if (state.operation != HistoryOperation.Idle) return
        val queued = state.queuedDeletion ?: return
        val latestContent = stateValue as? HistoryViewState.Content
        val currentItem = latestContent
            ?.items
            ?.firstOrNull { it.rowKey == queued.rowKey }
        val reconciliation = currentItem
            ?.let { createReconciliationContext(latestContent, it) }
        if (currentItem == null || reconciliation == null) {
            if (runtime.dropQueuedDeletion(queued.rowKey)) {
                updateDeleteExactMediaAvailability()
                runDeferredRefreshIfReady()
            }
            return
        }
        startDeletion(currentItem, reconciliation)
    }

    private fun createReconciliationContext(
        content: HistoryViewState.Content,
        item: HistoryItemUIState,
    ): HistoryReconciliationContext? {
        val oldIndex = content.items.indexOfFirst { it.rowKey == item.rowKey }
        if (oldIndex < 0) return null
        return HistoryReconciliationContext(
            oldIndex = oldIndex,
            nextKey = content.items.getOrNull(oldIndex + 1)?.rowKey,
            previousKey = content.items.getOrNull(oldIndex - 1)?.rowKey,
            loadedPageDepth = runtime.snapshot().currentPage.coerceAtLeast(FIRST_PAGE),
        )
    }

    private fun updateDeleteExactMediaAvailability() {
        synchronized(contentPublicationLock) {
            val content = stateValue as? HistoryViewState.Content ?: return
            val isDeleteExactMediaAvailable = runtime.isDeleteAvailable()
            testAfterDeleteAvailabilityRead?.invoke()
            updateViewState(
                content.copy(
                    isDeleteExactMediaAvailable = isDeleteExactMediaAvailable,
                ),
            )
        }
    }

    private fun mergeStableHistory(
        oldItems: List<History>,
        newItems: List<History>,
    ): List<History> {
        val merged = oldItems.toMutableList()
        newItems.forEach { newItem ->
            val index = merged.indexOfFirst { oldItem ->
                HistoryRowComparator.isItemTheSame(oldItem, newItem)
            }
            if (index >= 0) {
                merged[index] = newItem
            } else {
                merged += newItem
            }
        }
        return merged
    }

}

private fun Screens.historyDetails(item: HistoryItemUIState): PuberScreen {
    return when (val target = item.playbackTarget) {
        is HistoryPlaybackTarget.Episode -> details(
            itemId = item.itemId,
            initialEpisode = DetailsEpisodeTarget(
                seasonNumber = target.seasonNumber,
                episodeNumber = target.episodeNumber,
            ),
        )
        is HistoryPlaybackTarget.Movie,
        HistoryPlaybackTarget.Details -> details(item.itemId)
    }
}
