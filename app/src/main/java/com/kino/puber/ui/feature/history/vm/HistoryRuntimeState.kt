package com.kino.puber.ui.feature.history.vm

import com.kino.puber.data.api.models.History
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState

internal data class HistoryRuntimeState(
    val currentPage: Int = 0,
    val isFullDataNext: Boolean = false,
    val hasCompletedInitialLoad: Boolean = false,
    val stableHistory: List<History> = emptyList(),
    val operation: HistoryOperation = HistoryOperation.Idle,
    val focusedKey: HistoryRowKey? = null,
    val requestedFocusKey: HistoryRowKey? = null,
    val openMenuKey: HistoryRowKey? = null,
    val queuedDeletion: HistoryQueuedDeletion? = null,
    val refreshAfterPendingOperation: Boolean = false,
    val nextPageErrorMessage: String? = null,
    val pageAttemptRevision: Long = 0L,
    val operationSequence: Long = 0L,
) {
    fun nextOperationId(): Long = operationSequence + 1L
}

internal sealed interface HistoryOperation {
    data object Idle : HistoryOperation

    data class RefreshRequested(
        val operationId: Long,
    ) : HistoryOperation

    data class RestartRequested(
        val operationId: Long,
    ) : HistoryOperation

    data class LoadingFirstPage(
        val operationId: Long,
        val retainsContent: Boolean,
    ) : HistoryOperation

    data class LoadingNextPage(
        val operationId: Long,
    ) : HistoryOperation

    data class Deleting(
        val operationId: Long,
        val item: HistoryItemUIState,
        val reconciliation: HistoryReconciliationContext,
    ) : HistoryOperation

    data class Reconciling(
        val operationId: Long,
        val reconciliation: HistoryReconciliationContext,
    ) : HistoryOperation

    data class ReconciliationFailed(
        val reconciliation: HistoryReconciliationContext,
        val message: String,
    ) : HistoryOperation

    data class AwaitingPublication(
        val operationId: Long,
        val kind: HistoryPublicationKind,
    ) : HistoryOperation
}

internal enum class HistoryPublicationKind {
    FIRST_PAGE,
    FIRST_PAGE_ERROR,
    REFRESH_ERROR,
    NEXT_PAGE,
    NEXT_PAGE_ERROR,
    RECONCILIATION,
}

internal data class HistoryReconciliationContext(
    val oldIndex: Int,
    val nextKey: HistoryRowKey?,
    val previousKey: HistoryRowKey?,
    val loadedPageDepth: Int,
)

internal data class HistoryQueuedDeletion(
    val rowKey: HistoryRowKey,
)

internal class HistoryRuntimeStore(
    initialState: HistoryRuntimeState = HistoryRuntimeState(),
) {
    private var state = initialState

    @Synchronized
    fun snapshot(): HistoryRuntimeState = state

    @Synchronized
    fun update(transform: (HistoryRuntimeState) -> HistoryRuntimeState): HistoryRuntimeState {
        state = transform(state)
        return state
    }

    @Synchronized
    fun <T> reduce(
        transform: (HistoryRuntimeState) -> Pair<HistoryRuntimeState, T>,
    ): T {
        val (nextState, result) = transform(state)
        state = nextState
        return result
    }
}

internal data class HistoryFirstPageRequest(
    val operationId: Long,
    val loadedPageDepth: Int,
)

internal data class HistoryDeletionStart(
    val operation: HistoryOperation.Deleting? = null,
    val queued: Boolean = false,
)

internal data class HistoryReconciliationStart(
    val operationId: Long,
    val retained: List<History>,
)

internal data class HistoryPageDepthResult(
    val items: List<History>,
    val currentPage: Int,
    val totalPages: Int,
)

internal data class HistoryNextPageResult(
    val items: List<History>,
    val currentPage: Int,
    val totalPages: Int,
)

