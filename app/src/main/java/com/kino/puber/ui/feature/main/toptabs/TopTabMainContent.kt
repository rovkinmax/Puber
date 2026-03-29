package com.kino.puber.ui.feature.main.toptabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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

@Composable
internal fun TopTabMainContent(
    state: MainViewState,
    onAction: (UIAction) -> Unit,
    tabRouter: TabRouter,
) {
    val tabBarFocus = remember { FocusRequester() }
    val contentFocus = rememberFocusRequesterOnLaunch()
    val selectedIndex = state.tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

    BackHandler {
        val selectedTab = state.tabs.getOrNull(selectedIndex)
        if (selectedTab?.type != TabType.Home) {
            // Switch to Home tab
            val homeTab = state.tabs.firstOrNull { it.type == TabType.Home }
            if (homeTab != null) {
                onAction(CommonAction.ItemSelected(homeTab))
            }
        }
        // If already on Home — let system handle back (exit)
    }

    TabComponent(tabRouter = tabRouter) {
        Column(Modifier.fillMaxSize()) {
            TopTabBar(
                tabs = state.tabs,
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    val tab = state.tabs.getOrNull(index) ?: return@TopTabBar
                    onAction(CommonAction.ItemSelected(tab))
                },
                modifier = Modifier.focusRequester(tabBarFocus),
            )

            Box(
                Modifier
                    .weight(1f)
                    .focusRequester(contentFocus)
                    .focusRestorer()
                    .focusGroup()
            ) {
                PuberCurrentTab()
            }
        }
    }
}
