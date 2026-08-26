package com.kino.puber.ui.feature.device.settings

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DeviceSettingsFocusIntentTest {

    @Test
    fun ordinaryFirstEntry_requestsInitialListFocus() {
        assertEquals(
            DeviceSettingsFocusIntent.InitialList,
            deviceSettingsFocusIntent(
                rootAnchorRestorePending = false,
                rootFocusRestoreVersion = 0,
                handledRootFocusRestoreVersion = 0,
            ),
        )
    }

    @Test
    fun pendingRootReturn_requestsSpeedTestLauncherFocus() {
        assertEquals(
            DeviceSettingsFocusIntent.SpeedTestLauncher,
            deviceSettingsFocusIntent(
                rootAnchorRestorePending = true,
                rootFocusRestoreVersion = 1,
                handledRootFocusRestoreVersion = 0,
            ),
        )
    }

    @Test
    fun completedRootReturn_doesNotRescheduleInitialListFocus() {
        assertEquals(
            DeviceSettingsFocusIntent.None,
            deviceSettingsFocusIntent(
                rootAnchorRestorePending = false,
                rootFocusRestoreVersion = 1,
                handledRootFocusRestoreVersion = 1,
            ),
        )
    }

    @Test
    fun unhandledRootReturn_requestsLauncherAfterAnchorPendingStateClears() {
        assertEquals(
            DeviceSettingsFocusIntent.SpeedTestLauncher,
            deviceSettingsFocusIntent(
                rootAnchorRestorePending = false,
                rootFocusRestoreVersion = 1,
                handledRootFocusRestoreVersion = 0,
            ),
        )
    }

    @Test
    fun capturedNavigationBeforeReturn_doesNotMoveFocus() {
        assertEquals(
            DeviceSettingsFocusIntent.None,
            deviceSettingsFocusIntent(
                rootAnchorRestorePending = true,
                rootFocusRestoreVersion = 0,
                handledRootFocusRestoreVersion = 0,
            ),
        )
    }

    @Test
    fun rootReturnFocusRequest_retriesUntilLauncherIsAttachedAndAcceptsFocus() = runTest {
        var attempts = 0
        var retryFrames = 0

        requestRootReturnFocus(
            requestFocus = {
                attempts++
                attempts == 3
            },
            awaitRetryFrame = { retryFrames++ },
        )

        assertEquals(3, attempts)
        assertEquals(2, retryFrames)
    }
}
