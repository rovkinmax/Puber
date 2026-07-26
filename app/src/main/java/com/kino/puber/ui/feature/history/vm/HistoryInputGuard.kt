package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.history.model.HistoryAction

internal fun UIAction.isBlockedDuringDeletionFlow(): Boolean {
    return this is CommonAction.ItemSelected<*> ||
        this is HistoryAction.OpenContextMenu ||
        this is HistoryAction.Play ||
        this is HistoryAction.OpenDetails ||
        this is HistoryAction.DeleteExactMedia
}
