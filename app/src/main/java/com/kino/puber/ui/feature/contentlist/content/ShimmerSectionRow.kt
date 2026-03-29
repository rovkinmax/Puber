package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private const val SHIMMER_ITEM_COUNT = 7

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ShimmerSectionCards() {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
        userScrollEnabled = false,
    ) {
        items(SHIMMER_ITEM_COUNT, contentType = { "shimmer" }) {
            ShimmerVideoItem()
        }
    }
}

@Composable
private fun ShimmerVideoItem() {
    Box(
        modifier = Modifier
            .height(PuberTheme.Defaults.HorizontalVideoItemHeight)
            .aspectRatio(PuberTheme.Defaults.HorizontalVideoItemAspectRatio)
            .placeholder(visible = true)
            .focusable(),
    )
}
