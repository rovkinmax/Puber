package com.kino.puber.core.ui.navigation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.ui.feature.main.model.TabType
import io.mockk.mockk
import io.mockk.verify
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(InternalVoyagerApi::class)
internal class TabNavigatorChildRetentionTest {

    @Test
    fun firstObservedRefreshReplacesTheInitialRoot() {
        val initialRoot = RetentionProbeScreen(0)
        val refreshedRoot = RetentionProbeScreen(1)
        val child = navigator(
            key = "TabFlow:Tab:HistoryScreen",
            parent = null,
            screen = initialRoot,
        )
        val router = mockk<AppRouter>(relaxed = true)
        val tabSession = TabFlowSession(
            router = router,
            initialContentInstanceKey = "Tab:HistoryScreen",
        )

        assertTrue(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = refreshedRoot,
                contentInstanceKey = "Tab:HistoryScreen:refresh_1",
                tabSession = tabSession,
            )
        )
        assertEquals(listOf(refreshedRoot), child.items)
        verify(exactly = 1) { router.resetSession() }
    }

    @Test
    fun unchangedContentGenerationDoesNotResetARetainedStack() {
        val initialRoot = RetentionProbeScreen(0)
        val retainedRoot = RetentionProbeScreen(99)
        val child = navigator(
            key = "TabFlow:Tab:HistoryScreen",
            parent = null,
            screen = initialRoot,
        )
        val router = mockk<AppRouter>(relaxed = true)
        val tabSession = TabFlowSession(
            router = router,
            initialContentInstanceKey = "Tab:HistoryScreen",
        )
        assertFalse(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = initialRoot,
                contentInstanceKey = "Tab:HistoryScreen",
                tabSession = tabSession,
            )
        )
        child.replaceAll(retainedRoot)

        assertFalse(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = initialRoot,
                contentInstanceKey = "Tab:HistoryScreen",
                tabSession = tabSession,
            )
        )
        assertEquals(listOf(retainedRoot), child.items)
        verify(exactly = 0) { router.resetSession() }
    }

    @Test
    fun repeatedRefreshKeepsOneLogicalChildInTheParentRegistry() {
        val parent = navigator(
            key = "TabNavigator",
            parent = null,
            disposeNestedNavigators = false,
        )
        val children = parent.childrenForTest()
        val initialTab = PuberTab(
            screen = RetentionProbeScreen(0),
            tag = TabType.History,
        )
        val refreshedTab = PuberTab(
            screen = RetentionProbeScreen(0),
            tag = TabType.History,
            instanceKey = "refresh_1",
        )
        val initialRoot = RetentionProbeScreen(0)
        val child = navigator(
            key = tabFlowNavigatorKey(initialTab.navigationSlotKey),
            parent = parent,
            screen = initialRoot,
        )
        children[child.key] = child
        val router = mockk<AppRouter>(relaxed = true)
        val tabSession = TabFlowSession(
            router = router,
            initialContentInstanceKey = initialTab.contentInstanceKey,
        )
        val refreshTwoTab = PuberTab(
            screen = RetentionProbeScreen(0),
            tag = TabType.History,
            instanceKey = "refresh_2",
        )

        assertEquals(initialTab.key, refreshedTab.key)
        assertNotEquals(initialTab.contentInstanceKey, refreshedTab.contentInstanceKey)
        assertFalse(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = initialRoot,
                contentInstanceKey = initialTab.contentInstanceKey,
                tabSession = tabSession,
            )
        )
        assertTrue(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = RetentionProbeScreen(1),
                contentInstanceKey = refreshedTab.contentInstanceKey,
                tabSession = tabSession,
            )
        )
        assertTrue(
            replaceTabRootIfChanged(
                navigator = child,
                rootScreen = RetentionProbeScreen(2),
                contentInstanceKey = refreshTwoTab.contentInstanceKey,
                tabSession = tabSession,
            )
        )

        assertEquals(setOf(child.key), children.keys)
        assertSame(child, children[child.key])
        assertEquals(listOf(RetentionProbeScreen(2)), child.items)
        verify(exactly = 2) { router.resetSession() }
    }

    private fun navigator(
        key: String,
        parent: Navigator?,
        disposeNestedNavigators: Boolean = true,
        screen: PuberScreen = LoadingScreen,
    ): Navigator {
        return Navigator(
            screens = listOf(screen),
            key = key,
            stateHolder = mockk<SaveableStateHolder>(relaxed = true),
            disposeBehavior = NavigatorDisposeBehavior(
                disposeNestedNavigators = disposeNestedNavigators,
            ),
            parent = parent,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun Navigator.childrenForTest(): MutableMap<String, Navigator> {
    val childrenGetter = javaClass.declaredMethods.single { method ->
        method.name.startsWith("getChildren$") &&
            MutableMap::class.java.isAssignableFrom(method.returnType)
    }
    return childrenGetter.invoke(this) as MutableMap<String, Navigator>
}

@Parcelize
private data class RetentionProbeScreen(
    private val generation: Int,
) : PuberScreen {

    @IgnoredOnParcel
    override val key = "RetentionProbeScreen:$generation"

    @Composable
    override fun Content() = Unit
}
