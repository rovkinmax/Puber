package com.kino.puber.ui.feature.history.model

import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.PlayerStartMode

internal sealed interface HistoryAction : UIAction {
    data class OpenContextMenu(
        val item: HistoryItemUIState,
    ) : HistoryAction

    data object DismissContextMenu : HistoryAction

    data class Play(
        val item: HistoryItemUIState,
        val startMode: PlayerStartMode,
    ) : HistoryAction

    data class OpenDetails(
        val item: HistoryItemUIState,
    ) : HistoryAction

    data class DeleteExactMedia(
        val item: HistoryItemUIState,
    ) : HistoryAction

    data object RetryReconciliation : HistoryAction
}
