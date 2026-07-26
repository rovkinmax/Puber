package com.kino.puber.core.ui.navigation.component

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.FullscreenPuberScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.main.model.TabType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

private const val HISTORY_TAG = "probe_history"
private const val CHILD_BUTTON_TAG = "probe_child"
private const val PLAYER_BUTTON_TAG = "probe_player"
private const val DETAILS_BUTTON_TAG = "probe_details"
private const val DESTINATION_TAG = "probe_destination"
private const val BACK_BUTTON_TAG = "probe_back"
private const val OTHER_TAB_TAG = "probe_other_tab"

internal class TabFlowLifecycleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var tabRouter: TabRouter
    private lateinit var tabAppRouterHolder: TabAppRouterHolder

    @Before
    fun setUp() {
        ProbeHistoryVM.reset()
        ProbeNavigatorRegistry.reset()
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tabRouter = TabRouter(coroutineScope)
        tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        ProbeMainHost.bind(tabRouter, tabAppRouterHolder)
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        ProbeMainHost.clear()
        coroutineScope.cancel()
    }

    @Test
    fun refreshThenPlayerAndDetailsBackUsesTheRefreshedHistoryLifecycle() {
        composeRule.setContent {
            FlowComponent(
                scopeName = "TabFlowLifecycleTest",
                screen = ProbeMainHostScreen,
                moduleFactory = { scopeId, _ ->
                    module {
                        scope(named(scopeId)) {
                            scoped<AppLauncher> { ProbeAppLauncher }
                            scoped<Screens> { ScreensImpl }
                        }
                    }
                },
            )
        }
        val initialTab = PuberTab(
            screen = ProbeHistoryScreen(contentGeneration = 0),
            tag = TabType.History,
        )
        val refreshedTab = PuberTab(
            screen = ProbeHistoryScreen(contentGeneration = 1),
            tag = TabType.History,
            instanceKey = "refresh_1",
        )
        val refreshedAgainTab = PuberTab(
            screen = ProbeHistoryScreen(contentGeneration = 2),
            tag = TabType.History,
            instanceKey = "refresh_2",
        )
        val otherTab = PuberTab(
            screen = ProbeOtherScreen,
            tag = TabType.Home,
        )

        composeRule.runOnIdle { tabRouter.openTab(initialTab) }
        composeRule.onNodeWithTag(HISTORY_TAG).assertTextContains("content=0")

        composeRule.onNodeWithTag(CHILD_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(DESTINATION_TAG).assertTextContains("Child")
        composeRule.runOnIdle { tabRouter.openTab(otherTab) }
        composeRule.onNodeWithTag(OTHER_TAB_TAG).assertTextContains("Other")
        composeRule.runOnIdle { tabRouter.openTab(initialTab) }
        composeRule.onNodeWithTag(DESTINATION_TAG).assertTextContains("Child")
        composeRule.onNodeWithTag(BACK_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_TAG).assertTextContains("content=0")

        composeRule.runOnIdle { tabRouter.openTab(refreshedTab) }
        composeRule.onNodeWithTag(HISTORY_TAG).assertTextContains("content=1")
        composeRule.waitUntil { ProbeHistoryVM.clearedGenerations() == setOf(1) }
        composeRule.runOnIdle { tabRouter.openTab(refreshedAgainTab) }
        composeRule.onNodeWithTag(HISTORY_TAG).assertTextContains("content=2")
        composeRule.waitUntil { ProbeHistoryVM.clearedGenerations() == setOf(1, 2) }
        composeRule.runOnIdle {
            assertSame(
                ProbeNavigatorRegistry.historyNavigator(0),
                ProbeNavigatorRegistry.historyNavigator(1),
            )
            assertSame(
                ProbeNavigatorRegistry.historyNavigator(1),
                ProbeNavigatorRegistry.historyNavigator(2),
            )
        }

        openDestinationAndReturn(PLAYER_BUTTON_TAG, "Player")
        openDestinationAndReturn(DETAILS_BUTTON_TAG, "Details")

        composeRule.waitUntil {
            ProbeHistoryVM.createdCount() - ProbeHistoryVM.clearedGenerations().size == 1
        }
        composeRule.runOnIdle {
            assertEquals(
                1,
                ProbeHistoryVM.createdCount() - ProbeHistoryVM.clearedGenerations().size,
            )
        }
    }

    private fun openDestinationAndReturn(
        actionTag: String,
        destination: String,
    ) {
        composeRule.onNodeWithTag(actionTag).performClick()
        composeRule
            .onNodeWithTag(DESTINATION_TAG)
            .assertTextContains(destination)
        composeRule.onNodeWithTag(BACK_BUTTON_TAG).performClick()
        composeRule
            .onNodeWithTag(HISTORY_TAG)
            .assertTextContains("content=2")
    }
}

