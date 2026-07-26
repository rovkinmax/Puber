package com.kino.puber.ui.feature.history.vm

import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryViewState

internal fun HistoryRuntimeState.toContentViewState(
    items: List<HistoryItemUIState>,
    availableKeys: Set<HistoryRowKey>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
): HistoryViewState.Content {
    val deletingKeys = (operation as? HistoryOperation.Deleting)
        ?.item
        ?.rowKey
        ?.takeIf(availableKeys::contains)
        ?.let(::setOf)
        .orEmpty()
    val focusKey = (requestedFocusKey ?: focusedKey)
        ?.takeIf(availableKeys::contains)
    val operationIsRefreshing = operation is HistoryOperation.RefreshRequested ||
        operation is HistoryOperation.LoadingFirstPage && operation.retainsContent
    val reloadErrorMessage =
        (operation as? HistoryOperation.ReconciliationFailed)?.message
    return HistoryViewState.Content(
        items = items,
        isRefreshing = isRefreshing || operationIsRefreshing,
        isLoadingMore = isLoadingMore,
        hasMorePages = !isFullDataNext,
        pageAttemptRevision = pageAttemptRevision,
        nextPageErrorMessage = nextPageErrorMessage,
        isDeleteExactMediaAvailable =
            operation == HistoryOperation.Idle && queuedDeletion == null,
        openMenuKey = openMenuKey,
        deletingKeys = deletingKeys,
        focusKey = focusKey,
        reloadErrorMessage = reloadErrorMessage,
    )
}
