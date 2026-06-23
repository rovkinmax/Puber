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
    val tabFocusRequesters = remember(state.tabs.size) {
        List(state.tabs.size) { FocusRequester() }
    }
    val selectedIndex = state.tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

    var focusedTabIndex by rememberSaveable { mutableIntStateOf(selectedIndex) }
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
    )
    val isOnHome = state.tabs.getOrNull(focusedTabIndex)?.type == TabType.Home

    TopTabBackHandler(
        enabled = isContentFocused || !isOnHome,
        isContentFocused = isContentFocused,
        state = state,
        tabRowFocus = tabRowFocus,
        tabFocusRequesters = tabFocusRequesters,
        onHomeFocused = { focusedTabIndex = it },
        onAction = onAction,
    )

    TabComponent(
        tabRouter = tabRouter,
        tabAppRouterHolder = tabAppRouterHolder,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopTabBar(
                tabs = state.tabs,
                selectedIndex = focusedTabIndex,
                tabFocusRequesters = tabFocusRequesters,
                contentFocusRequester = contentFocus,
                onTabFocused = { index -> focusedTabIndex = index },
                onTabClick = { contentFocus.requestFocus() },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) isContentFocused = false },
                tabRowModifier = Modifier
                    .focusRequester(tabRowFocus),
            )

            CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides false) {
                TopTabContentBox(
                    contentFocus = contentFocus,
                    tabRowFocus = tabRowFocus,
                    onFocused = { isContentFocused = true },
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
) {
    LaunchedEffect(Unit) {
        delay(INITIAL_TAB_FOCUS_DELAY_MS)
        tabFocusRequesters.getOrNull(focusedTabIndex)?.requestFocus()
            ?: tabRowFocus.requestFocus()
    }
}

@Composable
private fun TopTabBackHandler(
    enabled: Boolean,
    isContentFocused: Boolean,
    state: MainViewState,
    tabRowFocus: FocusRequester,
    tabFocusRequesters: List<FocusRequester>,
    onHomeFocused: (Int) -> Unit,
    onAction: (UIAction) -> Unit,
) {
    BackHandler(enabled = enabled) {
        if (isContentFocused) {
            tabRowFocus.requestFocus()
        } else {
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
                @Suppress("DEPRECATION")
                exit = { direction ->
                    if (direction == FocusDirection.Up) {
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
