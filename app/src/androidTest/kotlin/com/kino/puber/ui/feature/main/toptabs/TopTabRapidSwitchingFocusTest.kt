package com.kino.puber.ui.feature.main.toptabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.House
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class TopTabRapidSwitchingFocusTest {

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
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        coroutineScope.cancel()
    }

    @Test
    fun oneToThreeTransitionsPerSecondConvergeAtEverySettlePoint() {
        var selectedTabFromAction = TabType.Home
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PuberTheme {
                DIScope(scopeName = "TopTabRapidSwitchingFocusTest") {
                    RapidSwitchingHost(
                        tabRouter = tabRouter,
                        tabAppRouterHolder = tabAppRouterHolder,
                        onTabSelected = { selectedTabFromAction = it },
                    )
                }
            }
        }
        composeRule.runOnIdle {
            tabRouter.openTab(
                PuberTab(
                    screen = TopTabRapidProbeScreen(TabType.Home),
                    tag = TabType.Home,
                ),
            )
        }
        composeRule.mainClock.advanceTimeBy(INITIAL_SETTLE_MS)
        composeRule.waitForIdle()

        assertConverged(TabType.Home, selectedTabFromAction)

        transitionAndAssert(Key.DirectionRight, THREE_TRANSITIONS_PER_SECOND_MS, TabType.Movies) {
            selectedTabFromAction
        }
        transitionAndAssert(Key.DirectionRight, THREE_TRANSITIONS_PER_SECOND_MS, TabType.Series) {
            selectedTabFromAction
        }
        transitionAndAssert(Key.DirectionLeft, TWO_TRANSITIONS_PER_SECOND_MS, TabType.Movies) {
            selectedTabFromAction
        }
        transitionAndAssert(Key.DirectionLeft, TWO_TRANSITIONS_PER_SECOND_MS, TabType.Home) {
            selectedTabFromAction
        }
        transitionAndAssert(Key.DirectionRight, ONE_TRANSITION_PER_SECOND_MS, TabType.Movies) {
            selectedTabFromAction
        }
        transitionAndAssert(Key.DirectionRight, ONE_TRANSITION_PER_SECOND_MS, TabType.Series) {
            selectedTabFromAction
        }
    }

    private fun transitionAndAssert(
        key: Key,
        intervalMillis: Long,
        expectedTab: TabType,
        selectedTab: () -> TabType,
    ) {
        focusedTab(tabLabel(expectedTab.previous(key))).performDirection(key)
        composeRule.mainClock.advanceTimeBy(intervalMillis)
        composeRule.waitForIdle()
        assertConverged(expectedTab, selectedTab())
    }

    private fun assertConverged(expectedTab: TabType, selectedTab: TabType) {
        rapidTabTypes.forEach { tabType ->
            val label = tabLabel(tabType)
            if (tabType == expectedTab) {
                focusedTab(label).assertIsFocused()
                selectedTab(label).assertExists()
            } else {
                focusedTab(label).assertDoesNotExist()
                selectedTab(label).assertDoesNotExist()
            }
        }
        composeRule
            .onNodeWithTag("tab_content_${expectedTab.name.lowercase()}")
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(expectedTab, selectedTab)
        }
    }

    private fun focusedTab(label: String) = composeRule.onNode(
        isFocused() and tabWithLabel(label),
        useUnmergedTree = true,
    )

    private fun selectedTab(label: String) = composeRule.onNode(
        isSelected() and tabWithLabel(label),
        useUnmergedTree = true,
    )

    private fun tabWithLabel(label: String) = hasAnyDescendant(hasText(label))

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performDirection(key: Key) =
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
}

@Composable
private fun RapidSwitchingHost(
    tabRouter: TabRouter,
    tabAppRouterHolder: TabAppRouterHolder,
    onTabSelected: (TabType) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(TabType.Home) }
    TopTabMainContent(
        state = MainViewState(
            tabs = rapidTabs(selectedTab),
            selectedTab = selectedTab,
        ),
        onAction = { action ->
            if (action is CommonAction.ItemSelected<*>) {
                val tab = action.item as MainTab
                selectedTab = tab.type
                onTabSelected(tab.type)
                tabRouter.openTab(
                    PuberTab(
                        screen = TopTabRapidProbeScreen(tab.type),
                        tag = tab.type,
                    ),
                )
            }
        },
        tabRouter = tabRouter,
        tabAppRouterHolder = tabAppRouterHolder,
    )
}

private fun rapidTabs(selectedTab: TabType): List<MainTab> {
    return listOf(
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
        MainTab(
            type = TabType.Series,
            label = "Сериалы",
            icon = PhosphorIcons.Duotone.TelevisionSimple,
            isSelected = selectedTab == TabType.Series,
        ),
    )
}

private val rapidTabTypes = listOf(TabType.Home, TabType.Movies, TabType.Series)

private fun tabLabel(tabType: TabType): String = when (tabType) {
    TabType.Home -> "Главная"
    TabType.Movies -> "Фильмы"
    TabType.Series -> "Сериалы"
    else -> error("Unsupported rapid-tab fixture type: $tabType")
}

private fun TabType.previous(direction: Key): TabType = when (direction) {
    Key.DirectionRight -> when (this) {
        TabType.Movies -> TabType.Home
        TabType.Series -> TabType.Movies
        else -> error("No rapid-tab predecessor for $this moving right")
    }
    Key.DirectionLeft -> when (this) {
        TabType.Home -> TabType.Movies
        TabType.Movies -> TabType.Series
        else -> error("No rapid-tab predecessor for $this moving left")
    }
    else -> error("Unsupported rapid-tab direction: $direction")
}

@Parcelize
private data class TopTabRapidProbeScreen(
    val tabType: TabType,
) : PuberScreen {
    @IgnoredOnParcel
    override val key = "TopTabRapidProbeScreen_${tabType.name}"

    @Composable
    override fun Content() {
        Text(
            text = tabType.name,
            modifier = Modifier.testTag("tab_content_${tabType.name.lowercase()}"),
        )
    }
}

private const val INITIAL_SETTLE_MS = 500L
private const val THREE_TRANSITIONS_PER_SECOND_MS = 334L
private const val TWO_TRANSITIONS_PER_SECOND_MS = 500L
private const val ONE_TRANSITION_PER_SECOND_MS = 1_000L