private data object ProbeAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private object ProbeMainHost {
    var tabRouter: TabRouter? = null
    var tabAppRouterHolder: TabAppRouterHolder? = null

    fun bind(
        tabRouter: TabRouter,
        tabAppRouterHolder: TabAppRouterHolder,
    ) {
        this.tabRouter = tabRouter
        this.tabAppRouterHolder = tabAppRouterHolder
    }

    fun clear() {
        tabRouter = null
        tabAppRouterHolder = null
    }
}

@Parcelize
private data object ProbeMainHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        TabComponent(
            tabRouter = requireNotNull(ProbeMainHost.tabRouter),
            tabAppRouterHolder = requireNotNull(ProbeMainHost.tabAppRouterHolder),
        ) {
            PuberCurrentTab()
        }
    }
}

@Parcelize
private data class ProbeHistoryScreen(
    private val contentGeneration: Int,
) : PuberScreen {

    @IgnoredOnParcel
    override val key = "ProbeHistoryScreen"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::ProbeHistoryVM)
        }
    }

    @Composable
    override fun Content() = DIScope(
        scopeName = key,
        moduleFactory = ::buildModule,
    ) {
        val vm = puberViewModel<ProbeHistoryVM>()
        val navigator = LocalNavigator.currentOrThrow
        SideEffect {
            ProbeNavigatorRegistry.recordHistory(contentGeneration, navigator)
        }
        Column {
            Text(
                text = "content=$contentGeneration vm=${vm.generation}",
                modifier = Modifier.testTag(HISTORY_TAG),
            )
            Button(
                modifier = Modifier.testTag(CHILD_BUTTON_TAG),
                onClick = vm::openChild,
            ) {
                Text("Open Child")
            }
            Button(
                modifier = Modifier.testTag(PLAYER_BUTTON_TAG),
                onClick = vm::openPlayer,
            ) {
                Text("Open Player")
            }
            Button(
                modifier = Modifier.testTag(DETAILS_BUTTON_TAG),
                onClick = vm::openDetails,
            ) {
                Text("Open Details")
            }
        }
    }
}

@Parcelize
private data object ProbeOtherScreen : PuberScreen {
    @Composable
    override fun Content() {
        Text(
            text = "Other",
            modifier = Modifier.testTag(OTHER_TAB_TAG),
        )
    }
}

private class ProbeHistoryVM(
    private val router: AppRouter,
) : ViewModel() {
    val generation: Int = generations.incrementAndGet()

    fun openChild() {
        router.navigateTo(ProbeChildScreen)
    }

    fun openPlayer() {
        router.navigateTo(ProbePlayerScreen)
    }

    fun openDetails() {
        router.navigateTo(ProbeDetailsScreen)
    }

    override fun onCleared() {
        cleared += generation
    }

    companion object {
        private val generations = AtomicInteger()
        private val cleared = ConcurrentHashMap.newKeySet<Int>()

        fun reset() {
            generations.set(0)
            cleared.clear()
        }

        fun createdCount(): Int = generations.get()

        fun clearedGenerations(): Set<Int> = cleared.toSet()
    }
}

@Parcelize
private data object ProbeChildScreen : PuberScreen {
    @Composable
    override fun Content() {
        ProbeDestination("Child")
    }
}

private object ProbeNavigatorRegistry {
    private val historyNavigators = ConcurrentHashMap<Int, Navigator>()

    fun reset() {
        historyNavigators.clear()
    }

    fun recordHistory(generation: Int, navigator: Navigator) {
        historyNavigators[generation] = navigator
    }

    fun historyNavigator(generation: Int): Navigator? = historyNavigators[generation]
}

@Parcelize
private data object ProbePlayerScreen : FullscreenPuberScreen {
    @Composable
    override fun Content() {
        ProbeDestination("Player")
    }
}

@Parcelize
private data object ProbeDetailsScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        ProbeDestination("Details")
    }
}

@Composable
private fun ProbeDestination(destination: String) {
    val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
    Column {
        Text(
            text = destination,
            modifier = Modifier.testTag(DESTINATION_TAG),
        )
        Button(
            modifier = Modifier.testTag(BACK_BUTTON_TAG),
            onClick = router::back,
        ) {
            Text("Back")
        }
    }
}
