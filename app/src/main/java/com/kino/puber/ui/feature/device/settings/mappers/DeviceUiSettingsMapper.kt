package com.kino.puber.ui.feature.device.settings.mappers

import com.kino.puber.data.api.models.Device
import com.kino.puber.data.api.models.SettingList
import com.kino.puber.data.api.models.SettingValue
import com.kino.puber.data.api.models.Settings
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

internal class DeviceUiSettingsMapper() {
    fun mapSettings(settings: Settings): DeviceSettingsListUi {
        val settingsList = buildList {
            add(settings.supportSsl.mapToUi())
            add(settings.supportHevc.mapToUi())
            add(settings.supportHdr.mapToUi())
            add(settings.support4k.mapToUi())
            add(settings.mixedPlaylist.mapToUi())
            add(settings.serverLocation.mapToUi())
            add(settings.streamingType.mapToUi())
        }

        return DeviceSettingsListUi(settingsList)
    }

    fun mapDevice(device: Device) = DeviceUi(
        title = device.title,
        hardware = device.hardware,
        software = device.software,
    )

    private fun SettingValue.mapToUi() = DeviceSettingUIModel.TypeValue(
        value = this.value,
        label = this.label,
    )

    private fun SettingList.mapToUi() = DeviceSettingUIModel.TypeList(
        type = this.type,
        values = this.value.map {
            SettingOptionUi(
                id = it.id,
                label = it.label,
                description = it.description,
                selected = it.selected,
            )
        },
        label = this.label,
    )
}