package com.kino.puber.ui.feature.main.toptabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TopTabMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
) {
    val tabBarFocus = remember { FocusRequester() }
    val contentFocus = rememberFocusRequesterOnLaunch()
    val selectedIndex = state.tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()

    var isContentFocused by remember { mutableStateOf(true) }
    var pendingSelectionJob by remember { mutableStateOf<Job?>(null) }

    val isOnHome = state.tabs.getOrNull(selectedIndex)?.type == TabType.Home

    // Back logic:
    // Content focused → move focus to TabRow
    // TabRow focused, not Home → switch to Home
    // TabRow focused, Home → system exit (BackHandler disabled)
    BackHandler(enabled = isContentFocused || !isOnHome) {
        if (isContentFocused) {
            tabBarFocus.requestFocus()
        } else {
            val homeTab = state.tabs.firstOrNull { it.type == TabType.Home }
            if (homeTab != null) {
                onAction(CommonAction.ItemSelected(homeTab))
            }
        }
    }

    TabComponent(tabRouter = tabRouter) {
        Column(Modifier.fillMaxSize()) {
            TopTabBar(
                tabs = state.tabs,
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    // Debounce: only switch tab if focus stays on it for 300ms.
                    // Prevents bouncing when scrolling through tabs quickly
                    // and when content auto-focus triggers onFocus on wrong tab.
                    pendingSelectionJob?.cancel()
                    if (index != selectedIndex) {
                        pendingSelectionJob = scope.launch {
                            delay(TAB_SELECTION_DEBOUNCE_MS)
                            val tab = state.tabs.getOrNull(index) ?: return@launch
                            onAction(CommonAction.ItemSelected(tab))
                        }
                    }
                },
                modifier = Modifier
                    .focusRequester(tabBarFocus)
                    .onFocusChanged { if (it.hasFocus) isContentFocused = false },
            )

            Box(
                Modifier
                    .weight(1f)
                    .focusRequester(contentFocus)
                    .onFocusChanged { if (it.hasFocus) isContentFocused = true }
                    .focusProperties {
                        exit = { direction ->
                            if (direction == FocusDirection.Up) {
                                tabBarFocus
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
    }
}

private const val TAB_SELECTION_DEBOUNCE_MS = 300L
