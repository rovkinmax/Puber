package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.ui.feature.history.component.HistoryScreen
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.main.model.TabType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class TabFlowLifecycleKeyTest {

    @Test
    fun refreshedHistoryTabNamespacesScreenLifecycleAndReusesTheLogicalNavigatorSlot() {
        val historyScreen = HistoryScreen(HistoryPresentation.TopTabs)
        val historyKey = historyScreen.key
        val initialTab = PuberTab(
            screen = historyScreen,
            tag = TabType.History,
        )
        val refreshedTab = PuberTab(
            screen = HistoryScreen(HistoryPresentation.TopTabs),
            tag = TabType.History,
            instanceKey = "refresh_2",
        )

        assertEquals(initialTab.key, refreshedTab.key)
        assertNotEquals(initialTab.contentInstanceKey, refreshedTab.contentInstanceKey)
        assertNotEquals(
            tabRootScreenKey(initialTab.contentInstanceKey, historyKey),
            tabRootScreenKey(refreshedTab.contentInstanceKey, historyKey),
        )
        assertEquals(
            tabFlowNavigatorKey(initialTab.navigationSlotKey),
            tabFlowNavigatorKey(refreshedTab.navigationSlotKey),
        )
        assertEquals(
            "TabRoot:${refreshedTab.contentInstanceKey}:$historyKey",
            tabRootScreenKey(refreshedTab.contentInstanceKey, historyKey),
        )
    }

    @Test
    fun historyPresentationsUseDistinctLifecycleAndRestorationNamespaces() {
        val topTabsScreen = HistoryScreen(HistoryPresentation.TopTabs)
        val sideDrawerScreen = HistoryScreen(HistoryPresentation.SideDrawer)
        val topTabs = PuberTab(
            screen = topTabsScreen,
            tag = TabType.History,
        )
        val sideDrawer = PuberTab(
            screen = sideDrawerScreen,
            tag = TabType.History,
        )

        assertEquals("HistoryScreen_TopTabs", topTabsScreen.key)
        assertEquals("HistoryScreen_SideDrawer", sideDrawerScreen.key)
        assertNotEquals(topTabsScreen.key, sideDrawerScreen.key)
        assertNotEquals(topTabs.key, sideDrawer.key)
        assertNotEquals(topTabs.navigationSlotKey, sideDrawer.navigationSlotKey)
        assertNotEquals(topTabs.contentInstanceKey, sideDrawer.contentInstanceKey)
        assertNotEquals(
            tabRootScreenKey(topTabs.contentInstanceKey, topTabsScreen.key),
            tabRootScreenKey(sideDrawer.contentInstanceKey, sideDrawerScreen.key),
        )
        assertNotEquals(
            tabFlowNavigatorKey(topTabs.navigationSlotKey),
            tabFlowNavigatorKey(sideDrawer.navigationSlotKey),
        )
    }
}
