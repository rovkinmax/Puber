package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class DeviceSettingsActions : UIAction {

    data object UnlinkDevice : DeviceSettingsActions()
    data class ChangeSettingValue(val setting: DeviceSettingUIModel.TypeValue) : DeviceSettingsActions()
    data class ChangeSettingList(val setting: DeviceSettingUIModel.TypeList) : DeviceSettingsActions()
    data class OnListSettingsList(val options: List<DeviceSettingUIModel.TypeList>) : DeviceSettingsActions()
}