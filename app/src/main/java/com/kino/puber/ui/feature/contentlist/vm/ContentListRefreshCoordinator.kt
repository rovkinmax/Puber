package com.kino.puber.ui.feature.contentlist.vm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class ContentListRefreshCoordinator {
    private val mutableRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val refreshRequests: Flow<Unit> = mutableRefreshRequests.asSharedFlow()

    fun requestRefresh() {
        mutableRefreshRequests.tryEmit(Unit)
    }
}
