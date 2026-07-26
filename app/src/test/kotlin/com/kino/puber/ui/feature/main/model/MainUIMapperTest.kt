package com.kino.puber.ui.feature.main.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.history.component.HistoryScreen
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.util.FakeResourceProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MainUIMapperTest {

    private val mapper = MainUIMapper(
        resources = FakeResourceProvider(),
        screens = ScreensImpl,
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true),
    )

    @Test
    fun historyTab_resolvesToHistoryScreen() {
        val screens = mockk<Screens>()
        every { screens.history(any()) } answers { HistoryScreen(firstArg()) }
        val topTabsMapper = createMapper(
            navPrefs = mockk(relaxed = true),
            screens = screens,
        )

        val tab = topTabsMapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.TopTabs,
        )

        assertHistoryTab(tab, HistoryPresentation.TopTabs)
        verify(exactly = 1) {
            screens.history(HistoryPresentation.TopTabs)
        }
        assertTrue(TabType.History.enabled)
    }

    @Test
    fun selectingHistory_opensHistoryContentInTopTabsState() {
        val home = mainTab(TabType.Home, isSelected = true)
        val history = mainTab(TabType.History)
        val selected = mapper.updateSelectedTab(
            state = MainViewState(
                tabs = listOf(home, history),
                selectedTab = TabType.Home,
            ),
            tab = history,
        )

        assertEquals(TabType.History, selected.selectedTab)
        assertEquals(listOf(false, true), selected.tabs.map(MainTab::isSelected))
        assertHistoryTab(
            tab = mapper.buildTabContent(
                type = selected.selectedTab,
                navigationMode = NavigationMode.TopTabs,
            ),
            presentation = HistoryPresentation.TopTabs,
        )
    }

    @Test
    fun sideDrawerState_resolvesTheSameHistoryScreen() {
        val navPrefs = mockk<NavigationPreferencesRepository>()
        every { navPrefs.getNavigationMode() } returns NavigationMode.SideDrawer
        every {
            navPrefs.getVisibleTabs(NavigationMode.SideDrawer)
        } returns listOf(TabType.Favourites, TabType.History, TabType.Movies)
        val screens = mockk<Screens>()
        every { screens.history(any()) } answers { HistoryScreen(firstArg()) }
        val sideDrawerMapper = createMapper(navPrefs, screens)

        val state = sideDrawerMapper.buildViewState()

        assertEquals(NavigationMode.SideDrawer, state.navigationMode)
        assertEquals(TabType.Favourites, state.selectedTab)
        assertEquals(
            listOf(TabType.Favourites, TabType.History, TabType.Movies),
            state.tabs.map(MainTab::type),
        )
        assertEquals(listOf(true, false, false), state.tabs.map(MainTab::isSelected))
        assertHistoryTab(
            tab = sideDrawerMapper.buildTabContent(
                type = TabType.History,
                navigationMode = state.navigationMode,
            ),
            presentation = HistoryPresentation.SideDrawer,
        )
        verify(exactly = 1) {
            screens.history(HistoryPresentation.SideDrawer)
        }
    }

    @Test
    fun historyRefresh_keepsLogicalTabKeyAndAdvancesScreenScopeGeneration() {
        val initial = mapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.TopTabs,
        )
        val sameInstance = mapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.TopTabs,
        )
        val refreshed = mapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.TopTabs,
            refreshVersion = 2,
        )

        assertEquals(initial.key, sameInstance.key)
        assertEquals(
            "Tab:${HistoryScreen(HistoryPresentation.TopTabs).key}",
            initial.key,
        )
        assertEquals(initial.key, refreshed.key)
        assertNotEquals(initial.contentInstanceKey, refreshed.contentInstanceKey)
        assertEquals(
            "Tab:${HistoryScreen(HistoryPresentation.TopTabs).key}:refresh_2",
            refreshed.contentInstanceKey,
        )
        assertEquals(TabType.History, refreshed.tag)
    }

    @Test
    fun historyPresentationsUseDistinctTabLifecycleKeys() {
        val topTabs = mapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.TopTabs,
        )
        val sideDrawer = mapper.buildTabContent(
            type = TabType.History,
            navigationMode = NavigationMode.SideDrawer,
        )

        assertEquals("Tab:HistoryScreen_TopTabs", topTabs.key)
        assertEquals("Tab:HistoryScreen_SideDrawer", sideDrawer.key)
        assertNotEquals(topTabs.key, sideDrawer.key)
        assertNotEquals(topTabs.contentInstanceKey, sideDrawer.contentInstanceKey)
    }

    private fun assertHistoryTab(
        tab: PuberTab,
        presentation: HistoryPresentation,
    ) {
        assertEquals("Tab:${HistoryScreen(presentation).key}", tab.key)
        assertEquals(TabType.History, tab.tag)
    }

    private fun mainTab(
        type: TabType,
        isSelected: Boolean = false,
    ): MainTab {
        return MainTab(
            type = type,
            label = type.name,
            icon = PhosphorIcons.Duotone.House,
            isSelected = isSelected,
        )
    }

    private fun createMapper(
        navPrefs: NavigationPreferencesRepository,
        screens: Screens = ScreensImpl,
    ): MainUIMapper {
        return MainUIMapper(
            resources = FakeResourceProvider(),
            screens = screens,
            navPrefs = navPrefs,
        )
    }
}
