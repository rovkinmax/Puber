package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.showall.model.ShowAllViewState

private const val GRID_COLUMNS = 3
private const val LOAD_MORE_THRESHOLD = 12

@Composable
internal fun ShowAllScreenContent(
    state: ShowAllViewState,
    onAction: (UIAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            ShowAllViewState.Loading -> FullScreenProgressIndicator()
            ShowAllViewState.Empty -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.empty_state))
            }
            is ShowAllViewState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.message)
            }
            is ShowAllViewState.Content -> ShowAllContentBody(
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ShowAllContentBody(
    state: ShowAllViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            lastVisibleItem.index > (totalItemsCount - LOAD_MORE_THRESHOLD - 1)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onAction(CommonAction.LoadMore)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(mainContentFocus),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
            VideoItemHorizontal(
                state = item,
                onClick = { onAction(CommonAction.ItemSelected(item)) },
            )
        }
    }
}