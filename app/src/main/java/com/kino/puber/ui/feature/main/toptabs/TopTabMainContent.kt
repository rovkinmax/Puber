package com.kino.puber.ui.feature.main.toptabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
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
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val tabRowFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val selectedIndex = state.tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

    var isContentFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tabRowFocus.requestFocus()
    }
    val isOnHome = state.tabs.getOrNull(selectedIndex)?.type == TabType.Home

    // Back: content → TabRow, TabRow(not Home) → Home, TabRow(Home) → exit
    BackHandler(enabled = isContentFocused || !isOnHome) {
        if (isContentFocused) {
            tabRowFocus.requestFocus()
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
                onTabFocused = { index ->
                    if (index != selectedIndex) {
                        val tab = state.tabs.getOrNull(index) ?: return@TopTabBar
                        onAction(CommonAction.ItemSelected(tab))
                    }
                },
                onTabClick = { contentFocus.requestFocus() },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) isContentFocused = false },
                tabRowModifier = Modifier.focusRequester(tabRowFocus),
            )

            CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides false) {
                Box(
                    Modifier
                        .weight(1f)
                        .focusRequester(contentFocus)
                        .onFocusChanged { if (it.hasFocus) isContentFocused = true }
                        .focusProperties {
                            @Suppress("DEPRECATION")
                            exit = { direction ->
                                if (direction == FocusDirection.Up) tabRowFocus
                                else FocusRequester.Default
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
}
