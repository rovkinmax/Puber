package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val LOAD_MORE_THRESHOLD = 12

@OptIn(FlowPreview::class)
@Composable
fun LoadMoreHandler(
    lazyListState: LazyListState,
    loadMoreAtEnd: () -> Unit,
    loadMoreAtStart: () -> Unit = {},
    loadMoreThreshold: Int = LOAD_MORE_THRESHOLD,
    loadMoreDebounce: Long = 0,
) {
    val shouldLoadMoreAtEnd by remember {
        derivedStateOf {
            val totalItemsCount = lazyListState.layoutInfo.totalItemsCount

            val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false

            lastVisibleItem.index > (totalItemsCount - loadMoreThreshold - 1)
        }
    }

    val shouldLoadMoreAtStart by remember {
        derivedStateOf {
            val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
                ?: return@derivedStateOf false

            firstVisibleItem.index < (loadMoreThreshold - 1)
        }
    }

    LaunchedEffect(shouldLoadMoreAtStart) {
        snapshotFlow { shouldLoadMoreAtStart }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .debounce(loadMoreDebounce)
            .collect { loadMoreAtStart() }
    }

    LaunchedEffect(shouldLoadMoreAtEnd) {
        snapshotFlow { shouldLoadMoreAtEnd }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .debounce(loadMoreDebounce)
            .collect { loadMoreAtEnd() }
    }
}