package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.kino.puber.core.ui.navigation.component.OsomeCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.vm.MainVM
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MainScreenComponent() {
    val vm = koinViewModel<MainVM>()
    val state by vm.collectViewState()
    val onAction: (UIAction) -> Unit = remember { vm::onAction }
    MainScreenContent(state, onAction = onAction)
}


@Composable
private fun MainScreenContent(state: MainViewState, onAction: (UIAction) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val mainContentFocus = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    SideEffect {
        coroutineScope.launch {
            mainContentFocus.requestFocus()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainSideMenuContent(
                state = state,
                onAction = onAction,
                drawerState = drawerState,
                mainContentFocus = mainContentFocus,
            )
        },
        content = { MainScreenContentBody(mainContentFocus) }
    )
}

@Composable
private fun NavigationDrawerScope.MainSideMenuContent(
    state: MainViewState,
    drawerState: DrawerState,
    mainContentFocus: FocusRequester,
    onAction: (UIAction) -> Unit
) {
    val tabFocusRequesters = remember(state.tabs.map { it.type }) {
        state.tabs.map { it.type }.associateWith { FocusRequester() }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp)
            .selectableGroup()
            .verticalScroll(rememberScrollState())
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    val selectedTab = state.tabs.first { it.isSelected }
                    tabFocusRequesters[selectedTab.type]?.requestFocus()
                }
            }
            .focusable(),
        horizontalAlignment = Alignment.Start,
    ) {
        state.tabs.forEachIndexed { index, tab ->
            MainSideMenuItem(
                tabFocusRequester = tabFocusRequesters[tab.type]!!,
                tab = tab,
                onAction = onAction,
                drawerState = drawerState,
                focusRequester = mainContentFocus,
            )
        }
    }
}

@Composable
private fun NavigationDrawerScope.MainSideMenuItem(
    tab: MainTab,
    drawerState: DrawerState,
    tabFocusRequester: FocusRequester,
    focusRequester: FocusRequester,
    onAction: (UIAction) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    NavigationDrawerItem(
        modifier = Modifier
            .height(40.dp)
            .focusRequester(tabFocusRequester),
        selected = tab.isSelected,
        onClick = {
            onAction(CommonAction.ItemSelected(tab))
            drawerState.setValue(DrawerValue.Closed)
            coroutineScope.launch {
                focusRequester.requestFocus()
            }
        },
        leadingContent = {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
            )
        },
        trailingContent = mainSideMenuItemBadge(tab)

    ) {
        Text(text = tab.label)
    }
}

@Composable
private fun mainSideMenuItemBadge(tab: MainTab): @Composable (() -> Unit)? {
    return if (tab.badge > 0) {
        {
            Badge {
                Text(
                    text = tab.badge.toString(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    } else {
        null
    }
}

@Composable
private fun MainScreenContentBody(
    focusRequester: FocusRequester
) {
    val closeDrawerWidth = 80.dp
    val backgroundContentPadding = 10.dp
    TabComponent {
        Box(
            Modifier
                .padding(closeDrawerWidth + backgroundContentPadding)
                .focusRequester(focusRequester)

        ) {
            OsomeCurrentTab()
        }
    }
}