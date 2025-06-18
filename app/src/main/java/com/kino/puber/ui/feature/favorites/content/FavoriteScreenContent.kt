package com.kino.puber.ui.feature.favorites.content

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.modifier.ifElse
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItem
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState

@Composable
internal fun FavoriteScreenContent(
    state: FavoriteViewState,
    onAction: (UIAction) -> Unit,
) {
    val fallbackFocusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(fallbackFocusRequester)
            .focusGroup(),
    ) {
        when (state) {
            FavoriteViewState.Empty -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Empty")
            }

            is FavoriteViewState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.message)
            }

            FavoriteViewState.Loading -> FullScreenProgressIndicator()
            is FavoriteViewState.Content -> FavoriteScreenContentBody(
                state = state,
                onAction = onAction,
                fallbackFocusRequester = fallbackFocusRequester,
            )
        }
    }
}

@Composable
private fun FavoriteScreenContentBody(
    state: FavoriteViewState.Content,
    onAction: (UIAction) -> Unit,
    fallbackFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2F)
        ) {

        }
        LazyVerticalGrid(
            modifier = Modifier
                .weight(2F)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            columns = GridCells.Fixed(6),
        ) {
            itemsIndexed(state.items) { index, item ->
                VideoItem(
                    modifier = Modifier.ifElse(
                        index == 0,
                        Modifier.focusRequester(fallbackFocusRequester),
                    ),
                    state = item,
                    onClick = { onAction(CommonAction.ItemSelected(item)) },
                )
            }
        }
    }
}