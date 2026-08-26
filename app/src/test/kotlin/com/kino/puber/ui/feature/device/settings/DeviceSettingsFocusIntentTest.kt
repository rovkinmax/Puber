package com.kino.puber.ui.feature.device.settings

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
            ),
        )
    }
}
