package com.kino.puber.ui.feature.history.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.history.HistoryRowKey

@Immutable
internal sealed class HistoryViewState {
    data object Loading : HistoryViewState()

    data object Empty : HistoryViewState()

    data class Error(
        val message: String,
    ) : HistoryViewState()

    data class Content(
        val items: List<HistoryItemUIState>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMorePages: Boolean = false,
        val pageAttemptRevision: Long = 0L,
        val nextPageErrorMessage: String? = null,
        val isDeleteExactMediaAvailable: Boolean = true,
        val openMenuKey: HistoryRowKey? = null,
        val deletingKeys: Set<HistoryRowKey> = emptySet(),
        val focusKey: HistoryRowKey? = null,
        val reloadErrorMessage: String? = null,
    ) : HistoryViewState()
}
