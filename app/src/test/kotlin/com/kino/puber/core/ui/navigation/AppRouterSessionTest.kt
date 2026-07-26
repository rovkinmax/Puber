package com.kino.puber.core.ui.navigation

import com.kino.puber.util.MainDispatcherExtension
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class AppRouterSessionTest {

    companion object {
        private val dispatcher = StandardTestDispatcher()

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension(dispatcher)
    }

    @Test
    fun resetSessionDropsBackDispatchersAndPendingResultListeners() = runTest(dispatcher) {
        val router = AppRouter(
            screens = mockk(relaxed = true),
            coroutineScope = this,
        )
        var result: String? = null
        router.addBackDispatcher(mockk(relaxed = true))
        router.setOnceResultListener<String>(resultCode = 7) {
            result = it
        }

        router.resetSession()
        router.back(resultCode = 7, result = "stale")

        assertFalse(router.hasBackDispatchers())
        assertNull(result)
    }

    @Test
    fun resetSessionRejectsCommandsScheduledByThePreviousSession() = runTest(dispatcher) {
        val router = AppRouter(
            screens = mockk(relaxed = true),
            coroutineScope = this,
        )
        val staleScreen = mockk<PuberScreen>()
        val activeScreen = mockk<PuberScreen>()
        val observed = mutableListOf<Command>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            router.events().collect(observed::add)
        }

        router.navigateTo(staleScreen)
        router.resetSession()
        runCurrent()
        router.navigateTo(activeScreen)
        runCurrent()

        assertEquals(listOf(Command.NavigateTo(activeScreen)), observed)
    }
}
