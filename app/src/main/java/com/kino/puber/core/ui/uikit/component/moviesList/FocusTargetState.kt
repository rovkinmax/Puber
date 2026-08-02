package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.LazyListState
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorFocusRestored
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorRestoreCompletion
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import com.kino.puber.core.ui.navigation.component.RootAnchorRestoreCompletion
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive

internal class ReconciledItemFocusState(
    val targetItemId: Int?,
    val focusRequester: FocusRequester,
    val rowHasFocusRef: BooleanArray,
    val onItemFocused: (Int) -> Unit,
)

@Composable
internal fun rememberReconciledItemFocus(
    rowKey: String,
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    initialFocusedItemId: Int? = null,
    requestAfterFrame: Boolean = false,
    onRowEmpty: () -> Unit,
): ReconciledItemFocusState {
    val focusedItemId = rememberSaveable(rowKey) {
        mutableStateOf(initialFocusedItemId ?: items.firstOrNull()?.id)
    }
    val previousItems = remember(rowKey) { mutableStateOf(items) }
    val pendingFocusItemId = remember(rowKey) { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }
    val rowHasFocusRef = remember { booleanArrayOf(false) }
    val contentFocusActive = LocalContentFocusActive.current
    val onRootFocusRestored = LocalRootAnchorFocusRestored.current
    val rootAnchorRestoreCompletion = LocalRootAnchorRestoreCompletion.current
    val screenKey = LocalScreenKey.current
    val targetItemId = resolveFocusedItemId(
        previousItems = previousItems.value,
        updatedItems = items,
        focusedItemId = focusedItemId.value,
    )

    ReconcileInitialItemFocusEffect(
        initialFocusedItemId = initialFocusedItemId,
        items = items,
        focusedItemId = focusedItemId,
        pendingFocusItemId = pendingFocusItemId,
    )
    ReconcilePublishedItemsEffect(
        items = items,
        isTargetRow = isTargetRow,
        focusedItemId = focusedItemId,
        previousItems = previousItems,
        pendingFocusItemId = pendingFocusItemId,
        onRowEmpty = onRowEmpty,
    )
    RequestReconciledItemFocusEffects(
        isTargetRow = isTargetRow,
        targetItemId = targetItemId,
        focusRequester = focusRequester,
        rowHasFocusRef = rowHasFocusRef,
        pendingFocusItemId = pendingFocusItemId,
        requestAfterFrame = requestAfterFrame,
        contentFocusActive = contentFocusActive,
        rootAnchorRestoreCompletion = rootAnchorRestoreCompletion,
        screenKey = screenKey,
    )

    return ReconciledItemFocusState(
        targetItemId = targetItemId,
        focusRequester = focusRequester,
        rowHasFocusRef = rowHasFocusRef,
        onItemFocused = { itemId ->
            focusedItemId.value = itemId
            if (isTargetRow && itemId == targetItemId) {
                onRootFocusRestored()
            }
        },
    )
}

@Composable
private fun ReconcileInitialItemFocusEffect(
    initialFocusedItemId: Int?,
    items: List<VideoItemUIState>,
    focusedItemId: MutableState<Int?>,
    pendingFocusItemId: MutableState<Int?>,
) {
    LaunchedEffect(initialFocusedItemId) {
        if (
            initialFocusedItemId != null &&
            initialFocusedItemId != focusedItemId.value &&
            items.any { it.id == initialFocusedItemId }
        ) {
            focusedItemId.value = initialFocusedItemId
            pendingFocusItemId.value = initialFocusedItemId
        }
    }
}

@Composable
private fun ReconcilePublishedItemsEffect(
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    focusedItemId: MutableState<Int?>,
    previousItems: MutableState<List<VideoItemUIState>>,
    pendingFocusItemId: MutableState<Int?>,
    onRowEmpty: () -> Unit,
) {
    LaunchedEffect(items) {
        val nextFocusedItemId = resolveFocusedItemId(
            previousItems = previousItems.value,
            updatedItems = items,
            focusedItemId = focusedItemId.value,
        )
        if (nextFocusedItemId != focusedItemId.value) {
            focusedItemId.value = nextFocusedItemId
            pendingFocusItemId.value = nextFocusedItemId
        }
        previousItems.value = items
        if (items.isEmpty() && isTargetRow) {
            onRowEmpty()
        }
    }
}

