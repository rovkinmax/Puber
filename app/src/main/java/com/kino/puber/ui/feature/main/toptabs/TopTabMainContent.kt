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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TopTabMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val tabRowFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val tabFocusRequesters = remember(state.tabs.size) {
        List(state.tabs.size) { FocusRequester() }
    }
    val selectedIndex = state.tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

    var focusedTabIndex by rememberSaveable { mutableIntStateOf(selectedIndex) }
    var lastFocusedRegion by rememberSaveable { mutableStateOf(TopTabFocusedRegion.Tabs) }
    var isContentFocused by remember { mutableStateOf(false) }

    SyncSelectedTabEffect(selectedIndex) { focusedTabIndex = selectedIndex }
    DelayedTabSelectionEffect(
        state = state,
        focusedTabIndex = focusedTabIndex,
        selectedIndex = selectedIndex,
        onAction = onAction,
    )
    InitialTabFocusEffect(
        focusedTabIndex = focusedTabIndex,
        tabFocusRequesters = tabFocusRequesters,
        tabRowFocus = tabRowFocus,
        contentFocus = contentFocus,
        focusManager = focusManager,
        lastFocusedRegion = lastFocusedRegion,
    )
    val isOnHome = state.tabs.getOrNull(focusedTabIndex)?.type == TabType.Home

    TopTabBackHandler(
        enabled = isContentFocused || !isOnHome,
        isContentFocused = isContentFocused,
        state = state,
        tabRowFocus = tabRowFocus,
        tabFocusRequesters = tabFocusRequesters,
        onTabsFocused = { lastFocusedRegion = TopTabFocusedRegion.Tabs },
        onHomeFocused = { focusedTabIndex = it },
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

        Column(Modifier.fillMaxSize()) {
            TopTabBar(
                tabs = state.tabs,
                selectedIndex = focusedTabIndex,
                tabFocusRequesters = tabFocusRequesters,
                onContentFocusRequested = requestContentFocus,
                onTabFocused = { index -> focusedTabIndex = index },
                onTabClick = requestContentFocus,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .onFocusChanged {
                        if (it.hasFocus) {
                            isContentFocused = false
                        }
                    },
                tabRowModifier = Modifier
                    .focusRequester(tabRowFocus),
            )

            CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides false) {
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
        }
    }
}

@Composable
private fun SyncSelectedTabEffect(selectedIndex: Int, onSelectedIndexChanged: (Int) -> Unit) {
    LaunchedEffect(selectedIndex) {
        onSelectedIndexChanged(selectedIndex)
    }
}

@Composable
private fun DelayedTabSelectionEffect(
    state: MainViewState,
    focusedTabIndex: Int,
    selectedIndex: Int,
    onAction: (UIAction) -> Unit,
) {
    LaunchedEffect(focusedTabIndex) {
        if (focusedTabIndex != selectedIndex) {
            delay(TAB_SELECTION_DELAY_MS)
            state.tabs.getOrNull(focusedTabIndex)?.let { tab ->
                onAction(CommonAction.ItemSelected(tab))
            }
        }
    }
}

@Composable
private fun InitialTabFocusEffect(
    focusedTabIndex: Int,
    tabFocusRequesters: List<FocusRequester>,
    tabRowFocus: FocusRequester,
    contentFocus: FocusRequester,
    focusManager: FocusManager,
    lastFocusedRegion: TopTabFocusedRegion,
) {
    LaunchedEffect(Unit) {
        delay(INITIAL_TAB_FOCUS_DELAY_MS)
        when (lastFocusedRegion) {
            TopTabFocusedRegion.Tabs -> tabFocusRequesters.getOrNull(focusedTabIndex)?.requestFocus()
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
    tabFocusRequesters: List<FocusRequester>,
    onTabsFocused: () -> Unit,
    onHomeFocused: (Int) -> Unit,
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
    tabFocusRequesters: List<FocusRequester>,
    onHomeFocused: (Int) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    val homeIndex = state.tabs.indexOfFirst { it.type == TabType.Home }.coerceAtLeast(0)
    state.tabs.getOrNull(homeIndex)?.let { homeTab ->
        onHomeFocused(homeIndex)
        onAction(CommonAction.ItemSelected(homeTab))
        tabFocusRequesters.getOrNull(homeIndex)?.requestFocus()
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
    Box(
        Modifier
            .weight(1f)
            .focusRequester(contentFocus)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onFocused()
                }
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        delay(CONTENT_CHILD_FOCUS_DELAY_MS)
                        focusManager.moveFocus(FocusDirection.Down)
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
            .focusRestorer()
            .focusGroup()
    ) {
        PuberCurrentTab()
    }
}

private const val TAB_SELECTION_DELAY_MS = 300L
private const val INITIAL_TAB_FOCUS_DELAY_MS = 100L
private const val CONTENT_CHILD_FOCUS_DELAY_MS = 16L
private const val CONTENT_FOCUS_RESTORE_ATTEMPTS = 5
private const val CONTENT_FOCUS_RESTORE_RETRY_DELAY_MS = 50L

private enum class TopTabFocusedRegion {
    Tabs,
    Content,
}
