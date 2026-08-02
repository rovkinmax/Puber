package com.kino.puber.ui.feature.main.toptabs

import android.app.Activity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpRect
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.navigation.component.PreserveLazyListAnchorOnRootReturn
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.contentlist.content.SectionRowContent
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.ui.feature.home.component.HomeScreenContent
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val FOCUSED_TITLE = "row-4-item-1"
private const val DETAILS_TAG = "details_destination"
private const val BACK_TAG = "details_back"
private const val BOUNDS_TOLERANCE = 1f
private const val MAX_FOCUSED_NODE_DIAGNOSTICS = 5

internal class TopTabDetailsBackFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeDetailsBackRestoresFocusedCardAndViewport() {
        detailsBackRestoresFocusedCardAndViewport(
            tabType = TabType.Home,
            tabScreen = TopTabHomeProbeScreen,
        )
    }

    @Test
    fun nonHomeDetailsBackRestoresFocusedCardAndViewport() {
        detailsBackRestoresFocusedCardAndViewport(
            tabType = TabType.Movies,
            tabScreen = TopTabNonHomeProbeScreen,
        )
    }

    private fun detailsBackRestoresFocusedCardAndViewport(
        tabType: TabType,
        tabScreen: PuberScreen,
    ) {
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val tabRouter = TabRouter(coroutineScope)
        val tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        TopTabDetailsProbeHost.bind(tabRouter, tabAppRouterHolder, tabType)

        try {
            composeRule.setContent {
                PuberTheme {
                    FlowComponent(
                        scopeName = "TopTabDetailsBackFocusTest_${tabType.name}",
                        screen = TopTabDetailsProbeHostScreen,
                        moduleFactory = { scopeId, _ ->
                            module {
                                scope(named(scopeId)) {
                                    scoped<AppLauncher> { TopTabDetailsNoOpAppLauncher }
                                    scoped<Screens> { ScreensImpl }
                                }
                            }
                        },
                    )
                }
            }
            composeRule.runOnIdle {
                tabRouter.openTab(
                    PuberTab(
                        screen = tabScreen,
                        tag = tabType,
                    ),
                )
            }
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasText("row-0-item-0"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.mainClock.advanceTimeBy(200)

            composeRule
                .onNodeWithText("row-0-item-0")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
            repeat(4) { row ->
                composeRule
                    .onNodeWithText("row-$row-item-0")
                    .performDirectionDown()
                composeRule.waitForIdle()
            }
            composeRule
                .onNodeWithText("row-4-item-0")
                .performDirectionRight()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()

            val focusedCard = composeRule.onNodeWithText(FOCUSED_TITLE)
            focusedCard.assertIsFocused()
            val viewportBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
            val settledBounds = focusedCard.getUnclippedBoundsInRoot()
            assertCardInsideViewport(settledBounds, viewportBounds)
            composeRule.waitForIdle()
            val boundsBefore = settledBounds
            val anchorBefore = composeRule.runOnIdle {
                TopTabDetailsProbeHost.anchor(tabType)
            }

            focusedCard.performSelect()
            composeRule.onNodeWithTag(DETAILS_TAG).assertExists()
            composeRule.runOnIdle {
                TopTabDetailsProbeHost.back()
            }
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasTestTag(DETAILS_TAG))
                    .fetchSemanticsNodes()
                    .isEmpty()
            }

            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasText("row-", substring = true))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.waitUntil(timeoutMillis = 1_500) {
                TopTabDetailsProbeHost.anchor(tabType) == anchorBefore
            }
            val anchorAfter = composeRule.runOnIdle {
                TopTabDetailsProbeHost.anchor(tabType)
            }
            assertEquals("lazy anchor", anchorBefore, anchorAfter)
            try {
                composeRule.waitUntil(timeoutMillis = 1_500) {
                    composeRule
                        .onAllNodes(isFocused() and hasText(FOCUSED_TITLE))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError(
                    "Expected $FOCUSED_TITLE after anchor restoration; focused nodes=" +
                        focusedNodeSummary(),
                    error,
                )
            }
            val restoredCard = composeRule.onNode(isFocused() and hasText(FOCUSED_TITLE))
            restoredCard.assertIsFocused()
            val boundsAfter = restoredCard.getUnclippedBoundsInRoot()
            assertRectEquals(boundsBefore, boundsAfter)
        } finally {
            composeRule.runOnIdle { tabAppRouterHolder.dispose() }
            coroutineScope.cancel()
            TopTabDetailsProbeHost.clear()
        }
    }

    private fun assertRectEquals(before: DpRect, after: DpRect) {
        assertEquals("left", before.left.value, after.left.value, BOUNDS_TOLERANCE)
        assertEquals("top", before.top.value, after.top.value, BOUNDS_TOLERANCE)
        assertEquals("right", before.right.value, after.right.value, BOUNDS_TOLERANCE)
        assertEquals("bottom", before.bottom.value, after.bottom.value, BOUNDS_TOLERANCE)
    }

    private fun assertCardInsideViewport(card: DpRect, viewport: DpRect) {
        org.junit.Assert.assertTrue(
            "focused card top ${card.top} is above viewport ${viewport.top}",
            card.top.value >= viewport.top.value - BOUNDS_TOLERANCE,
        )
        org.junit.Assert.assertTrue(
            "focused card bottom ${card.bottom} is below viewport ${viewport.bottom}",
            card.bottom.value <= viewport.bottom.value + BOUNDS_TOLERANCE,
        )
    }

    private fun focusedNodeSummary(): List<String> {
        return composeRule
            .onAllNodes(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .take(MAX_FOCUSED_NODE_DIAGNOSTICS)
            .map { node ->
                val text = node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.joinToString(separator = "|") { it.text }
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                "text=$text, tag=$tag, bounds=${node.boundsInRoot}"
            }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performDirectionDown() {
        performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.DirectionDown)
            keyUp(androidx.compose.ui.input.key.Key.DirectionDown)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performDirectionRight() {
        performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.DirectionRight)
            keyUp(androidx.compose.ui.input.key.Key.DirectionRight)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performSelect() {
        performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.DirectionCenter)
            keyUp(androidx.compose.ui.input.key.Key.DirectionCenter)
        }
    }
}

