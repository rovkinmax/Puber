package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState

@Composable
internal fun HomeScreenContent(
    state: HomeViewState,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
) {
    when (state) {
        is HomeViewState.Loading -> {
            FullScreenProgressIndicator()
        }
        is HomeViewState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message)
            }
        }
        is HomeViewState.Content -> {
            HomeContent(
                state = state,
                onAction = onAction,
                onHeroClick = onHeroClick,
                onCollectionClick = onCollectionClick,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeViewState.Content,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
) {
    PositionFocusedItemInLazyLayout {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (state.heroItems.isNotEmpty()) {
                item(key = "hero") {
                    HeroCarousel(
                        items = state.heroItems,
                        onItemClick = onHeroClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.sections.forEach { section ->
                item(key = "section_${section.type.name}") {
                    Column {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        HomeSectionRow(
                            items = section.items,
                            onItemClick = { item ->
                                if (section.type == HomeSectionType.Collections) {
                                    onCollectionClick(item.id, item.title)
                                } else {
                                    onAction(CommonAction.ItemSelected(item))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<VideoItemUIState>,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    val listState = rememberLazyListState()
    val savedItemFocusRequester = remember { FocusRequester() }
    var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }

    LazyRow(
        state = listState,
        modifier = Modifier
            .graphicsLayer { clip = false }
            .focusRestorer(savedItemFocusRequester),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
            val isFocusTarget = index == focusedItemIndex
            VideoItemHorizontal(
                modifier = Modifier
                    .then(
                        if (isFocusTarget) Modifier.focusRequester(savedItemFocusRequester)
                        else Modifier
                    )
                    .onFocusChanged { if (it.isFocused) focusedItemIndex = index },
                state = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}
