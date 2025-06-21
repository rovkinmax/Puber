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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.modifier.createInitialFocusRestorerModifiers
import com.kino.puber.core.ui.uikit.component.modifier.ifElse
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoGridUIState(
    val list: List<VideoGridItemUIState>,
)

sealed class VideoGridItemUIState {
    class Title(val title: String) : VideoGridItemUIState()
    class Items(val items: List<VideoItemUIState>) : VideoGridItemUIState()
}

@Composable
fun VideoGrid(
    modifier: Modifier = Modifier,
    state: VideoGridUIState,
    onItemClick: (VideoItemUIState) -> Unit = {},
    onItemFocused: (VideoItemUIState) -> Unit = {},
    enableTopSideGradient: Boolean = true,
) {
    val lazyListState = rememberLazyListState()

    val showTopGradient by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset > 0 } }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = PuberTheme.Defaults.VideoItemHeight),
        ) {
            itemsIndexed(state.list) { indexC, columnItem ->
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
                        columnIndex = indexC,
                        onItemClick = onItemClick,
                        onItemFocused = onItemFocused,
                    )
                }
            }
        }

        if (enableTopSideGradient && showTopGradient) {
            val gradientHeight = 48.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gradientHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0F),
                            ),
                            endY = with(LocalDensity.current) { gradientHeight.toPx() },
                        )
                    )
            )
        }
    }
}

@Composable
private fun VideoGridItems(
    items: VideoGridItemUIState.Items,
    columnIndex: Int,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
) {
    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {

        val listState = rememberLazyListState()
        val focusRestorerModifier = createInitialFocusRestorerModifiers()
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRestorerModifier.parentModifier),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(items.items) { indexR, item ->
                VideoItem(
                    modifier = Modifier
                        .ifElse(
                            indexR == 0 && columnIndex == 0,
                            ifTrueModifier = focusRestorerModifier.childModifier,
                        )
                        .onFocusChanged { state ->
                            if (state.hasFocus) {
                                onItemFocused(item)
                            }
                        },
                    state = item,
                    onClick = { onItemClick(item) },
                )
            }
        }
        FadeGradient(listState)
    }
}

@Composable
private fun BoxScope.FadeGradient(listState: LazyListState) {
    val showGradient by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItems = layoutInfo.totalItemsCount
            val viewportEndOffset = layoutInfo.viewportEndOffset

            if (totalItems == 0 || visibleItems.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItems.lastOrNull()
                val isLastItemInDataSet = lastVisibleItem?.index == totalItems - 1

                if (isLastItemInDataSet) {
                    val lastItemEnd = lastVisibleItem.offset + lastVisibleItem.size
                    lastItemEnd > viewportEndOffset
                } else {
                    true
                }
            }
        }
    }

    if (showGradient) {
        val gradientWidth = 36.dp
        Box(
            modifier = Modifier
                .width(gradientWidth)
                .height(PuberTheme.Defaults.VideoItemHeight)
                .align(Alignment.CenterEnd)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0F),
                            MaterialTheme.colorScheme.surface,
                        ),
                        endX = with(LocalDensity.current) { gradientWidth.toPx() },
                    )
                )
        )
    }
}