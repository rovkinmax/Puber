package com.kino.puber.ui.feature.main.toptabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.LocalRootAnchorRestorePending
import com.kino.puber.core.ui.navigation.component.LocalRootFocusRestoreVersion
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.LocalTvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.TopTabContextMenuDialog
import com.kino.puber.core.ui.uikit.component.TvDialogFocusRestorer
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TopTabMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
) {
    val tabRowFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val rootFocusRestoreVersion = LocalRootFocusRestoreVersion.current
    val rootAnchorRestorePending = LocalRootAnchorRestorePending.current
    val skipInitialFocus = remember { rootAnchorRestorePending }
    val tabFocusState = rememberTopTabFocusState(state, tabRowFocus)
    val tabFocusRequesters = tabFocusState.focusRequesters
    val focusedTabType = tabFocusState.focusedTabType
    var lastFocusedRegion by rememberSaveable { mutableStateOf(TopTabFocusedRegion.Tabs) }
    var isContentFocused by remember { mutableStateOf(false) }
    var contextMenuTabIndex by remember { mutableStateOf<Int?>(null) }
    var refreshFocusRequestVersion by rememberSaveable { mutableIntStateOf(0) }
    DelayedTabSelectionEffect(
        tabs = state.tabs,
        focusedTabType = focusedTabType,
        selectedTab = state.selectedTab,
        focusIntentToken = tabFocusState.focusIntentToken,
        onAction = onAction,
    )
    InitialTabFocusEffect(
        focusedTabType = focusedTabType,
        tabFocusRequesters = tabFocusRequesters,
        tabRowFocus = tabRowFocus,
        contentFocus = contentFocus,
        focusManager = focusManager,
        lastFocusedRegion = lastFocusedRegion,
        skipInitialFocus = skipInitialFocus,
    )
    RootReturnContentFocusEffect(
        restoreVersion = rootFocusRestoreVersion,
        onRestore = {
            lastFocusedRegion = TopTabFocusedRegion.Content
            isContentFocused = true
        },
    )
    RefreshContentFocusEffect(
        requestVersion = refreshFocusRequestVersion,
        contentFocus = contentFocus,
        focusManager = focusManager,
    )
    val isOnHome = focusedTabType == TabType.Home

    TopTabBackHandler(
        enabled = isContentFocused || !isOnHome,
        isContentFocused = isContentFocused,
        state = state,
        tabRowFocus = tabRowFocus,
        tabFocusRequesters = tabFocusRequesters,
        onTabsFocused = { lastFocusedRegion = TopTabFocusedRegion.Tabs },
        onHomeFocused = tabFocusState.onTabFocused,
        onAction = onAction,
    )

    TabComponent(
        tabRouter = tabRouter,
        tabAppRouterHolder = tabAppRouterHolder,
    ) {
        val requestContentFocus = {
            lastFocusedRegion = TopTabFocusedRegion.Content
            contentFocus.requestFocus()
            Unit
        }

        CompositionLocalProvider(
            LocalTvDialogFocusRestorer provides tabFocusState.dialogFocusRestorer
        ) {
            Column(Modifier.fillMaxSize()) {
                TopTabBar(
                    tabs = state.tabs,
                    selectedIndex = tabFocusState.selectedIndex,
                    tabFocusRequesters = tabFocusRequesters,
                    onContentFocusRequested = requestContentFocus,
                    onTabFocused = tabFocusState.onTabFocused,
                    onTabClick = requestContentFocus,
                    onTabContextMenu = { index -> contextMenuTabIndex = index },
                    onSearchClick = { onAction(MainAction.SearchClicked) },
                    onSettingsClick = { onAction(MainAction.SettingsClicked) },
                    modifier = Modifier
                        .onFocusChanged {
                            if (it.hasFocus) {
                                isContentFocused = false
                            }
                        },
                    tabRowModifier = Modifier
                        .focusRequester(tabRowFocus),
                )

                TopTabContentFocusProvider(isContentFocused) {
                    TopTabContentBox(
                        contentFocus = contentFocus,
                        tabRowFocus = tabRowFocus,
                        onExitToTabs = { lastFocusedRegion = TopTabFocusedRegion.Tabs },
                        onFocused = {
                            lastFocusedRegion = TopTabFocusedRegion.Content
                            isContentFocused = true
                        },
                    )
                }

                val contextMenuTab = contextMenuTabIndex?.let(state.tabs::getOrNull)
                TopTabContextMenuDialog(
                    title = contextMenuTab?.label,
                    onRefresh = {
                        contextMenuTab?.let { tab ->
                            lastFocusedRegion = TopTabFocusedRegion.Content
                            refreshFocusRequestVersion++
                            onAction(MainAction.RefreshTab(tab))
                        }
                    },
                    onDismiss = { contextMenuTabIndex = null },
                )
            }
        }
    }
}

