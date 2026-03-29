package com.kino.puber.ui.feature.main.toptabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType

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

    // Track the last user-selected tab to avoid onFocus-triggered switching
    var userSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    val isOnHome = state.tabs.getOrNull(selectedIndex)?.type == TabType.Home

    // Only intercept Back when NOT on Home — switch to Home tab.
    // When on Home, don't set BackHandler at all — system handles exit.
    BackHandler(enabled = !isOnHome) {
        val homeTab = state.tabs.firstOrNull { it.type == TabType.Home }
        if (homeTab != null) {
            onAction(CommonAction.ItemSelected(homeTab))
        }
    }

    TabComponent(tabRouter = tabRouter) {
        Column(Modifier.fillMaxSize()) {
            TopTabBar(
                tabs = state.tabs,
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    if (index != userSelectedIndex) {
                        userSelectedIndex = index
                        val tab = state.tabs.getOrNull(index) ?: return@TopTabBar
                        onAction(CommonAction.ItemSelected(tab))
                    }
                },
                modifier = Modifier.focusRequester(tabBarFocus),
            )

            Box(
                Modifier
                    .weight(1f)
                    .focusRequester(contentFocus)
                    .focusProperties {
                        exit = { direction ->
                            // Block upward focus escape from content to TabRow
                            if (direction == FocusDirection.Up) {
                                FocusRequester.Cancel
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
