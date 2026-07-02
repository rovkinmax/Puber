package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.ApiDomainDialog
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.component.VideoItemContextMenuDialog
import com.kino.puber.core.ui.uikit.component.dpadScrollOptimization
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemHorizontal
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.home.model.HomeAction
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState

@Composable
internal fun HomeScreenContent(
    state: HomeViewState,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            is HomeViewState.Loading -> {
                LoadingView(message = state.message)
            }

            is HomeViewState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { onAction(CommonAction.RetryClicked) },
                    onConfigureApiDomain = { onAction(HomeAction.OpenApiDomainDialog) },
                )
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

        ApiDomainDialog(
            state = state.apiDomainDialog,
            onSave = { onAction(HomeAction.SaveApiDomain(it)) },
            onReset = { onAction(HomeAction.ResetApiDomain) },
            onDetect = { onAction(HomeAction.DetectApiDomain) },
            onDismiss = { onAction(HomeAction.CloseApiDomainDialog) },
        )
    }
}

@Composable
private fun LoadingView(message: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullScreenProgressIndicator()
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 160.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onConfigureApiDomain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TvSafeButton(
            text = stringResource(R.string.error_button_retry),
            onClick = onRetry,
            primary = true,
        )
        Spacer(Modifier.height(8.dp))
        TvSafeButton(
            text = stringResource(R.string.api_domain_open_action),
            onClick = onConfigureApiDomain,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeViewState.Content,
    onAction: (UIAction) -> Unit,
    onHeroClick: (Int) -> Unit,
    onCollectionClick: (Int, String) -> Unit,
) {
    var focusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedTarget by remember(state.heroItems, state.sections) {
        mutableStateOf(state.defaultFocusedTarget())
    }
    var contextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }

    PositionFocusedItemInLazyLayout {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
                    .focusGroup()
                    .onTvContextMenuKey(
                        enabled = focusedTarget is HomeFocusedTarget.Video,
                        onOpen = {
                            contextMenuItem = (focusedTarget as? HomeFocusedTarget.Video)?.item
                        },
                    )
                    .onSelectKeyClick(
                        canHandle = { focusedTarget != null },
                        onClick = {
                            when (val target = focusedTarget) {
                                is HomeFocusedTarget.Collection -> onCollectionClick(target.id, target.title)
                                is HomeFocusedTarget.Hero -> onHeroClick(target.id)
                                is HomeFocusedTarget.Video -> onAction(CommonAction.ItemSelected(target.item))
                                null -> Unit
                            }
                        },
                    ),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (state.heroItems.isNotEmpty()) {
                    item(key = "hero") {
                        HeroCarousel(
                            items = state.heroItems,
                            onItemClick = onHeroClick,
                            onFocusedItemChanged = { id ->
                                focusedTarget = HomeFocusedTarget.Hero(id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.sections.forEachIndexed { index, section ->
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
                                isTargetRow = index == focusedSectionIndex,
                                onSectionFocused = { focusedSectionIndex = index },
                                onItemClick = { item ->
                                    if (section.type == HomeSectionType.Collections) {
                                        onCollectionClick(item.id, item.title)
                                    } else {
                                        onAction(CommonAction.ItemSelected(item))
                                    }
                                },
                                onItemContextMenu = if (section.type == HomeSectionType.Collections) {
                                    null
                                } else {
                                    { item -> contextMenuItem = item }
                                },
                                onItemFocused = { item ->
                                    focusedTarget = if (section.type == HomeSectionType.Collections) {
                                        HomeFocusedTarget.Collection(id = item.id, title = item.title)
                                    } else {
                                        HomeFocusedTarget.Video(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            VideoItemContextMenuDialog(
                item = contextMenuItem,
                onDismiss = { contextMenuItem = null },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    items: List<VideoItemUIState>,
    isTargetRow: Boolean,
    onSectionFocused: () -> Unit,
    onItemClick: (VideoItemUIState) -> Unit,
    onItemContextMenu: ((VideoItemUIState) -> Unit)?,
    onItemFocused: (VideoItemUIState) -> Unit,
) {
    val listState = rememberLazyListState()
    val savedItemFocusRequester = remember { FocusRequester() }
    var focusedItemIndex by rememberSaveable { mutableIntStateOf(0) }

    LazyRow(
        state = listState,
        modifier = Modifier
            .graphicsLayer { clip = false }
            .dpadScrollOptimization()
            .focusRestorer(savedItemFocusRequester),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
            val isFocusTarget = if (isTargetRow) index == focusedItemIndex else index == 0
            VideoItemHorizontal(
                modifier = Modifier
                    .then(
                        if (isFocusTarget) Modifier.focusRequester(savedItemFocusRequester)
                        else Modifier
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedItemIndex = index
                            onSectionFocused()
                            onItemFocused(item)
                        }
                    },
                state = item,
                onClick = { onItemClick(item) },
                onContextMenu = onItemContextMenu?.let { callback -> { callback(item) } },
            )
        }
    }
}

private sealed interface HomeFocusedTarget {
    data class Hero(val id: Int) : HomeFocusedTarget
    data class Video(val item: VideoItemUIState) : HomeFocusedTarget
    data class Collection(val id: Int, val title: String) : HomeFocusedTarget
}

private fun HomeViewState.Content.defaultFocusedTarget(): HomeFocusedTarget? {
    val hero = heroItems.firstOrNull()
    if (hero != null) {
        return HomeFocusedTarget.Hero(hero.id)
    }

    val section = sections.firstOrNull { it.items.isNotEmpty() } ?: return null
    val item = section.items.first()
    return if (section.type == HomeSectionType.Collections) {
        HomeFocusedTarget.Collection(id = item.id, title = item.title)
    } else {
        HomeFocusedTarget.Video(item)
    }
}

private fun Modifier.onSelectKeyClick(
    canHandle: () -> Boolean,
    onClick: () -> Unit,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (!event.key.isSelectKey()) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> canHandle()
            KeyEventType.KeyUp -> {
                if (canHandle()) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
}

private fun Key.isSelectKey(): Boolean {
    return this == Key.DirectionCenter || this == Key.Enter
}