private object TopTabDetailsProbeHost {
    private var tabRouter: TabRouter? = null
    private var tabAppRouterHolder: TabAppRouterHolder? = null
    private var selectedTab: TabType? = null
    private var rootRouter: AppRouter? = null
    private val lazyListStates = mutableMapOf<TabType, LazyListState>()

    fun bind(
        tabRouter: TabRouter,
        tabAppRouterHolder: TabAppRouterHolder,
        selectedTab: TabType,
    ) {
        this.tabRouter = tabRouter
        this.tabAppRouterHolder = tabAppRouterHolder
        this.selectedTab = selectedTab
    }

    fun clear() {
        tabRouter = null
        tabAppRouterHolder = null
        selectedTab = null
        rootRouter = null
        lazyListStates.clear()
    }

    fun requireTabRouter(): TabRouter = requireNotNull(tabRouter)

    fun requireTabAppRouterHolder(): TabAppRouterHolder = requireNotNull(tabAppRouterHolder)

    fun requireSelectedTab(): TabType = requireNotNull(selectedTab)

    fun recordRootRouter(router: AppRouter) {
        rootRouter = router
    }

    fun recordLazyListState(tabType: TabType, state: LazyListState) {
        lazyListStates[tabType] = state
    }

    fun back() {
        requireNotNull(rootRouter).back()
    }

    fun anchor(tabType: TabType): LazyAnchor {
        val state = requireNotNull(lazyListStates[tabType])
        return LazyAnchor(
            index = state.firstVisibleItemIndex,
            offset = state.firstVisibleItemScrollOffset,
        )
    }
}

