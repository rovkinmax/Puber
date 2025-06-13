package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface DeviceSettingUIModel {
    data class TypeValue(
        val value: Int,
        val label: String
    ) : DeviceSettingUIModel

    data class TypeList(
        val type: String,
        val values: List<SettingOptionUi>,
        val label: String,
    ) : DeviceSettingUIModel
}

@Immutable
internal data class DeviceSettingsListUi(
    val settingsList: List<DeviceSettingUIModel>
)

@Immutable
internal data class SettingOptionUi(
    val id: Int,
    val label: String,
    val description: String = "",
    val selected: Int
)

@Immutable
internal data class DeviceUi(
    val title: String,
    val hardware: String,
    val software: String
)