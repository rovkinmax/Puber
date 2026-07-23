package com.kino.puber.core.ui.navigation

import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AppRouterTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun navigateForResult_emitsResultCommandWithoutRegisteringOnCallerRouter() = runTest {
        val router = createRouter()
        val screen = mockk<PuberScreen>()

        router.navigateForResult<String>(screen, RESULT_CONTENT_CHANGED) { }

        val command = router.events().first()
        assertTrue(command is Command.NavigateForResult)
        command as Command.NavigateForResult
        assertEquals(screen, command.screen)
        assertEquals(RESULT_CONTENT_CHANGED, command.requestCode)
    }

    @Test
    fun resultListenersWithSameCode_dispatchInnermostFirstAndPreserveOuter() = runTest {
        val router = createRouter()
        val outerResults = mutableListOf<String?>()
        val innerResults = mutableListOf<String?>()

        router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED) { outerResults += it }
        router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED) { innerResults += it }

        router.back(RESULT_CONTENT_CHANGED, "inner")
        router.back(RESULT_CONTENT_CHANGED, "outer")

        assertEquals(listOf("inner"), innerResults)
        assertEquals(listOf("outer"), outerResults)
    }

    @Test
    fun emptyInnerResult_isConsumedBeforeOuterResult() = runTest {
        val router = createRouter()
        val outerResults = mutableListOf<ContentChangeSet?>()
        val innerResults = mutableListOf<ContentChangeSet?>()

        router.setOnceResultListener<ContentChangeSet>(RESULT_CONTENT_CHANGED) { outerResults += it }
        router.setOnceResultListener<ContentChangeSet>(RESULT_CONTENT_CHANGED) { innerResults += it }

        router.back(RESULT_CONTENT_CHANGED, ContentChangeSet.empty())
        val outerChange = ContentChangeSet.single(42, ContentChangeType.Watched)
        router.back(RESULT_CONTENT_CHANGED, outerChange)

        assertEquals(listOf(ContentChangeSet.empty()), innerResults)
        assertEquals(listOf(outerChange), outerResults)
    }

    @Test
    fun newRootScreen_discardsResultListenersFromReplacedStack() = runTest {
        val router = createRouter()
        val results = mutableListOf<String?>()
        router.setOnceResultListener<String>(RESULT_CONTENT_CHANGED) { results += it }

        router.newRootScreen(mockk<PuberScreen>())
        router.back(RESULT_CONTENT_CHANGED, "stale")

        assertTrue(results.isEmpty())
    }

    private fun createRouter(): AppRouter {
        return AppRouter(
            screens = mockk(relaxed = true),
            coroutineScope = CoroutineScope(Dispatchers.Main.immediate),
        )
    }
}
