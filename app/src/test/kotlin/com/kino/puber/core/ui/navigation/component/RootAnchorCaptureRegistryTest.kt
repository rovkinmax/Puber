package com.kino.puber.core.ui.navigation.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RootAnchorCaptureRegistryTest {

    @Test
    fun captureAndCompletionAreScopedToTheExactScreenKey() {
        val registry = RootAnchorCaptureRegistry()
        registry.register("screen-a") { LazyAnchor(index = 2, offset = 545) }
        registry.register("screen-b") { LazyAnchor(index = 4, offset = 24) }

        assertTrue(registry.capture("screen-a"))
        assertEquals(LazyAnchor(index = 2, offset = 545), registry.savedAnchor("screen-a"))
        assertEquals(null, registry.savedAnchor("screen-b"))
        assertTrue(registry.restorePending)
        assertFalse(registry.focusRestored)

        registry.completeRestore("screen-b")
        assertTrue(registry.restorePending)
        assertEquals(RootAnchorRestoreCompletion(), registry.restoreCompletion)

        registry.markFocusRestored()
        assertTrue(registry.focusRestored)

        registry.completeRestore("screen-a")
        assertFalse(registry.restorePending)
        assertFalse(registry.focusRestored)
        assertEquals(null, registry.savedAnchor("screen-a"))
        assertEquals(
            RootAnchorRestoreCompletion(screenKey = "screen-a", version = 1),
            registry.restoreCompletion,
        )
    }

    @Test
    fun staleUnregisterCannotRemoveTheLatestRegistrationForTheSameScreen() {
        val registry = RootAnchorCaptureRegistry()
        val unregisterFirst = registry.register("screen") {
            LazyAnchor(index = 1, offset = 10)
        }
        registry.register("screen") {
            LazyAnchor(index = 3, offset = 30)
        }

        unregisterFirst()

        assertTrue(registry.capture("screen"))
        assertEquals(LazyAnchor(index = 3, offset = 30), registry.savedAnchor("screen"))
    }

    @Test
    fun nestedCapturesRestoreEachScreenInLifoOrder() {
        val registry = RootAnchorCaptureRegistry()
        registry.register("screen-a") { LazyAnchor(index = 2, offset = 20) }
        registry.register("screen-b") { LazyAnchor(index = 4, offset = 40) }

        assertTrue(registry.capture("screen-a"))
        assertTrue(registry.capture("screen-b"))
        assertTrue(registry.restorePending)
        assertFalse(registry.focusRestored)

        registry.markFocusRestored()
        assertTrue(registry.focusRestored)

        registry.completeRestore("screen-a")
        assertTrue(registry.restorePending)
        assertTrue(registry.focusRestored)
        assertEquals(RootAnchorRestoreCompletion(), registry.restoreCompletion)

        registry.completeRestore("screen-b")
        assertTrue(registry.restorePending)
        assertFalse(registry.focusRestored)
        assertEquals(null, registry.savedAnchor("screen-b"))
        assertEquals(LazyAnchor(index = 2, offset = 20), registry.savedAnchor("screen-a"))
        assertEquals(
            RootAnchorRestoreCompletion(screenKey = "screen-b", version = 1),
            registry.restoreCompletion,
        )

        registry.markFocusRestored()
        assertTrue(registry.focusRestored)

        registry.completeRestore("screen-a")
        assertFalse(registry.restorePending)
        assertFalse(registry.focusRestored)
        assertEquals(null, registry.savedAnchor("screen-a"))
        assertEquals(
            RootAnchorRestoreCompletion(screenKey = "screen-a", version = 2),
            registry.restoreCompletion,
        )
    }

    @Test
    fun sameKeyCapturesConsumeOnlyTheCompletedFrameAnchor() {
        val registry = RootAnchorCaptureRegistry()
        var anchor = LazyAnchor(index = 1, offset = 10)
        registry.register("screen") { anchor }

        assertTrue(registry.capture("screen"))
        anchor = LazyAnchor(index = 3, offset = 30)
        assertTrue(registry.capture("screen"))
        assertEquals(LazyAnchor(index = 3, offset = 30), registry.savedAnchor("screen"))

        registry.markFocusRestored()
        registry.completeRestore("screen")

        assertTrue(registry.restorePending)
        assertEquals(LazyAnchor(index = 1, offset = 10), registry.savedAnchor("screen"))

        registry.markFocusRestored()
        registry.completeRestore("screen")

        assertFalse(registry.restorePending)
        assertEquals(null, registry.savedAnchor("screen"))
    }
}
