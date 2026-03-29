package com.kino.puber.ui.feature.collections.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.collections.detail.model.CollectionDetailViewState

@Composable
internal fun CollectionDetailScreenContent(
    state: CollectionDetailViewState,
    onAction: (UIAction) -> Unit,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    when (state) {
        is CollectionDetailViewState.Loading -> FullScreenProgressIndicator()
        is CollectionDetailViewState.Error -> FullScreenError(
            error = state.message,
            onClick = { onAction(CommonAction.RetryClicked) },
        )
        is CollectionDetailViewState.Content -> {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )

                if (state.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Пусто")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                            VideoItemHorizontal(
                                state = item,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
