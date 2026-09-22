package com.kino.puber.ui.feature.main.vm

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
        val vm = MainVM(
            router = router,
            mainUIMapper = mapper,
            tabRouter = tabRouter,
            navigationPreferencesRepository = mockk(relaxed = true),
            bookmarkPreferencesRepository = BookmarkPreferencesRepository(),
        )
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

    @Test
    fun settingsControl_navigatesToDeviceSettings() {
        val settingsScreen = mockk<PuberScreen>()
        val screens = mockk<Screens>()
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns screens
        every { screens.deviceSettings() } returns settingsScreen
        val vm = MainVM(
            router = router,
            mainUIMapper = mockk(),
            tabRouter = mockk(relaxed = true),
            navigationPreferencesRepository = mockk(relaxed = true),
            bookmarkPreferencesRepository = BookmarkPreferencesRepository(),
        )

        vm.onSettingsClick()

        verify(exactly = 1) { router.navigateTo(settingsScreen) }
    }

    @Test
    fun disablingSelectedOptionalTab_fallsBackAndOpensModeStartTab() = runTest(dispatcher) {
        val preferences = MutableStateFlow(
            ContentPreferences(
                showCartoonsTab = false,
                showAnimeTab = true,
                showAnime = true,
            )
        )
        val repository = mockk<NavigationPreferencesRepository>()
        every { repository.contentPreferences } returns preferences
        val screens = mockk<Screens>(relaxed = true)
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns screens
        val mapper = mockk<MainUIMapper>()
        every {
            mapper.buildViewState(null, BookmarkMode.Simple)
        } returns mainState(TabType.Anime)
        every {
            mapper.buildViewState(TabType.Anime, BookmarkMode.Simple)
        } returns mainState(TabType.Home)
        every { mapper.buildTabContent(any(), any(), any()) } answers {
            puberTab(firstArg())
        }
        val tabRouter = mockk<TabRouter>(relaxed = true)
        val openedTabs = mutableListOf<PuberTab>()
        every { tabRouter.openTab(capture(openedTabs)) } returns Unit
        val vm = MainVM(
            router,
            mapper,
            tabRouter,
            repository,
            BookmarkPreferencesRepository(),
        )

        vm.testOnStart()
        runCurrent()
        preferences.value = preferences.value.copy(showAnimeTab = false)
        runCurrent()

        assertEquals(TabType.Home, vm.testStateValue.selectedTab)
        assertEquals(listOf(TabType.Anime, TabType.Home), openedTabs.map(PuberTab::tag))
        vm.testCancelScope()
    }

    @Test
    fun showAnimeChange_advancesAffectedTabGenerationsAndRefreshesSelectedTab() = runTest(dispatcher) {
        val preferences = MutableStateFlow(
            ContentPreferences(
                showCartoonsTab = true,
                showAnimeTab = true,
                showAnime = true,
            )
        )
        val repository = mockk<NavigationPreferencesRepository>()
        every { repository.contentPreferences } returns preferences
        val screens = mockk<Screens>(relaxed = true)
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns screens
        val mapper = mockk<MainUIMapper>()
        val initialState = mainState(TabType.Home)
        every {
            mapper.buildViewState(null, BookmarkMode.Simple)
        } returns initialState
        every {
            mapper.buildViewState(TabType.Home, BookmarkMode.Simple)
        } returns initialState
        every { mapper.updateSelectedTab(any(), any()) } answers {
            val state = firstArg<MainViewState>()
            val tab = secondArg<MainTab>()
            state.copy(selectedTab = tab.type)
        }
        every { mapper.buildTabContent(any(), any(), any()) } answers {
            puberTab(firstArg(), thirdArg())
        }
        val tabRouter = mockk<TabRouter>(relaxed = true)
        val vm = MainVM(
            router,
            mapper,
            tabRouter,
            repository,
            BookmarkPreferencesRepository(),
        )

        vm.testOnStart()
        runCurrent()
        preferences.value = preferences.value.copy(showAnime = false)
        runCurrent()
        listOf(TabType.Movies, TabType.Series, TabType.Cartoons).forEach { type ->
            vm.onAction(CommonAction.ItemSelected(mainTab(type)))
        }

        listOf(TabType.Home, TabType.Movies, TabType.Series, TabType.Cartoons).forEach { type ->
            verify(exactly = 1) {
                mapper.buildTabContent(
                    type = type,
                    navigationMode = NavigationMode.TopTabs,
                    refreshVersion = 1,
                )
            }
        }
        verify(exactly = 0) {
            mapper.buildTabContent(
                type = TabType.Anime,
                navigationMode = any(),
                refreshVersion = 1,
            )
        }
        vm.testCancelScope()
    }

    @Test
    fun enablingExtendedBookmarksRebuildsNavigationImmediately() = runTest(dispatcher) {
        val contentPreferences = MutableStateFlow(
            ContentPreferences(
                showCartoonsTab = false,
                showAnimeTab = false,
                showAnime = true,
            )
        )
        val navigationRepository = mockk<NavigationPreferencesRepository>()
        every { navigationRepository.contentPreferences } returns contentPreferences
        val bookmarkPreferences = BookmarkPreferencesRepository()
        val screens = mockk<Screens>(relaxed = true)
        val router = mockk<AppRouter>(relaxed = true)
        every { router.screens } returns screens
        val mapper = mockk<MainUIMapper>()
        val simpleState = mainState(TabType.Home)
        val extendedState = simpleState.copy(
            tabs = listOf(
                mainTab(TabType.Home, isSelected = true),
                mainTab(TabType.Bookmarks),
            )
        )
        every { mapper.buildViewState(null, BookmarkMode.Simple) } returns simpleState
        every { mapper.buildViewState(TabType.Home, BookmarkMode.Extended) } returns extendedState
        every { mapper.buildTabContent(any(), any(), any()) } answers {
            puberTab(firstArg())
        }
        val tabRouter = mockk<TabRouter>(relaxed = true)
        val vm = MainVM(
            router = router,
            mainUIMapper = mapper,
            tabRouter = tabRouter,
            navigationPreferencesRepository = navigationRepository,
            bookmarkPreferencesRepository = bookmarkPreferences,
        )

        vm.testOnStart()
        runCurrent()
        bookmarkPreferences.setMode(BookmarkMode.Extended)
        runCurrent()

        assertEquals(
            listOf(TabType.Home, TabType.Bookmarks),
            vm.testStateValue.tabs.map(MainTab::type),
        )
        verify(exactly = 1) {
            mapper.buildViewState(TabType.Home, BookmarkMode.Extended)
        }
        verify(exactly = 1) {
            mapper.buildTabContent(
                type = TabType.Home,
                navigationMode = NavigationMode.TopTabs,
                refreshVersion = 1,
            )
        }
        vm.testCancelScope()
    }

    private fun mainState(selectedTab: TabType): MainViewState {
        return MainViewState(
            tabs = listOf(mainTab(selectedTab, isSelected = true)),
            selectedTab = selectedTab,
            navigationMode = NavigationMode.TopTabs,
        )
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

    private fun puberTab(
        type: TabType,
        refreshVersion: Int = 0,
    ): PuberTab {
        val screen = mockk<PuberScreen>()
        every { screen.key } returns "${type.name}Screen"
        return PuberTab(
            screen = screen,
            tag = type,
            instanceKey = refreshVersion.takeIf { it > 0 }?.let { "refresh_$it" }.orEmpty(),
        )
    }
}
