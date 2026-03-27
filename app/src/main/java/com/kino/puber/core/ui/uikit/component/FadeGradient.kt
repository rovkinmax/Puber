package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Composable
internal fun BoxScope.FadeGradient(listState: LazyListState) {
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
