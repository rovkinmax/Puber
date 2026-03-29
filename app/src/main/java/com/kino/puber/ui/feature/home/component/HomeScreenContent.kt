package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeViewState

@Composable
internal fun HomeScreenContent(
    state: HomeViewState,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
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
            HomeContent(state = state, onAction = onAction, onHeroClick = onHeroClick)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeViewState.Content,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
) {
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
            item(key = "title_${section.type.name}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(key = "content_${section.type.name}") {
                HomeSectionRow(
                    items = section.items,
                    onItemClick = { item -> onAction(CommonAction.ItemSelected(item)) },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<VideoItemUIState>,
    onItemClick: (VideoItemUIState) -> Unit,
) {
    LazyRow(
        modifier = Modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items = items, key = { it.id }) { item ->
            val clickCallback = remember(item.id) { { onItemClick(item) } }
            VideoItemHorizontal(
                state = item,
                onClick = clickCallback,
            )
        }
    }
}
