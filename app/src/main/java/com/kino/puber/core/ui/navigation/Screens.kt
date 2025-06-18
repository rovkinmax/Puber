package com.kino.puber.core.ui.navigation

import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel

interface Screens {
    fun auth(): PuberScreen

    fun main(): PuberScreen

    fun deviceSettings(): PuberScreen
    fun deviceSettingsListChooser(options: List<DeviceSettingUIModel.TypeValue>): PuberScreen
}