internal fun HistoryRuntimeStore.beginFirstPage(): HistoryFirstPageRequest? {
    return reduce { state ->
        val operationId = when (val operation = state.operation) {
            HistoryOperation.Idle -> state.nextOperationId()
            is HistoryOperation.RefreshRequested -> operation.operationId
            is HistoryOperation.RestartRequested -> operation.operationId
            else -> null
        }
        if (operationId == null) {
            state to null
        } else {
            val nextState = state.copy(
                operation = HistoryOperation.LoadingFirstPage(
                    operationId = operationId,
                    retainsContent = state.stableHistory.isNotEmpty(),
                ),
                operationSequence = maxOf(state.operationSequence, operationId),
            )
            nextState to HistoryFirstPageRequest(
                operationId = operationId,
                loadedPageDepth = state.currentPage.coerceAtLeast(FIRST_PAGE),
            )
        }
    }
}

internal fun HistoryRuntimeStore.acceptFirstPage(
    operationId: Long,
    result: HistoryPageDepthResult,
): HistoryRuntimeState? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.LoadingFirstPage
        if (operation?.operationId != operationId) {
            state to null
        } else {
            val nextState = state.copy(
                currentPage = result.currentPage,
                isFullDataNext = result.currentPage >= result.totalPages,
                hasCompletedInitialLoad = true,
                stableHistory = result.items,
                operation = HistoryOperation.AwaitingPublication(
                    operationId = operationId,
                    kind = HistoryPublicationKind.FIRST_PAGE,
                ),
                nextPageErrorMessage = null,
                pageAttemptRevision = state.pageAttemptRevision + 1L,
            )
            nextState to nextState
        }
    }
}

internal fun HistoryRuntimeStore.cancelFirstPage(operationId: Long) {
    update { state ->
        val operation = state.operation as? HistoryOperation.LoadingFirstPage
        if (operation?.operationId == operationId) {
            state.copy(operation = HistoryOperation.Idle)
        } else {
            state
        }
    }
}

internal fun HistoryRuntimeStore.currentNextPageRequest(): HistoryOperation.LoadingNextPage? {
    return snapshot().operation as? HistoryOperation.LoadingNextPage
}

internal fun HistoryRuntimeStore.acceptNextPage(
    operationId: Long,
    result: HistoryNextPageResult,
    merge: (List<History>, List<History>) -> List<History>,
): Boolean {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.LoadingNextPage
        if (operation?.operationId != operationId) {
            state to false
        } else {
            state.copy(
                currentPage = result.currentPage,
                isFullDataNext = result.currentPage >= result.totalPages,
                stableHistory = merge(state.stableHistory, result.items),
                operation = HistoryOperation.AwaitingPublication(
                    operationId = operationId,
                    kind = HistoryPublicationKind.NEXT_PAGE,
                ),
                nextPageErrorMessage = null,
                pageAttemptRevision = state.pageAttemptRevision + 1L,
            ) to true
        }
    }
}

internal fun HistoryRuntimeStore.cancelNextPage(operationId: Long) {
    update { state ->
        val operation = state.operation as? HistoryOperation.LoadingNextPage
        if (operation?.operationId == operationId) {
            state.copy(operation = HistoryOperation.Idle)
        } else {
            state
        }
    }
}

internal fun HistoryRuntimeStore.failNextPage(
    operationId: Long,
    message: String,
): Boolean {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.LoadingNextPage
        if (operation?.operationId != operationId) {
            state to false
        } else {
            state.copy(
                operation = HistoryOperation.AwaitingPublication(
                    operationId = operationId,
                    kind = HistoryPublicationKind.NEXT_PAGE_ERROR,
                ),
                nextPageErrorMessage = message,
                pageAttemptRevision = state.pageAttemptRevision + 1L,
            ) to true
        }
    }
}

internal fun HistoryRuntimeStore.resetAfterUnhandledError(): HistoryRuntimeState {
    return update {
        it.copy(
            operation = HistoryOperation.Idle,
            nextPageErrorMessage = null,
        )
    }
}

internal fun HistoryRuntimeStore.completePublication(
    vararg kinds: HistoryPublicationKind,
) {
    update { state ->
        val publication = state.operation as? HistoryOperation.AwaitingPublication
        if (publication?.kind in kinds) {
            state.copy(operation = HistoryOperation.Idle)
        } else {
            state
        }
    }
}

