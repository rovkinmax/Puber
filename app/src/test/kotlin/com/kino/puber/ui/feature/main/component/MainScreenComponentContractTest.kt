package com.kino.puber.ui.feature.main.component

import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.vm.MainVM
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MainScreenComponentContractTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun searchClicked_navigatesToSearchWithoutChangingMainState() {
        val searchScreen = mockk<PuberScreen>()
        val (vm, router, screens, tabRouter) = createViewModel(searchScreen = searchScreen)
        val initialState = vm.testStateValue

        vm.onAction(MainAction.SearchClicked)

        verify(exactly = 1) { screens.search() }
        verify(exactly = 1) { router.navigateTo(searchScreen) }
        verify(exactly = 0) { screens.deviceSettings() }
        verify(exactly = 0) { tabRouter.openTab(any()) }
        assertEquals(initialState, vm.testStateValue)
        vm.testCancelScope()
    }

    @Test
    fun settingsClicked_navigatesToDeviceSettingsWithoutChangingMainState() {
        val settingsScreen = mockk<PuberScreen>()
        val (vm, router, screens, tabRouter) = createViewModel(settingsScreen = settingsScreen)
        val initialState = vm.testStateValue

        vm.onAction(MainAction.SettingsClicked)

        verify(exactly = 1) { screens.deviceSettings() }
        verify(exactly = 1) { router.navigateTo(settingsScreen) }
        verify(exactly = 0) { screens.search() }
        verify(exactly = 0) { tabRouter.openTab(any()) }
        assertEquals(initialState, vm.testStateValue)
        vm.testCancelScope()
    }

    private fun createViewModel(
        searchScreen: PuberScreen? = null,
        settingsScreen: PuberScreen? = null,
    ): ViewModelFixture {
        val router = mockk<AppRouter>(relaxed = true)
        val screens = mockk<Screens>()
        val tabRouter = mockk<TabRouter>(relaxed = true)
        every { router.screens } returns screens
        searchScreen?.let { every { screens.search() } returns it }
        settingsScreen?.let { every { screens.deviceSettings() } returns it }
        val vm = MainVM(
            router = router,
            mainUIMapper = mockk(relaxed = true),
            tabRouter = tabRouter,
            navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true),
            bookmarkPreferencesRepository = BookmarkPreferencesRepository(),
        )
        return ViewModelFixture(vm, router, screens, tabRouter)
    }

    private data class ViewModelFixture(
        val vm: MainVM,
        val router: AppRouter,
        val screens: Screens,
        val tabRouter: TabRouter,
    )
}
