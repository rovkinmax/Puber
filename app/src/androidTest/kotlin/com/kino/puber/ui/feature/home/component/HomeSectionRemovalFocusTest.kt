package com.kino.puber.ui.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.ui.feature.main.toptabs.TopTabMainContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class HomeSectionRemovalFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var tabRouter: TabRouter
    private lateinit var tabAppRouterHolder: TabAppRouterHolder

    @Before
    fun setUp() {
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tabRouter = TabRouter(coroutineScope)
        tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        TopTabHomeRemovalProbeHost.reset()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        TopTabHomeRemovalProbeHost.reset()
        coroutineScope.cancel()
    }

    @Test
    fun removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 1,
            expectedId = 2,
        )
    }

    @Test
    fun removingFocusedMiddleCardFocusesTheRightNeighborAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3, 4),
            removedId = 2,
            expectedId = 3,
        )
    }

    @Test
    fun removingFocusedLastCardFocusesThePreviousCardAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 3,
            expectedId = 2,
        )
    }

    @Test
    fun removingCardWhileSearchIsFocusedDoesNotStealTopChromeFocus() {
        val previousIds = listOf(1, 2, 3)
        setTopTabHomeContent(
            homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds,
                    ),
                ),
            )
        )

        focusCard(previousIds = previousIds, targetId = 2)
        val productionSearch = productionSearchControl()
        productionSearch
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            TopTabHomeRemovalProbeHost.state = homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds.filterNot { it == 2 },
                    ),
                ),
            )
        }

        productionSearch.assertIsFocused()
        composeRule.onNodeWithText(cardTitle(3)).assertIsNotFocused()
    }

    @Test
    fun removingTheFocusedSectionSelectsTheReplacementSection() {
        var state by mutableStateOf(
            homeState(
                sections = listOf(
                    section(HomeSectionType.ContinueWatching, "Focused"),
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = state,
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText("Focused card")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle {
            state = homeState(
                sections = listOf(
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            )
        }

        composeRule.onNodeWithText("Replacement card").assertIsFocused()
    }

    private fun assertRetainedRowRemovalFocus(
        previousIds: List<Int>,
        removedId: Int,
        expectedId: Int,
    ) {
        setTopTabHomeContent(
            homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds,
                    ),
                ),
            )
        )

        val productionSearch = productionSearchControl()
        productionSearch
            .assertExists()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()

        focusCard(previousIds = previousIds, targetId = removedId)
        productionSearch.assertIsNotFocused()

        composeRule.runOnIdle {
            TopTabHomeRemovalProbeHost.state = homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds.filterNot { it == removedId },
                    ),
                ),
            )
        }

        composeRule.onNodeWithText(cardTitle(expectedId)).assertIsFocused()
        productionSearch.assertIsNotFocused()
    }

    private fun focusCard(
        previousIds: List<Int>,
        targetId: Int,
    ) {
        val targetIndex = previousIds.indexOf(targetId)
        check(targetIndex >= 0)
        composeRule
            .onNodeWithText(cardTitle(previousIds.first()))
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        repeat(targetIndex) { index ->
            composeRule
                .onNodeWithText(cardTitle(previousIds[index]))
                .performKeyInput {
                    keyDown(Key.DirectionRight)
                    keyUp(Key.DirectionRight)
                }
            composeRule
                .onNodeWithText(cardTitle(previousIds[index + 1]))
                .assertIsFocused()
        }
    }

    private fun setTopTabHomeContent(state: HomeViewState) {
        TopTabHomeRemovalProbeHost.state = state
        composeRule.setContent {
            PuberTheme {
                DIScope(scopeName = "HomeSectionRemovalFocusTest") {
                    TopTabMainContent(
                        state = homeMainState(),
                        onAction = {},
                        tabRouter = tabRouter,
                        tabAppRouterHolder = tabAppRouterHolder,
                        onSearchClick = {},
                        onSettingsClick = {},
                    )
                }
            }
        }
        composeRule.runOnIdle {
            tabRouter.openTab(
                PuberTab(
                    screen = TopTabHomeRemovalProbeScreen,
                    tag = TabType.Home,
                ),
            )
        }
        composeRule.waitUntil {
            composeRule
                .onAllNodes(hasText("Retained card", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitUntil {
            composeRule
                .onAllNodes(
                    matcher = isFocused() and hasAnyDescendant(hasText("Home")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun productionSearchControl() = composeRule.onNode(
        matcher = hasClickAction() and hasAnyDescendant(hasContentDescription("Search")),
        useUnmergedTree = true,
    )

    private fun homeMainState() = MainViewState(
        tabs = listOf(
            MainTab(
                type = TabType.Home,
                label = "Home",
                icon = PhosphorIcons.Duotone.House,
                isSelected = true,
            ),
        ),
        selectedTab = TabType.Home,
    )

    @Parcelize
    private data object TopTabHomeRemovalProbeScreen : PuberScreen {
        @Composable
        override fun Content() {
            HomeScreenContent(
                state = TopTabHomeRemovalProbeHost.state,
                onAction = {},
                onHeroClick = {},
                onCollectionClick = { _, _ -> },
            )
        }
    }

    private fun homeState(
        sections: List<HomeSectionState>,
    ) = HomeViewState.Content(sections = sections)

    private fun section(
        type: HomeSectionType,
        title: String,
        itemIds: List<Int> = listOf(title.hashCode()),
    ) = HomeSectionState(
        title = "$title section",
        type = type,
        items = itemIds.map { id ->
            VideoItemUIState(
                id = id,
                title = if (itemIds.size == 1 && id == title.hashCode()) {
                    "$title card"
                } else {
                    cardTitle(id)
                },
                imageUrl = "",
                bigImageUrl = "",
            )
        },
    )

    private companion object {
        fun cardTitle(id: Int) = "Retained card $id"
    }
}

private object TopTabHomeRemovalProbeHost {
    var state by mutableStateOf<HomeViewState>(HomeViewState.Content())

    fun reset() {
        state = HomeViewState.Content()
    }
}
