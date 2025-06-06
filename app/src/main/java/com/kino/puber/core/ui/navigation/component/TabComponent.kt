package com.kino.puber.core.ui.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.kino.puber.core.ui.navigation.TabCommand
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import org.koin.compose.LocalKoinScope
import org.koin.compose.koinInject

@Composable
fun TabComponent(content: @Composable () -> Unit) {
    val scopeName = LocalKoinScope.current.id
    TabNavigator(
        tab = LoadingTab,
        key = scopeName,
    ) {
        val navigator = LocalTabNavigator.current
        val router = koinInject<TabRouter>()
        LaunchedEffect(scopeName) {
            router.events()
                .collect { event ->
                    when (event) {
                        is TabCommand.Open -> navigator.current = event.tab
                    }
                }
        }
        content()
    }
}

@Composable
fun OsomeCurrentTab() {
    val tabNavigator = LocalTabNavigator.current
    val currentTab = tabNavigator.current

    tabNavigator.saveableState("currentTab") {
        currentTab.Content()
    }
}

private object LoadingTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 0U, title = "Loading", icon = null)

    @Composable
    override fun Content() {
        FullScreenProgressIndicator()
    }
}