package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private const val SHIMMER_ITEM_COUNT = 7

@Composable
internal fun ShimmerSectionCards() {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
        userScrollEnabled = false,
    ) {
        items(SHIMMER_ITEM_COUNT) {
            ShimmerVideoItem()
        }
    }
}

@Composable
private fun ShimmerVideoItem() {
    Box(
        modifier = Modifier
            .size(
                width = PuberTheme.Defaults.VideoItemWidth,
                height = PuberTheme.Defaults.VideoItemHeight,
            )
            .placeholder(visible = true),
    )
}
