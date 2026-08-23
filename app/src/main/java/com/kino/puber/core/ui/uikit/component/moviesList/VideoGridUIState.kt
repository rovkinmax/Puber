package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.DpadScrollAxis
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoGridUIState(
    val list: List<VideoGridItemUIState>,
)

@Immutable
sealed class VideoGridItemUIState {
    data class Title(val title: String) : VideoGridItemUIState()
    data class Items(
        val items: List<VideoItemUIState>,
        val rowKey: String,
    ) : VideoGridItemUIState()
}

@Composable
fun VideoGrid(
    modifier: Modifier = Modifier,
    state: VideoGridUIState,
    onItemClick: (VideoItemUIState) -> Unit = {},
    onItemFocused: (VideoItemUIState) -> Unit = {},
    onItemContextMenu: ((VideoItemUIState) -> Unit)? = null,
    enableTopSideGradient: Boolean = true,
    initialFocusedItemId: Int? = null,
) {
    val lazyListState = rememberLazyListState()
    val gridFocus = rememberVideoGridFocusState(
        list = state.list,
        initialFocusedItemId = initialFocusedItemId,
        lazyListState = lazyListState,
    )

    val showTopGradient by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset > 0 } }

    PositionFocusedItemInLazyLayout {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = PuberTheme.Defaults.VideoItemHeight),
            ) {
                itemsIndexed(state.list, key = { _, item ->
                    when (item) {
                        is VideoGridItemUIState.Title -> "title_${item.title}"
                        is VideoGridItemUIState.Items -> "items_${item.rowKey}"
                    }
                }) { _, columnItem ->
                    when (columnItem) {

                        is VideoGridItemUIState.Title -> Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            text = columnItem.title,
                            style = MaterialTheme.typography.titleLarge,
                        )

                        is VideoGridItemUIState.Items -> VideoGridItems(
                            items = columnItem,
                            isTargetRow = columnItem.rowKey == gridFocus.rowFocus.focusedRowKey,
                            initialFocusedItemId = initialFocusedItemId?.takeIf { itemId ->
                                columnItem.items.any { it.id == itemId }
                            },
                            onItemClick = onItemClick,
                            onItemContextMenu = onItemContextMenu,
                            onItemFocused = { item ->
                                gridFocus.rowFocus.onRowFocused(columnItem.rowKey)
                                onItemFocused(item)
                            },
                            onRowEmpty = {
                                gridFocus.rowFocus.onRowEmpty(
                                    gridFocus.rows.indexOfFirst { it.key == columnItem.rowKey }
                                )
                            },
                        )
                    }
                }
            }

            VideoGridTopGradient(visible = enableTopSideGradient && showTopGradient)
        }
    }
}

@Composable
private fun BoxScope.VideoGridTopGradient(visible: Boolean) {
    if (!visible) return
    val gradientHeight = 48.dp
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val gradientBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(surfaceColor, surfaceColor.copy(alpha = 0F)),
            endY = with(density) { gradientHeight.toPx() },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(gradientHeight)
            .align(Alignment.TopCenter)
            .background(brush = gradientBrush),
    )
}

@Composable
private fun VideoGridItems(
    items: VideoGridItemUIState.Items,
    isTargetRow: Boolean,
    initialFocusedItemId: Int?,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (VideoItemUIState) -> Unit,
    onRowEmpty: () -> Unit,
) {
    val itemFocus = rememberReconciledItemFocus(
        rowKey = items.rowKey,
        items = items.items,
        isTargetRow = isTargetRow,
        initialFocusedItemId = initialFocusedItemId,
        requestAfterFrame = true,
        onRowEmpty = onRowEmpty,
    )
    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        val listState = rememberLazyListState()
        val rowFocusRequester = remember { FocusRequester() }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocusRequester)
                .dpadScrollOptimization(axis = DpadScrollAxis.Horizontal)
                .focusRestorer(itemFocus.focusRequester)
                .onFocusChanged { itemFocus.rowHasFocusRef[0] = it.hasFocus },
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(items.items, key = { _, item -> item.id }) { indexR, item ->
                val isFallbackTarget = item.id == itemFocus.targetItemId
                VideoGridRowItem(
                    item = item,
                    itemIndex = indexR,
                    isFallbackTarget = isFallbackTarget,
                    rowFocusRequester = rowFocusRequester,
                    savedItemFocusRequester = itemFocus.focusRequester,
                    onItemClick = onItemClick,
                    onItemContextMenu = onItemContextMenu,
                    onItemFocused = { _, focusedItem ->
                        itemFocus.onItemFocused(focusedItem.id)
                        onItemFocused(focusedItem)
                    },
                )
            }
        }
        FadeGradient(listState)
    }
}

@Composable
private fun VideoGridRowItem(
    item: VideoItemUIState,
    itemIndex: Int,
    isFallbackTarget: Boolean,
    rowFocusRequester: FocusRequester,
    savedItemFocusRequester: FocusRequester,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (Int, VideoItemUIState) -> Unit,
) {
    val focusModifier = remember(itemIndex, item.id) {
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                onItemFocused(itemIndex, item)
            }
        }
    }
    val clickCallback = remember(item.id, item.presentation) {
        if (item.presentation == VideoItemPresentation.Playable) {
            {
                runCatching { rowFocusRequester.saveFocusedChild() }
                onItemClick(item)
            }
        } else {
            {}
        }
    }
    val contextMenuCallback = onItemContextMenu
        ?.takeIf { item.presentation == VideoItemPresentation.Playable }
        ?.let { callback -> { callback(item) } }
    VideoItem(
        modifier = Modifier
            .then(
                if (isFallbackTarget) {
                    Modifier.focusRequester(savedItemFocusRequester)
                } else {
                    Modifier
                },
            )
            .then(focusModifier),
        state = item,
        onClick = clickCallback,
        onContextMenu = contextMenuCallback,
    )
}