@Parcelize
private data object TopTabDetailsProbeHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        val rootRouter = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val selectedTab = TopTabDetailsProbeHost.requireSelectedTab()
        SideEffect {
            TopTabDetailsProbeHost.recordRootRouter(rootRouter)
        }
        TopTabMainContent(
            state = MainViewState(
                tabs = listOf(
                    MainTab(
                        type = TabType.Home,
                        label = "Главная",
                        icon = PhosphorIcons.Duotone.House,
                        isSelected = selectedTab == TabType.Home,
                    ),
                    MainTab(
                        type = TabType.Movies,
                        label = "Фильмы",
                        icon = PhosphorIcons.Duotone.FilmSlate,
                        isSelected = selectedTab == TabType.Movies,
                    ),
                ),
                selectedTab = selectedTab,
            ),
            onAction = {},
            tabRouter = TopTabDetailsProbeHost.requireTabRouter(),
            tabAppRouterHolder = TopTabDetailsProbeHost.requireTabAppRouterHolder(),
            onSearchClick = {},
            onSettingsClick = {},
        )
    }
}

@Parcelize
private data object TopTabHomeProbeScreen : PuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val listState = rememberLazyListState()
        SideEffect {
            TopTabDetailsProbeHost.recordLazyListState(TabType.Home, listState)
        }
        HomeScreenContent(
            state = HomeViewState.Content(
                sections = (0..5).map { row ->
                    HomeSectionState(
                        title = "Row $row",
                        type = HomeSectionType.values()[row],
                        items = (0..2).map { column ->
                            VideoItemUIState(
                                id = row * 10 + column,
                                title = "row-$row-item-$column",
                                imageUrl = "",
                                bigImageUrl = "",
                                showTitle = true,
                            )
                        },
                    )
                },
            ),
            onAction = { action ->
                if (action is CommonAction.ItemSelected<*>) {
                    router.navigateTo(TopTabDetailsProbeDetailsScreen)
                }
            },
            onHeroClick = {},
            onCollectionClick = { _, _ -> },
            lazyListState = listState,
        )
    }
}

@Parcelize
private data object TopTabNonHomeProbeScreen : PuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val listState = rememberLazyListState()
        val focusedSectionIndex = rememberSaveable { mutableIntStateOf(0) }
        PreserveLazyListAnchorOnRootReturn(listState)
        SideEffect {
            TopTabDetailsProbeHost.recordLazyListState(TabType.Movies, listState)
        }
        val rows = (0..5).map { row ->
            NonHomeRow(
                config = SectionConfig(id = "row-$row", title = "Row $row"),
                state = SectionState.Content(
                    items = (0..2).map { column ->
                        VideoItemUIState(
                            id = row * 10 + column,
                            title = "row-$row-item-$column",
                            imageUrl = "",
                            bigImageUrl = "",
                            showTitle = true,
                        )
                    },
                ),
            )
        }

        PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
            ) {
                items(rows, key = { it.config.id }) { row ->
                    val rowIndex = rows.indexOf(row)
                    SectionRowContent(
                        state = row.state,
                        config = row.config,
                        isTargetRow = rowIndex == focusedSectionIndex.intValue,
                        onItemClick = {
                            router.navigateTo(TopTabDetailsProbeDetailsScreen)
                        },
                        onItemContextMenu = {},
                        onItemFocused = {},
                        onSectionFocused = {
                            focusedSectionIndex.intValue = rowIndex
                        },
                        onRetry = {},
                        onLoadMore = {},
                    )
                }
            }
        }
    }
}

@Parcelize
private data object TopTabDetailsProbeDetailsScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        Column {
            Text(text = "Details", modifier = Modifier.testTag(DETAILS_TAG))
            Button(
                onClick = router::back,
                modifier = Modifier.testTag(BACK_TAG),
            ) {
                Text("Back")
            }
        }
    }
}

private data object TopTabDetailsNoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private data class NonHomeRow(
    val config: SectionConfig,
    val state: SectionState.Content,
)

private data class LazyAnchor(
    val index: Int,
    val offset: Int,
)
