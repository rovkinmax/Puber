package com.kino.puber.ui.feature.history.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.House
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenError
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.ListItemError
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.ui.feature.history.component.preview.HistoryScreenPreviewProvider
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.ui.feature.main.toptabs.TopTabBar

internal const val HISTORY_REFRESH_TEST_TAG = "history_refresh_action"
internal const val HISTORY_FINAL_PAGE_TEST_TAG = "history_final_page"
internal const val HISTORY_REFRESH_INDICATOR_TEST_TAG = "history_refresh_indicator"
internal const val HISTORY_NEXT_PAGE_SKELETON_TEST_TAG = "history_next_page_skeleton"
internal const val HISTORY_NEXT_PAGE_SKELETON_CARD_TEST_TAG_PREFIX =
    "history_next_page_skeleton_card_"

private const val GRID_COLUMNS = 3
private const val LOAD_MORE_THRESHOLD = GRID_COLUMNS * 2
private const val LOADING_MORE_KEY = "history_loading_more"
private const val NEXT_PAGE_ERROR_KEY = "history_next_page_error"
private const val RELOAD_ERROR_KEY = "history_reload_error"
private val REFRESH_INDICATOR_HEIGHT = 4.dp

@Composable
internal fun HistoryScreenContent(
    state: HistoryViewState,
    presentation: HistoryPresentation,
    onAction: (UIAction) -> Unit,
) {
    var previousStateWasContent by remember { mutableStateOf(state is HistoryViewState.Content) }
    var refreshFocusRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        if (state is HistoryViewState.Empty && previousStateWasContent) {
            refreshFocusRequest++
        }
        previousStateWasContent = state is HistoryViewState.Content
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            HistoryViewState.Loading -> FullScreenProgressIndicator()
            HistoryViewState.Empty -> HistoryEmptyState(
                focusRequest = refreshFocusRequest,
                onRefresh = { onAction(CommonAction.RetryClicked) },
            )
            is HistoryViewState.Error -> FullScreenError(
                error = state.message,
                onClick = { onAction(CommonAction.RetryClicked) },
            )
            is HistoryViewState.Content -> when {
                state.items.isNotEmpty() -> HistoryContent(
                    state = state,
                    presentation = presentation,
                    onAction = onAction,
                )
                state.reloadErrorMessage != null -> FullScreenError(
                    error = state.reloadErrorMessage,
                    onClick = { onAction(HistoryAction.RetryReconciliation) },
                )
                else -> FullScreenProgressIndicator()
            }
        }
    }
}

@Composable
private fun HistoryContent(
    state: HistoryViewState.Content,
    presentation: HistoryPresentation,
    onAction: (UIAction) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val gridFocusRequester = rememberFocusRequesterOnLaunch()
    val focusedUiKey = state.focusKey?.toHistoryUiKey()
    val menuUiKey = state.openMenuKey?.toHistoryUiKey()
    val isPaginationIdle = !state.isRefreshing && !state.isLoadingMore
    val isPaginationDefinitivelyExhausted = !state.hasMorePages &&
        isPaginationIdle &&
        state.nextPageErrorMessage == null &&
        state.reloadErrorMessage == null
    val finalPageMarkerModifier = if (isPaginationDefinitivelyExhausted) {
        Modifier.testTag(HISTORY_FINAL_PAGE_TEST_TAG)
    } else {
        Modifier
    }

    HistoryGridEffects(
        state = state,
        gridState = gridState,
        focusedUiKey = focusedUiKey,
        menuUiKey = menuUiKey,
        onAction = onAction,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .then(finalPageMarkerModifier),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (presentation) {
                HistoryPresentation.TopTabs -> Unit
                HistoryPresentation.SideDrawer -> HistoryHeader()
            }
            HistoryRefreshIndicator(isRefreshing = state.isRefreshing)
            HistoryGrid(
                state = state,
                gridState = gridState,
                focusedUiKey = focusedUiKey,
                menuUiKey = menuUiKey,
                gridFocusModifier = Modifier.focusRequester(gridFocusRequester),
                onAction = onAction,
            )
        }
        HistoryContextMenu(
            item = state.openMenuKey?.let { key ->
                state.items.firstOrNull { it.rowKey == key }
            },
            isDeleteExactMediaAvailable = state.isDeleteExactMediaAvailable,
            onAction = onAction,
        )
    }
}

@Composable
private fun HistoryGridEffects(
    state: HistoryViewState.Content,
    gridState: LazyGridState,
    focusedUiKey: String?,
    menuUiKey: String?,
    onAction: (UIAction) -> Unit,
) {
    val isNearEnd by remember(state.items.size) {
        derivedStateOf {
            if (state.items.isEmpty()) {
                return@derivedStateOf false
            }
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleIndex >= state.items.lastIndex - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(
        isNearEnd,
        state.items.size,
        state.isRefreshing,
        state.isLoadingMore,
        state.hasMorePages,
        state.pageAttemptRevision,
        state.nextPageErrorMessage,
    ) {
        val pagingIsIdle = !state.isRefreshing && !state.isLoadingMore
        val canLoadMore = pagingIsIdle &&
            state.hasMorePages &&
            state.nextPageErrorMessage == null
        if (isNearEnd && canLoadMore) {
            onAction(CommonAction.LoadMore)
        }
    }

    LaunchedEffect(focusedUiKey, menuUiKey) {
        if (focusedUiKey != null && menuUiKey == null) {
            val targetIndex = state.items.indexOfFirst {
                it.rowKey.toHistoryUiKey() == focusedUiKey
            }
            val targetIsVisible = gridState.layoutInfo.visibleItemsInfo.any {
                it.index == targetIndex
            }
            if (targetIndex >= 0 && !targetIsVisible) {
                gridState.scrollToItem(targetIndex)
            }
        }
    }
}

@Composable
private fun HistoryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun HistoryRefreshIndicator(isRefreshing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(REFRESH_INDICATOR_HEIGHT)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HISTORY_REFRESH_INDICATOR_TEST_TAG),
            )
        }
    }
}

