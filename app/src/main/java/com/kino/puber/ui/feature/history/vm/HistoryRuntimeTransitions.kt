package com.kino.puber.ui.feature.history.vm

import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState

internal fun resolveHistoryFocusKey(
    items: List<HistoryItemUIState>,
    reconciliation: HistoryReconciliationContext,
): HistoryRowKey? {
    val keys = items.map(HistoryItemUIState::rowKey)
    return reconciliation.nextKey?.takeIf(keys::contains)
        ?: reconciliation.previousKey?.takeIf(keys::contains)
        ?: keys.getOrNull(reconciliation.oldIndex.coerceAtMost(keys.lastIndex))
}

internal fun HistoryOperation.canQueueDeletion(): Boolean {
    return this is HistoryOperation.RefreshRequested ||
        this is HistoryOperation.LoadingFirstPage ||
        this is HistoryOperation.LoadingNextPage ||
        this is HistoryOperation.AwaitingPublication
}

internal fun HistoryRuntimeStore.isDeleteAvailable(): Boolean {
    val state = snapshot()
    return state.operation == HistoryOperation.Idle &&
        state.queuedDeletion == null
}

internal fun HistoryOperation.isDeletionFlowActive(): Boolean {
    return when (this) {
        is HistoryOperation.Deleting,
        is HistoryOperation.Reconciling,
        is HistoryOperation.ReconciliationFailed -> true
        is HistoryOperation.AwaitingPublication ->
            kind == HistoryPublicationKind.RECONCILIATION
        else -> false
    }
}

internal fun HistoryRuntimeState.isDeletionFlowActive(): Boolean {
    return queuedDeletion != null || operation.isDeletionFlowActive()
}

internal fun HistoryRuntimeStore.isDeletionFlowActive(): Boolean {
    return snapshot().isDeletionFlowActive()
}

internal fun HistoryRuntimeStore.hasPendingEmptyPublication(): Boolean {
    val publication = snapshot().operation as? HistoryOperation.AwaitingPublication
    return publication?.kind == HistoryPublicationKind.FIRST_PAGE
}

internal fun HistoryRuntimeStore.beginDeferredRefresh(): Boolean {
    return reduce { state ->
        if (
            state.refreshAfterPendingOperation &&
            state.operation == HistoryOperation.Idle &&
            state.queuedDeletion == null
        ) {
            val operationId = state.nextOperationId()
            state.copy(
                operation = HistoryOperation.RefreshRequested(operationId),
                refreshAfterPendingOperation = false,
                nextPageErrorMessage = null,
                operationSequence = operationId,
            ) to true
        } else {
            state to false
        }
    }
}

internal fun HistoryRuntimeStore.dropQueuedDeletion(rowKey: HistoryRowKey): Boolean {
    return reduce { state ->
        val queued = state.queuedDeletion
        if (
            state.operation == HistoryOperation.Idle &&
            queued?.rowKey == rowKey
        ) {
            state.copy(queuedDeletion = null) to true
        } else {
            state to false
        }
    }
}

internal fun HistoryRuntimeStore.completeReconciliationPublication(
    operationId: Long,
): HistoryRuntimeState? {
    return reduce { state ->
        val publication = state.operation as? HistoryOperation.AwaitingPublication
        if (
            publication?.operationId != operationId ||
            publication.kind != HistoryPublicationKind.RECONCILIATION
        ) {
            state to null
        } else {
            val isEmpty = state.stableHistory.isEmpty()
            val nextState = state.copy(
                operation = HistoryOperation.Idle,
                focusedKey = state.focusedKey.takeUnless { isEmpty },
                requestedFocusKey = state.requestedFocusKey.takeUnless { isEmpty },
                openMenuKey = state.openMenuKey.takeUnless { isEmpty },
                queuedDeletion = state.queuedDeletion.takeUnless { isEmpty },
                refreshAfterPendingOperation = state.refreshAfterPendingOperation && !isEmpty,
            )
            nextState to nextState
        }
    }
}

internal fun HistoryRuntimeStore.prepareContentPublication(
    availableKeys: Set<HistoryRowKey>,
    paginatorFocusKey: HistoryRowKey?,
): HistoryRuntimeState {
    return update { state ->
        val nextState = state.copy(
            requestedFocusKey = paginatorFocusKey ?: state.requestedFocusKey,
            openMenuKey = state.openMenuKey?.takeIf(availableKeys::contains),
        )
        nextState
    }
}
