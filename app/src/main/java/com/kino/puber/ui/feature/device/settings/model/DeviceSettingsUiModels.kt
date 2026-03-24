package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable
import com.kino.puber.domain.interactor.device.DeviceSettingType

@Immutable
sealed class DeviceSettingUIModel {
    data class TypeValue(
        val type: DeviceSettingType,
        val value: Boolean,
        val label: String,
        val supported: Boolean = true,
    ) : DeviceSettingUIModel()

    data class TypeList(
        val type: DeviceSettingType,
        val values: List<SettingOptionUi>,
        val label: String,
    ) : DeviceSettingUIModel()
}

@Immutable
internal data class DeviceSettingsListUi(
    val settingsList: List<DeviceSettingUIModel>
)

@Immutable
data class SettingOptionUi(
    val id: Int,
    val label: String,
    val description: String = "",
    val selected: Boolean
)

@Immutable
internal data class DeviceUi(
    val title: String,
    val hardware: String,
    val software: String
)