internal fun HistoryRuntimeStore.completeEmptyPublication(): Boolean {
    return reduce { state ->
        val publication = state.operation as? HistoryOperation.AwaitingPublication
        val accepted = publication?.kind == HistoryPublicationKind.FIRST_PAGE
        if (accepted) {
            state.copy(
                operation = HistoryOperation.Idle,
                refreshAfterPendingOperation = false,
                queuedDeletion = null,
                focusedKey = null,
                requestedFocusKey = null,
                openMenuKey = null,
            ) to true
        } else {
            state to false
        }
    }
}

internal fun HistoryRuntimeStore.beginNextPage(): Boolean {
    return reduce { state ->
        when {
            state.operation != HistoryOperation.Idle -> state to false
            state.queuedDeletion != null -> state to false
            state.refreshAfterPendingOperation -> state to false
            state.isFullDataNext -> state to false
            else -> {
                val operationId = state.nextOperationId()
                state.copy(
                    operation = HistoryOperation.LoadingNextPage(operationId),
                    nextPageErrorMessage = null,
                    operationSequence = operationId,
                ) to true
            }
        }
    }
}

internal fun HistoryRuntimeStore.beginRefresh(deferIfBusy: Boolean): Boolean {
    return reduce { state ->
        if (state.operation == HistoryOperation.Idle && state.queuedDeletion == null) {
            val operationId = state.nextOperationId()
            state.copy(
                operation = HistoryOperation.RefreshRequested(operationId),
                nextPageErrorMessage = null,
                operationSequence = operationId,
            ) to true
        } else {
            val nextState = if (deferIfBusy) {
                state.copy(refreshAfterPendingOperation = true)
            } else {
                state
            }
            nextState to false
        }
    }
}

internal fun HistoryRuntimeStore.beginRestart(): Boolean {
    return reduce { state ->
        if (state.operation == HistoryOperation.Idle && state.queuedDeletion == null) {
            val operationId = state.nextOperationId()
            state.copy(
                operation = HistoryOperation.RestartRequested(operationId),
                nextPageErrorMessage = null,
                operationSequence = operationId,
            ) to true
        } else {
            state to false
        }
    }
}

internal fun HistoryRuntimeStore.focus(item: HistoryItemUIState) {
    update {
        it.copy(
            focusedKey = item.rowKey,
            requestedFocusKey = null,
        )
    }
}

internal fun HistoryRuntimeStore.openMenu(item: HistoryItemUIState): Boolean {
    return reduce { state ->
        if (state.isDeletionFlowActive()) {
            state to false
        } else {
            state.copy(
                focusedKey = item.rowKey,
                openMenuKey = item.rowKey,
            ) to true
        }
    }
}

internal fun HistoryRuntimeStore.dismissMenu() {
    update { it.copy(openMenuKey = null) }
}

internal fun HistoryRuntimeStore.beginDeletion(
    item: HistoryItemUIState,
    reconciliation: HistoryReconciliationContext,
    queueIfBusy: Boolean,
): HistoryDeletionStart {
    return reduce { state ->
        when {
            state.operation == HistoryOperation.Idle -> {
                val operationId = state.nextOperationId()
                val operation = HistoryOperation.Deleting(
                    operationId = operationId,
                    item = item,
                    reconciliation = reconciliation,
                )
                state.copy(
                    operation = operation,
                    focusedKey = item.rowKey,
                    openMenuKey = null,
                    queuedDeletion = null,
                    nextPageErrorMessage = null,
                    operationSequence = operationId,
                ) to HistoryDeletionStart(operation = operation)
            }
            queueIfBusy &&
                state.queuedDeletion == null &&
                state.operation.canQueueDeletion() ->
                state.copy(
                    focusedKey = item.rowKey,
                    openMenuKey = null,
                    queuedDeletion = HistoryQueuedDeletion(item.rowKey),
                ) to HistoryDeletionStart(queued = true)
            else -> state to HistoryDeletionStart()
        }
    }
}

internal fun HistoryRuntimeStore.cancelDeletion(operationId: Long) {
    update { state ->
        val operation = state.operation as? HistoryOperation.Deleting
        if (operation?.operationId == operationId) {
            state.copy(operation = HistoryOperation.Idle)
        } else {
            state
        }
    }
}

