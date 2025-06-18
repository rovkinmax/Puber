package com.kino.puber.ui.feature.device.settings.list

import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel

internal data class DeviceSettingListChooserViewState(
    val state: DeviceSettingListChooserState = DeviceSettingListChooserState.Loading,
)

internal sealed interface DeviceSettingListChooserState {
    object Loading : DeviceSettingListChooserState
    data class Error(val error: String) : DeviceSettingListChooserState
    data class Success(val options: List<DeviceSettingUIModel.TypeList>) : DeviceSettingListChooserState
}