package com.kino.puber.core.ui.navigation.component

import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.RootPuberScreen
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowComponentResultTest {

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
}