internal fun HistoryRuntimeStore.failDeletion(operationId: Long): HistoryRuntimeState? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.Deleting
        if (operation?.operationId == operationId) {
            val nextState = state.copy(operation = HistoryOperation.Idle)
            nextState to nextState
        } else {
            state to null
        }
    }
}

internal fun HistoryRuntimeStore.beginReconciliation(
    deletionId: Long,
    reconciliation: HistoryReconciliationContext,
    retained: List<History>,
    requestedFocusKey: HistoryRowKey?,
): HistoryReconciliationStart? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.Deleting
        if (operation?.operationId != deletionId) {
            state to null
        } else {
            val operationId = state.nextOperationId()
            val nextState = state.copy(
                stableHistory = retained,
                operation = HistoryOperation.Reconciling(
                    operationId = operationId,
                    reconciliation = reconciliation,
                ),
                requestedFocusKey = requestedFocusKey,
                openMenuKey = null,
                operationSequence = operationId,
            )
            nextState to HistoryReconciliationStart(
                operationId = operationId,
                retained = retained,
            )
        }
    }
}

internal fun HistoryRuntimeStore.beginReconciliationRetry(
    reconciliation: HistoryReconciliationContext,
): Long? {
    return reduce { state ->
        val failed = state.operation as? HistoryOperation.ReconciliationFailed
        if (failed?.reconciliation != reconciliation) {
            state to null
        } else {
            val operationId = state.nextOperationId()
            state.copy(
                operation = HistoryOperation.Reconciling(
                    operationId = operationId,
                    reconciliation = reconciliation,
                ),
                operationSequence = operationId,
            ) to operationId
        }
    }
}

internal fun HistoryRuntimeStore.acceptReconciliation(
    operationId: Long,
    reconciliation: HistoryReconciliationContext,
    result: HistoryPageDepthResult,
    requestedFocusKey: HistoryRowKey?,
): HistoryRuntimeState? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.Reconciling
        if (operation?.operationId != operationId) {
            state to null
        } else {
            val nextState = state.copy(
                currentPage = result.currentPage,
                isFullDataNext = result.currentPage >= result.totalPages,
                stableHistory = result.items,
                operation = HistoryOperation.AwaitingPublication(
                    operationId = operationId,
                    kind = HistoryPublicationKind.RECONCILIATION,
                ),
                requestedFocusKey = requestedFocusKey,
                nextPageErrorMessage = null,
                pageAttemptRevision = state.pageAttemptRevision + 1L,
            )
            nextState to nextState
        }
    }
}

private const val FIRST_PAGE = 1

internal fun HistoryRuntimeStore.failReconciliation(
    operationId: Long,
    reconciliation: HistoryReconciliationContext,
    message: String,
    requestedFocusKey: HistoryRowKey?,
): HistoryRuntimeState? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.Reconciling
        if (operation?.operationId != operationId) {
            state to null
        } else {
            val nextState = state.copy(
                operation = HistoryOperation.ReconciliationFailed(
                    reconciliation = reconciliation,
                    message = message,
                ),
                requestedFocusKey = requestedFocusKey,
            )
            nextState to nextState
        }
    }
}

internal fun HistoryRuntimeStore.failFirstPage(
    operationId: Long,
): HistoryRuntimeState? {
    return reduce { state ->
        val operation = state.operation as? HistoryOperation.LoadingFirstPage
        if (operation?.operationId != operationId) {
            state to null
        } else {
            val publicationKind = if (state.stableHistory.isEmpty()) {
                HistoryPublicationKind.FIRST_PAGE_ERROR
            } else {
                HistoryPublicationKind.REFRESH_ERROR
            }
            val nextState = state.copy(
                hasCompletedInitialLoad = true,
                operation = HistoryOperation.AwaitingPublication(
                    operationId = operationId,
                    kind = publicationKind,
                ),
                pageAttemptRevision = if (state.stableHistory.isEmpty()) {
                    state.pageAttemptRevision
                } else {
                    state.pageAttemptRevision + 1L
                },
            )
            nextState to nextState
        }
    }
}