@Composable
private fun HistoryGrid(
    state: HistoryViewState.Content,
    gridState: LazyGridState,
    focusedUiKey: String?,
    menuUiKey: String?,
    gridFocusModifier: Modifier,
    onAction: (UIAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            modifier = gridFocusModifier
                .fillMaxSize()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            itemsIndexed(
                items = state.items,
                key = { _, item -> item.rowKey.toHistoryUiKey() },
            ) { index, item ->
                HistoryGridCard(
                    state = item,
                    blockRightFocusExit =
                        index % GRID_COLUMNS == GRID_COLUMNS - 1 ||
                            index == state.items.lastIndex,
                    focusedUiKey = focusedUiKey,
                    menuUiKey = menuUiKey,
                    deletingKeys = state.deletingKeys,
                    onAction = onAction,
                )
            }

            historyStatusItems(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun HistoryGridCard(
    state: HistoryItemUIState,
    blockRightFocusExit: Boolean,
    focusedUiKey: String?,
    menuUiKey: String?,
    deletingKeys: Set<HistoryRowKey>,
    onAction: (UIAction) -> Unit,
) {
    val itemUiKey = state.rowKey.toHistoryUiKey()
    val onClick = remember(state, onAction) {
        { onAction(CommonAction.ItemSelected(state)) }
    }
    val onContextMenu = remember(state, onAction) {
        { onAction(HistoryAction.OpenContextMenu(state)) }
    }
    val onFocus = remember(state, onAction) {
        { onAction(CommonAction.ItemFocused(state)) }
    }
    HistoryCard(
        state = state,
        requestFocus = focusedUiKey == itemUiKey && menuUiKey == null,
        isDeleting = state.rowKey in deletingKeys,
        blockRightFocusExit = blockRightFocusExit,
        onClick = onClick,
        onContextMenu = onContextMenu,
        onFocus = onFocus,
    )
}

private fun LazyGridScope.historyStatusItems(
    state: HistoryViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    if (state.reloadErrorMessage != null) {
        item(
            key = RELOAD_ERROR_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            ListItemError(
                error = state.reloadErrorMessage,
                onClick = { onAction(HistoryAction.RetryReconciliation) },
            )
        }
    }

    if (state.nextPageErrorMessage != null) {
        item(
            key = NEXT_PAGE_ERROR_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            ListItemError(
                error = state.nextPageErrorMessage,
                onClick = { onAction(CommonAction.ReloadNextPage) },
            )
        }
    }

    if (state.isLoadingMore) {
        item(
            key = LOADING_MORE_KEY,
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HISTORY_NEXT_PAGE_SKELETON_TEST_TAG)
                    .focusProperties { canFocus = false },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(GRID_COLUMNS) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PuberTheme.Defaults.HorizontalVideoItemHeight)
                            .testTag(
                                HISTORY_NEXT_PAGE_SKELETON_CARD_TEST_TAG_PREFIX + index,
                            )
                            .placeholder(visible = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(
    focusRequest: Int,
    onRefresh: () -> Unit,
) {
    val refreshFocusRequester = rememberFocusRequesterOnLaunch()

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            refreshFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.history_empty_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRefresh,
            modifier = Modifier
                .focusRequester(refreshFocusRequester)
                .testTag(HISTORY_REFRESH_TEST_TAG),
        ) {
            Text(stringResource(R.string.history_refresh))
        }
    }
}

@Preview(
    name = "History — TopTabs states",
    device = TV_1080p,
)
@Composable
private fun HistoryScreenContentPreview(
    @PreviewParameter(HistoryScreenPreviewProvider::class) state: HistoryViewState,
) = PuberTheme {
    HistoryTopTabsPreviewHost(state)
}

@Composable
private fun HistoryTopTabsPreviewHost(state: HistoryViewState) {
    val tabs = listOf(
        MainTab(
            type = TabType.Home,
            label = stringResource(R.string.main_tabs_home),
            icon = PhosphorIcons.Duotone.House,
        ),
        MainTab(
            type = TabType.Movies,
            label = stringResource(R.string.main_tabs_movies),
            icon = PhosphorIcons.Duotone.FilmSlate,
        ),
        MainTab(
            type = TabType.Series,
            label = stringResource(R.string.main_tabs_series),
            icon = PhosphorIcons.Duotone.TelevisionSimple,
        ),
        MainTab(
            type = TabType.Collections,
            label = stringResource(R.string.main_tabs_collections),
            icon = PhosphorIcons.Duotone.Playlist,
        ),
        MainTab(
            type = TabType.History,
            label = stringResource(R.string.main_tabs_history),
            icon = PhosphorIcons.Duotone.ClockCounterClockwise,
            isSelected = true,
        ),
    )
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopTabBar(
            tabs = tabs,
            selectedIndex = tabs.lastIndex,
            tabFocusRequesters = tabFocusRequesters,
            onContentFocusRequested = {},
            onTabFocused = {},
            onTabClick = {},
            onTabContextMenu = {},
            onSearchClick = {},
            onSettingsClick = {},
        )
        Box(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides false) {
                HistoryScreenContent(
                    state = state,
                    presentation = HistoryPresentation.TopTabs,
                    onAction = {},
                )
            }
        }
    }
}