@Composable
private fun RequestReconciledItemFocusEffects(
    isTargetRow: Boolean,
    targetItemId: Int?,
    focusRequester: FocusRequester,
    rowHasFocusRef: BooleanArray,
    pendingFocusItemId: MutableState<Int?>,
    requestAfterFrame: Boolean,
    contentFocusActive: Boolean,
    rootAnchorRestoreCompletion: RootAnchorRestoreCompletion,
    screenKey: String?,
) {
    LaunchedEffect(isTargetRow, contentFocusActive) {
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (targetCanReceiveFocus && !rowHasFocusRef[0]) {
            focusRequester.requestAfterComposition(requestAfterFrame)
        }
    }
    LaunchedEffect(pendingFocusItemId.value, contentFocusActive) {
        if (isTargetRow && contentFocusActive && pendingFocusItemId.value != null) {
            focusRequester.requestAfterComposition(requestAfterFrame)
            pendingFocusItemId.value = null
        }
    }
    LaunchedEffect(rootAnchorRestoreCompletion.version) {
        val matchingCompletedRestore =
            rootAnchorRestoreCompletion.screenKey == screenKey &&
                rootAnchorRestoreCompletion.version > 0
        val targetCanReceiveFocus = isTargetRow && contentFocusActive && targetItemId != null
        if (matchingCompletedRestore && targetCanReceiveFocus) {
            focusRequester.requestAfterAnchorRestore()
        }
    }
}

private suspend fun FocusRequester.requestAfterAnchorRestore() {
    repeat(ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS) {
        withFrameNanos { }
        if (requestFocus()) return
    }
}

private suspend fun FocusRequester.requestAfterComposition(awaitFrame: Boolean) {
    if (awaitFrame) {
        withFrameNanos { }
    }
    requestFocus()
}

private const val ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS = 3

internal class ReconciledRowFocusState(
    val focusedRowKey: String?,
    val onRowFocused: (String) -> Unit,
    val onRowEmpty: (Int) -> Unit,
)

@Composable
internal fun rememberReconciledRowFocus(
    rows: List<FocusableRow>,
    initialRowKey: String? = null,
    resetKey: Any? = Unit,
): ReconciledRowFocusState {
    val focusedRowKey = rememberSaveable(resetKey) { mutableStateOf(initialRowKey) }
    val previousRows = remember { mutableStateOf(rows) }
    val resolvedRowKey = resolveFocusedRowKey(
        previousRows = previousRows.value,
        updatedRows = rows,
        focusedRowKey = focusedRowKey.value,
    )
    LaunchedEffect(rows) {
        focusedRowKey.value = resolvedRowKey
        previousRows.value = rows
    }
    return ReconciledRowFocusState(
        focusedRowKey = resolvedRowKey,
        onRowFocused = { rowKey -> focusedRowKey.value = rowKey },
        onRowEmpty = { rowIndex ->
            focusedRowKey.value = nearestNonEmptyRowKey(
                rows = rows,
                emptyRowIndex = rowIndex,
            )
        },
    )
}

internal class VideoGridFocusState(
    val rows: List<FocusableRow>,
    val rowFocus: ReconciledRowFocusState,
)

@Composable
internal fun rememberVideoGridFocusState(
    list: List<VideoGridItemUIState>,
    initialFocusedItemId: Int?,
    lazyListState: LazyListState,
): VideoGridFocusState {
    val initialColumnIndex = remember(list, initialFocusedItemId) {
        list.indexOfFirst { gridItem ->
            gridItem is VideoGridItemUIState.Items &&
                gridItem.items.any { it.id == initialFocusedItemId }
        }
    }
    val initialRowKey = remember(list, initialColumnIndex) {
        (list.getOrNull(initialColumnIndex) as? VideoGridItemUIState.Items)?.rowKey
    }
    val rows = remember(list) {
        list.filterIsInstance<VideoGridItemUIState.Items>()
            .map { row -> FocusableRow(row.rowKey, row.items.size) }
    }
    val rowFocus = rememberReconciledRowFocus(
        rows = rows,
        initialRowKey = initialRowKey,
        resetKey = initialFocusedItemId,
    )
    LaunchedEffect(initialColumnIndex) {
        if (initialColumnIndex >= 0) {
            lazyListState.scrollToItem(initialColumnIndex)
        }
    }
    return VideoGridFocusState(rows = rows, rowFocus = rowFocus)
}
