package com.kino.puber.ui.feature.collections.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.Text
import com.kino.puber.core.ui.navigation.component.LocalFocusRestoreTarget
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.ui.feature.collections.model.CollectionUIState
import com.kino.puber.ui.feature.collections.model.CollectionsViewState

@Composable
internal fun CollectionsScreenContent(
    state: CollectionsViewState,
    onCollectionClick: (CollectionUIState) -> Unit,
    onLoadMore: () -> Unit,
) {
    when (state) {
        is CollectionsViewState.Loading -> FullScreenProgressIndicator()
        is CollectionsViewState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message)
            }
        }
        is CollectionsViewState.Content -> {
            CollectionsGrid(
                collections = state.collections,
                onCollectionClick = onCollectionClick,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun CollectionsGrid(
    collections: List<CollectionUIState>,
    onCollectionClick: (CollectionUIState) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    val savedItemFocusRequester = remember { FocusRequester() }
    val restoreFocusTarget = LocalFocusRestoreTarget.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer(savedItemFocusRequester),
    ) {
        itemsIndexed(collections, key = { _, item -> item.id }) { index, collection ->
            if (index >= collections.size - 6) {
                onLoadMore()
            }
            val isFocusTarget = index == focusedItemIndex
            val clickCallback = remember(collection.id) { { onCollectionClick(collection) } }
            CollectionCard(
                state = collection,
                onClick = clickCallback,
                modifier = Modifier
                    .then(
                        if (isFocusTarget) {
                            Modifier
                                .focusRequester(savedItemFocusRequester)
                                .then(
                                    if (restoreFocusTarget != null) Modifier.focusRequester(restoreFocusTarget)
                                    else Modifier
                                )
                        } else Modifier
                    )
                    .onFocusChanged { if (it.isFocused) focusedItemIndex = index },
            )
        }
    }
}
