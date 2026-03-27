package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.kino.puber.core.ui.uikit.component.FadeGradient
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoGridUIState(
    val list: List<VideoGridItemUIState>,
)

@Immutable
sealed class VideoGridItemUIState {
    data class Title(val title: String) : VideoGridItemUIState()
    data class Items(val items: List<VideoItemUIState>) : VideoGridItemUIState()
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
    var focusedColumnIndex by rememberSaveable { mutableIntStateOf(-1) }

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
                        isTargetRow = indexC == focusedColumnIndex,
                        onItemClick = onItemClick,
                        onItemFocused = { item ->
                            focusedColumnIndex = indexC
                            onItemFocused(item)
                        },
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
    isTargetRow: Boolean,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemFocused: (VideoItemUIState) -> Unit,
) {
    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        val listState = rememberLazyListState()
        var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }

        val rowFocusRequester = remember { FocusRequester() }
        val savedItemFocusRequester = remember { FocusRequester() }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocusRequester)
                .focusRestorer(savedItemFocusRequester),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(items.items) { indexR, item ->
                val isFallbackTarget = if (isTargetRow) {
                    indexR == focusedItemIndex
                } else {
                    indexR == 0 && columnIndex == 0
                }

                VideoItem(
                    modifier = Modifier
                        .then(
                            if (isFallbackTarget) Modifier.focusRequester(savedItemFocusRequester)
                            else Modifier
                        )
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                focusedItemIndex = indexR
                                onItemFocused(item)
                            }
                        },
                    state = item,
                    onClick = {
                        runCatching { rowFocusRequester.saveFocusedChild() }
                        onItemClick(item)
                    },
                )
            }
        }
        FadeGradient(listState)
    }
}

