package com.kino.puber.ui.feature.device.settings.model

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

internal data class DeviceSettingsListUi(
    val settingsList: List<DeviceSettingUIModel>
)

internal data class SettingOptionUi(
    val id: Int,
    val label: String,
    val description: String = "",
    val selected: Int
)

internal data class DeviceUi(
    val title: String,
    val hardware: String,
    val software: String
)