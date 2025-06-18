package com.kino.puber.ui

import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.auth.component.AuthScreen
import com.kino.puber.ui.feature.device.settings.DeviceSettingsScreen
import com.kino.puber.ui.feature.device.settings.list.DeviceSettingListChooserScreen
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.main.component.MainScreen

internal object ScreensImpl : Screens {
    override fun auth(): PuberScreen {
        return AuthScreen()
    }

    override fun main(): PuberScreen {
        return MainScreen()
    }

    override fun deviceSettings(): PuberScreen {
        return DeviceSettingsScreen()
    }

    override fun deviceSettingsListChooser(options: List<DeviceSettingUIModel.TypeValue>): PuberScreen {
        return DeviceSettingListChooserScreen()
    }
}