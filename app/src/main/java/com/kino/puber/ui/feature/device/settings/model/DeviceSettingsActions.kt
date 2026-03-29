package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType

internal sealed class DeviceSettingsActions : UIAction {

    data object UnlinkDevice : DeviceSettingsActions()
    data class ChangeSettingValue(val setting: DeviceSettingUIModel.TypeValue) : DeviceSettingsActions()
    data class ToggleListExpand(val setting: DeviceSettingUIModel.TypeList) : DeviceSettingsActions()
    data class SelectOption(val type: DeviceSettingType, val optionId: Int) : DeviceSettingsActions()
    data object ToggleSkipIntro : DeviceSettingsActions()
    data object ToggleSkipRecap : DeviceSettingsActions()
    data object ToggleSkipCredits : DeviceSettingsActions()
    data object ToggleDebugOverlay : DeviceSettingsActions()
    data class ChangeNavigationMode(val mode: NavigationMode) : DeviceSettingsActions()
}
