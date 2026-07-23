package com.kino.puber.ui.feature.contentlist.vm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class ContentListRefreshCoordinator {
    private val mutableRefreshGeneration = MutableStateFlow(0L)

    fun refreshRequests(): Flow<Unit> {
        // Capture before collection starts so a refresh cannot disappear in the coroutine launch gap.
        val observedGeneration = mutableRefreshGeneration.value
        return mutableRefreshGeneration
            .filter { it != observedGeneration }
            .map { Unit }
    }

    fun requestRefresh() {
        mutableRefreshGeneration.update { it + 1 }
    }
}
