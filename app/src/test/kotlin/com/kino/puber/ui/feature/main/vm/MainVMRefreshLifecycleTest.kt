package com.kino.puber.ui.feature.main.vm

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MainVMRefreshLifecycleTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    @Test
    fun rapidRefreshKeepsOneLogicalTabAndAdvancesItsContentGeneration() = runTest(dispatcher) {
        val screens = mockk<Screens>(relaxed = true)
        val router = mockk<AppRouter>(relaxed = true)
        val mapper = mockk<MainUIMapper>()
        val tabRouter = mockk<TabRouter>(relaxed = true)
        val openedTabs = mutableListOf<PuberTab>()
        val historyScreen = mockk<PuberScreen>()
        every { historyScreen.key } returns "HistoryScreen"
        every { router.screens } returns screens
        every { tabRouter.openTab(capture(openedTabs)) } returns Unit
        every {
            mapper.buildTabContent(
                type = TabType.History,
                navigationMode = any(),
                refreshVersion = any(),
            )
        } answers {
            val version = thirdArg<Int>()
            PuberTab(
                screen = historyScreen,
                tag = TabType.History,
                instanceKey = version.takeIf { it > 0 }?.let { "refresh_$it" }.orEmpty(),
            )
        }
        every { mapper.updateSelectedTab(any(), any()) } answers { firstArg() }
        val vm = MainVM(router, mapper, tabRouter)
        val historyTab = MainTab(
            type = TabType.History,
            label = "History",
            icon = PhosphorIcons.Duotone.House,
        )

        vm.onAction(MainAction.RefreshTab(historyTab))
        vm.onAction(MainAction.RefreshTab(historyTab))

        assertEquals(
            listOf("Tab:HistoryScreen", "Tab:HistoryScreen"),
            openedTabs.map(PuberTab::key),
        )
        assertEquals(
            listOf("Tab:HistoryScreen:refresh_1", "Tab:HistoryScreen:refresh_2"),
            openedTabs.map(PuberTab::contentInstanceKey),
        )
        assertSame(
            vm.tabAppRouterHolder.getOrCreate(
                key = openedTabs.first().key,
                initialContentInstanceKey = openedTabs.first().contentInstanceKey,
            ),
            vm.tabAppRouterHolder.getOrCreate(
                key = openedTabs.last().key,
                initialContentInstanceKey = openedTabs.last().contentInstanceKey,
            ),
        )
    }
}