@Composable
private fun TopTabContentFocusProvider(
    isContentFocused: Boolean,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(
    LocalAutoFocusOnLaunchEnabled provides false,
    LocalContentFocusActive provides isContentFocused,
    content = content,
)

@Composable
private fun RefreshContentFocusEffect(
    requestVersion: Int,
    contentFocus: FocusRequester,
    focusManager: FocusManager,
) {
    LaunchedEffect(requestVersion) {
        if (requestVersion == 0) return@LaunchedEffect
        delay(REFRESH_CONTENT_FOCUS_DELAY_MS)
        restoreContentChildFocus(
            contentFocus = contentFocus,
            focusManager = focusManager,
        )
    }
}

@Composable
private fun RootReturnContentFocusEffect(
    restoreVersion: Int,
    onRestore: () -> Unit,
) {
    LaunchedEffect(restoreVersion) {
        if (restoreVersion == 0) return@LaunchedEffect
        withFrameNanos { }
        onRestore()
    }
}

@Composable
private fun DelayedTabSelectionEffect(
    tabs: List<MainTab>,
    focusedTabType: TabType?,
    selectedTab: TabType,
    focusIntentToken: Int,
    onAction: (UIAction) -> Unit,
) {
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestFocusIntentToken by rememberUpdatedState(focusIntentToken)
    LaunchedEffect(focusedTabType, focusIntentToken) {
        val tab = tabs.firstOrNull { it.type == focusedTabType } ?: return@LaunchedEffect
        if (tab.type == latestSelectedTab) return@LaunchedEffect

        delay(TAB_SELECTION_DELAY_MS)
        if (!TopTabFocusIntent(focusedTabType, latestFocusIntentToken).isLatest(focusIntentToken)) {
            return@LaunchedEffect
        }
        if (tab.type != latestSelectedTab) {
            onAction(CommonAction.ItemSelected(tab))
        }
    }
}

@Composable
private fun InitialTabFocusEffect(
    focusedTabType: TabType?,
    tabFocusRequesters: Map<TabType, FocusRequester>,
    tabRowFocus: FocusRequester,
    contentFocus: FocusRequester,
    focusManager: FocusManager,
    lastFocusedRegion: TopTabFocusedRegion,
    skipInitialFocus: Boolean,
) {
    LaunchedEffect(Unit) {
        if (skipInitialFocus) return@LaunchedEffect
        delay(INITIAL_TAB_FOCUS_DELAY_MS)
        when (lastFocusedRegion) {
            TopTabFocusedRegion.Tabs -> tabFocusRequesters[focusedTabType]?.requestFocus()
                ?: tabRowFocus.requestFocus()
            TopTabFocusedRegion.Content -> restoreContentChildFocus(
                contentFocus = contentFocus,
                focusManager = focusManager,
            )
        }
    }
}

private suspend fun restoreContentChildFocus(
    contentFocus: FocusRequester,
    focusManager: FocusManager,
) {
    repeat(CONTENT_FOCUS_RESTORE_ATTEMPTS) {
        if (contentFocus.restoreFocusedChild()) {
            return
        }
        val contentFocused = contentFocus.requestFocus()
        delay(CONTENT_CHILD_FOCUS_DELAY_MS)
        if (contentFocus.restoreFocusedChild()) {
            return
        }
        if (contentFocused && focusManager.moveFocus(FocusDirection.Down)) {
            return
        }
        delay(CONTENT_FOCUS_RESTORE_RETRY_DELAY_MS)
    }
}

@Composable
private fun TopTabBackHandler(
    enabled: Boolean,
    isContentFocused: Boolean,
    state: MainViewState,
    tabRowFocus: FocusRequester,
    tabFocusRequesters: Map<TabType, FocusRequester>,
    onTabsFocused: () -> Unit,
    onHomeFocused: (TabType) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    BackHandler(enabled = enabled) {
        if (isContentFocused) {
            onTabsFocused()
            tabRowFocus.requestFocus()
        } else {
            onTabsFocused()
            focusHomeTab(
                state = state,
                tabFocusRequesters = tabFocusRequesters,
                onHomeFocused = onHomeFocused,
                onAction = onAction,
            )
        }
    }
}

private fun focusHomeTab(
    state: MainViewState,
    tabFocusRequesters: Map<TabType, FocusRequester>,
    onHomeFocused: (TabType) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    val homeIndex = state.tabs.indexOfFirst { it.type == TabType.Home }.coerceAtLeast(0)
    state.tabs.getOrNull(homeIndex)?.let { homeTab ->
        onHomeFocused(homeTab.type)
        onAction(CommonAction.ItemSelected(homeTab))
        tabFocusRequesters[homeTab.type]?.requestFocus()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ColumnScope.TopTabContentBox(
    contentFocus: FocusRequester,
    tabRowFocus: FocusRequester,
    onExitToTabs: () -> Unit,
    onFocused: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val contentDialogFocusRestorer = remember(contentFocus, coroutineScope, focusManager) {
        TvDialogFocusRestorer(
            onDialogOpening = { contentFocus.saveFocusedChild() },
            onDialogClosed = {
                coroutineScope.launch {
                    delay(CONTENT_CHILD_FOCUS_DELAY_MS)
                    restoreContentChildFocus(
                        contentFocus = contentFocus,
                        focusManager = focusManager,
                    )
                }
            },
        )
    }
    Box(
        Modifier
            .weight(1f)
            .topTabContentFocusBehavior(
                contentFocus = contentFocus,
                tabRowFocus = tabRowFocus,
                coroutineScope = coroutineScope,
                focusManager = focusManager,
                onExitToTabs = onExitToTabs,
                onFocused = onFocused,
            )
            .focusRestorer()
            .focusGroup()
    ) {
        CompositionLocalProvider(
            LocalTvDialogFocusRestorer provides contentDialogFocusRestorer
        ) {
            PuberCurrentTab()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.topTabContentFocusBehavior(
    contentFocus: FocusRequester,
    tabRowFocus: FocusRequester,
    coroutineScope: CoroutineScope,
    focusManager: FocusManager,
    onExitToTabs: () -> Unit,
    onFocused: () -> Unit,
): Modifier {
    return this
            .focusRequester(contentFocus)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onFocused()
                }
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        delay(CONTENT_CHILD_FOCUS_DELAY_MS)
                        if (!contentFocus.restoreFocusedChild()) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                }
            }
            .focusProperties {
                enter = {
                    if (contentFocus.restoreFocusedChild()) {
                        FocusRequester.Cancel
                    } else {
                        FocusRequester.Default
                    }
                }
                @Suppress("DEPRECATION")
                exit = { direction ->
                    contentFocus.saveFocusedChild()
                    if (direction == FocusDirection.Up) {
                        onExitToTabs()
                        tabRowFocus
                    } else {
                        FocusRequester.Default
                    }
                }
            }
}

private const val TAB_SELECTION_DELAY_MS = 300L
private const val INITIAL_TAB_FOCUS_DELAY_MS = 100L
private const val CONTENT_CHILD_FOCUS_DELAY_MS = 16L
private const val CONTENT_FOCUS_RESTORE_ATTEMPTS = 5
private const val CONTENT_FOCUS_RESTORE_RETRY_DELAY_MS = 50L
private const val REFRESH_CONTENT_FOCUS_DELAY_MS = 150L

private enum class TopTabFocusedRegion {
    Tabs,
    Content,
}
