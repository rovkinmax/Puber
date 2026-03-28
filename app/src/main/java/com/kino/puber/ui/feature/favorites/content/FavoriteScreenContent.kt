package com.kino.puber.ui.feature.favorites.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.component.details.VideoItemGridDetails
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGrid
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState

@Composable
internal fun FavoriteScreenContent(
    state: FavoriteViewState,
    onAction: (UIAction) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
            )
        }
    }
}

@Composable
private fun FavoriteScreenContentBody(
    state: FavoriteViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    val mainContentFocus = rememberFocusRequesterOnLaunch()
    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(mainContentFocus)
        ) {
            VideoItemGridDetails(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PuberTheme.Defaults.DetailsWeight),
                state = state.selectedItem,
            )

            Row(Modifier.weight(PuberTheme.Defaults.ContentWeight)) {
                VideoGrid(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.gridState,
                    onItemClick = { onAction(CommonAction.ItemSelected(it)) },
                    onItemFocused = { onAction(CommonAction.ItemFocused(it)) },
                )
            }
        }
    }
}