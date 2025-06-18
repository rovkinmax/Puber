package com.kino.puber.ui.feature.main.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.vm.MainVM
import kotlinx.coroutines.delay
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
    var isMainFocusRequested by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.scrim, Color.Transparent
            )
        ),
        drawerContent = {
            MainSideMenuContent(
                state = state,
                onAction = onAction,
                drawerState = drawerState,
                mainContentFocus = mainContentFocus,
            )
        },
        content = { MainScreenContentBody(mainContentFocus) },
    )

    val scope = rememberCoroutineScope()
    SideEffect {
        scope.launch {
            // hack to force focus on launch
            // if you know how to fix it, please do
            delay(100)
            if (isMainFocusRequested.not()) {
                isMainFocusRequested = true
                mainContentFocus.requestFocus()
            }
        }
    }
}

@Composable
private fun NavigationDrawerScope.MainSideMenuContent(
    state: MainViewState,
    drawerState: DrawerState,
    mainContentFocus: FocusRequester,
    onAction: (UIAction) -> Unit
) {

    val fallbackFocusItem = remember { FocusRequester() }
    val backgroundColor = animateColorAsState(
        targetValue = if (drawerState.isOpen) {
            Color.Unspecified
        } else {
            MaterialTheme.colorScheme.surface
        }
    )

    BackHandler(enabled = drawerState.isOpen.not()) {
        drawerState.setValue(DrawerValue.Open)
    }

    Column(
        Modifier
            .background(backgroundColor.value)
            .fillMaxHeight()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
            .focusRestorer(fallbackFocusItem)
            .focusGroup(),
        horizontalAlignment = Alignment.Start,
    ) {
        state.tabs.forEachIndexed { index, tab ->
            MainSideMenuItem(
                tabFocusRequester = fallbackFocusItem,
                tab = tab,
                onAction = onAction,
                drawerState = drawerState,
                mainContentFocus = mainContentFocus,
            )
        }
    }
}

private val DrawerState.isOpen: Boolean
    get() = currentValue == DrawerValue.Open

@Composable
private fun NavigationDrawerScope.MainSideMenuItem(
    tab: MainTab,
    drawerState: DrawerState,
    tabFocusRequester: FocusRequester,
    mainContentFocus: FocusRequester,
    onAction: (UIAction) -> Unit
) {
    val modifier = Modifier.height(40.dp).onFocusChanged { focusState ->
        if (focusState.isFocused) {
            onAction(CommonAction.ItemSelected(tab))
        }
    }.run {
        if (tab.isSelected) {
            focusRequester(tabFocusRequester)
        } else {
            this
        }
    }

    NavigationDrawerItem(
        modifier = modifier,
        selected = tab.isSelected,
        onClick = {
            drawerState.setValue(DrawerValue.Closed)
            mainContentFocus.requestFocus()
        },
        leadingContent = {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
            )
        },
        trailingContent = mainSideMenuItemBadge(tab),
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
    TabComponent {
        Box(
            Modifier
                .padding(start = closeDrawerWidth)
                .selectableGroup()
                .focusRequester(focusRequester)

        ) {
            PuberCurrentTab()
        }
    }
}