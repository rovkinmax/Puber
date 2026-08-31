package com.kino.puber.ui.feature.player.component

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelFocusRequesterTest {

    @Test
    fun requestFocusWithRetry_doesNotRequest_whenOwnerEndsBeforeFirstAttempt() = runTest {
        var isOwner = true
        var requestCount = 0

        val focused = requestFocusWithRetry(
            isOwner = { isOwner },
            awaitNextAttempt = { isOwner = false },
            requestFocus = {
                requestCount++
                true
            },
        )

        assertFalse(focused)
        assertEquals(0, requestCount)
    }

    @Test
    fun requestFocusWithRetry_stopsRetrying_whenOwnerChangesDuringExit() = runTest {
        var isOwner = true
        var requestCount = 0

        val focused = requestFocusWithRetry(
            isOwner = { isOwner },
            awaitNextAttempt = {},
            requestFocus = {
                requestCount++
                isOwner = false
                false
            },
        )

        assertFalse(focused)
        assertEquals(1, requestCount)
    }

    @Test
    fun requestFocusWithRetry_stopsWithoutRequest_whenDisposedCoroutineIsCancelled() = runTest {
        var requestCount = 0
        val requestJob = launch {
            requestFocusWithRetry(
                isOwner = { true },
                awaitNextAttempt = { awaitCancellation() },
                requestFocus = {
                    requestCount++
                    true
                },
            )
        }

        runCurrent()
        requestJob.cancelAndJoin()

        assertEquals(0, requestCount)
    }

    @Test
    fun requestFocusWithRetry_isBounded_whenTargetIsDisabledOrDetached() = runTest {
        var frameCount = 0
        var requestCount = 0

        val focused = requestFocusWithRetry(
            isOwner = { true },
            awaitNextAttempt = { frameCount++ },
            requestFocus = {
                requestCount++
                false
            },
        )

        assertFalse(focused)
        assertEquals(30, frameCount)
        assertEquals(30, requestCount)
    }

    @Test
    fun requestFocusWithRetry_succeeds_whenOwnedTargetAttaches() = runTest {
        var requestCount = 0

        val focused = requestFocusWithRetry(
            isOwner = { true },
            awaitNextAttempt = {},
            requestFocus = {
                requestCount++
                requestCount == 3
            },
        )

        assertTrue(focused)
        assertEquals(3, requestCount)
    }

    @Test
    fun requestWhenAttached_usesFallback_afterPrimaryBoundExpires() = runTest {
        var primaryRequestCount = 0
        var fallbackRequestCount = 0

        val focused = requestFocusWithFallback(
            isOwner = { true },
            awaitNextAttempt = {},
            requestPrimaryFocus = {
                primaryRequestCount++
                false
            },
            requestFallbackFocus = {
                fallbackRequestCount++
                true
            },
        )

        assertTrue(focused)
        assertEquals(30, primaryRequestCount)
        assertEquals(1, fallbackRequestCount)
    }

    @Test
    fun requestWhenAttached_doesNotUseFallback_afterOwnershipEnds() = runTest {
        var isOwner = true
        var primaryRequestCount = 0
        var fallbackRequestCount = 0

        val focused = requestFocusWithFallback(
            isOwner = { isOwner },
            awaitNextAttempt = {},
            requestPrimaryFocus = {
                primaryRequestCount++
                isOwner = false
                false
            },
            requestFallbackFocus = {
                fallbackRequestCount++
                true
            },
        )

        assertFalse(focused)
        assertEquals(1, primaryRequestCount)
        assertEquals(0, fallbackRequestCount)
    }
}
