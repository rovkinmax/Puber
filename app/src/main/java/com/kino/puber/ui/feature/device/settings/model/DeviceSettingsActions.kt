package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.SettingList
import com.kino.puber.data.api.models.SettingValue

sealed class DeviceSettingsActions : UIAction {

    data object UnlinkDevice : DeviceSettingsActions()
    data object Retry : DeviceSettingsActions()
    data class ChangeSettingValue(val setting: SettingValue) : DeviceSettingsActions()
    data class ChangeSettingList(val setting: SettingList) : DeviceSettingsActions()
}