package com.kino.puber.core.ui.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabCommand
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.compose.koinInject

internal val LocalTabAppRouterHolder = staticCompositionLocalOf<TabAppRouterHolder?> { null }

internal class TabAppRouterHolder(private val screens: Screens) {
    private data class Entry(val router: AppRouter, val scope: CoroutineScope)

    private val entries = mutableMapOf<ScreenKey, Entry>()

    fun getOrCreate(key: ScreenKey): AppRouter {
        return entries.getOrPut(key) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            Entry(
                router = AppRouter(coroutineScope = scope, screens = screens),
                scope = scope,
            )
        }.router
    }

    fun dispose(key: ScreenKey) {
        entries.remove(key)?.scope?.cancel()
    }

    fun dispose() {
        entries.values.forEach { it.scope.cancel() }
        entries.clear()
    }
}

@Composable
internal fun TabComponent(
    tabRouter: TabRouter = koinInject(),
    tabAppRouterHolder: TabAppRouterHolder? = null,
    content: @Composable () -> Unit,
) {
    val parentScope = LocalPuberKoinScope.current
    val screens: Screens? = remember(parentScope) { parentScope?.getOrNull() }
    val rememberedHolder = remember(screens, tabAppRouterHolder) {
        if (tabAppRouterHolder == null) {
            screens?.let { TabAppRouterHolder(it) }
        } else {
            null
        }
    }
    val holder = tabAppRouterHolder ?: rememberedHolder
    DisposableEffect(rememberedHolder) {
        onDispose {
            rememberedHolder?.dispose()
        }
    }

    val scopeName = parentScope?.id ?: ""
    CompositionLocalProvider(LocalTabAppRouterHolder provides holder) {
        TabNavigator(
            tab = LoadingTab,
            key = scopeName,
        ) {
            val navigator = LocalTabNavigator.current
            LaunchedEffect(tabRouter) {
                tabRouter.events()
                    .collect { event ->
                        when (event) {
                            is TabCommand.Open -> navigator.current = event.tab
                        }
                    }
            }
            content()
        }
    }
}

@Composable
fun PuberCurrentTab() {
    val tabNavigator = LocalTabNavigator.current
    val currentTab = tabNavigator.current

    key(currentTab.key) {
        tabNavigator.saveableState(currentTab.key) {
            currentTab.Content()
        }
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
