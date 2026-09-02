package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.OverlayPuberScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.RootPuberScreen
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowComponentResultTest {

    @Test
    fun physicalBack_routesUnhandledPopThroughAppRouter() {
        val router = mockk<AppRouter>(relaxed = true)
        every { router.dispatchBackPressed() } returns false

        val allowVoyagerPop = onBackPressed(router)

        assertFalse(allowVoyagerPop)
        verify(exactly = 1) { router.dispatchBackPressed() }
        verify(exactly = 1) { router.back() }
    }

    @Test
    fun physicalBack_doesNotEnqueuePop_whenScreenDispatcherHandlesIt() {
        val router = mockk<AppRouter>(relaxed = true)
        every { router.dispatchBackPressed() } returns true

        val allowVoyagerPop = onBackPressed(router)

        assertFalse(allowVoyagerPop)
        verify(exactly = 1) { router.dispatchBackPressed() }
        verify(exactly = 0) { router.back() }
    }

    @Test
    fun rootScreenWithRootRouter_routesResultNavigationToRoot() {
        val screen = mockk<RootPuberScreen>()

        val target = resolveTabResultNavigationTarget(
            screen = screen,
            rootRouter = mockk<AppRouter>(),
        )

        assertEquals(TabResultNavigationTarget.Root, target)
    }

    @Test
    fun rootScreenWithoutRootRouter_routesResultNavigationToTab() {
        val screen = mockk<RootPuberScreen>()

        val target = resolveTabResultNavigationTarget(
            screen = screen,
            rootRouter = null,
        )

        assertEquals(TabResultNavigationTarget.Tab, target)
    }

    @Test
    fun nonRootScreenWithRootRouter_routesResultNavigationToTab() {
        val screen = mockk<PuberScreen>()

        val target = resolveTabResultNavigationTarget(
            screen = screen,
            rootRouter = mockk<AppRouter>(),
        )

        assertEquals(TabResultNavigationTarget.Tab, target)
    }

    @Test
    fun rootResultNavigationForwardsToRootRouterAndDoesNotPushOnTab() {
        val router = mockk<AppRouter>(relaxed = true)
        val rootRouter = mockk<AppRouter>(relaxed = true)
        val screen = mockk<RootPuberScreen>()
        val listener: (Any?) -> Unit = {}
        val pushedScreens = mutableListOf<PuberScreen>()

        onTabNavigateForResult(
            event = Command.NavigateForResult(
                screen = screen,
                requestCode = RESULT_CONTENT_CHANGED,
                listener = listener,
            ),
            router = router,
            rootRouter = rootRouter,
            pushScreen = pushedScreens::add,
        )

        assertEquals(emptyList<PuberScreen>(), pushedScreens)
        verify(exactly = 1) {
            rootRouter.navigateForResult(screen, RESULT_CONTENT_CHANGED, listener)
        }
        verify(exactly = 0) {
            router.setOnceResultListener(RESULT_CONTENT_CHANGED, listener)
        }
    }

    @Test
    fun nonRootResultNavigationRegistersOnTabRouterAndPushesScreen() {
        val router = mockk<AppRouter>(relaxed = true)
        val screen = mockk<PuberScreen>()
        val listener: (Any?) -> Unit = {}
        val pushedScreens = mutableListOf<PuberScreen>()

        onTabNavigateForResult(
            event = Command.NavigateForResult(
                screen = screen,
                requestCode = RESULT_CONTENT_CHANGED,
                listener = listener,
            ),
            router = router,
            rootRouter = null,
            pushScreen = pushedScreens::add,
        )

        assertEquals(listOf(screen), pushedScreens)
        verify(exactly = 1) {
            router.setOnceResultListener(RESULT_CONTENT_CHANGED, listener)
        }
    }

    @Test
    fun overlayScreen_keepsPreviousScreenAsVisibleBackgroundLayer() {
        val background = mockk<RootPuberScreen>()
        val overlay = mockk<OverlayPuberScreen>()

        val layers = resolveVisibleScreenLayers(listOf(background, overlay))

        assertEquals(listOf(background, overlay), layers)
    }

    @Test
    fun regularScreen_hidesEarlierScreens() {
        val previous = mockk<RootPuberScreen>()
        val current = mockk<RootPuberScreen>()

        val layers = resolveVisibleScreenLayers(listOf(previous, current))

        assertEquals(listOf(current), layers)
    }
}
