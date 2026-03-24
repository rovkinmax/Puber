package com.kino.puber.ui.feature.device.settings.mappers

import com.kino.puber.data.api.models.DeviceResponseModel
import com.kino.puber.data.api.models.SettingList
import com.kino.puber.data.api.models.SettingValue
import com.kino.puber.data.api.models.SettingsResponse
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

data class DeviceCapabilities(
    val sslSupported: Boolean,
    val hevcSupported: Boolean,
    val hdrSupported: Boolean,
    val is4kSupported: Boolean,
)

internal class DeviceUiSettingsMapper {

    fun mapSettings(settings: SettingsResponse, capabilities: DeviceCapabilities): DeviceSettingsListUi {
        val settingsList = buildList {
            add(mapToggle(settings.supportSsl, DeviceSettingType.SUPPORT_SSL, capabilities.sslSupported))
            add(mapToggle(settings.supportHevc, DeviceSettingType.SUPPORT_HEVC, capabilities.hevcSupported))
            add(mapToggle(settings.supportHdr, DeviceSettingType.SUPPORT_HDR, capabilities.hdrSupported))
            add(mapToggle(settings.support4k, DeviceSettingType.SUPPORT_4K, capabilities.is4kSupported))
            add(mapToggle(settings.mixedPlaylist, DeviceSettingType.MIXED_PLAYLIST, true))
            add(mapList(settings.serverLocation, DeviceSettingType.SERVER_LOCATION))
            add(mapList(settings.streamingType, DeviceSettingType.STREAMING_TYPE))
        }
        return DeviceSettingsListUi(settingsList)
    }

    fun mapDevice(device: DeviceResponseModel) = DeviceUi(
        title = device.title,
        hardware = device.hardware,
        software = device.software,
    )

    private fun mapToggle(
        setting: SettingValue,
        type: DeviceSettingType,
        supported: Boolean,
    ) = DeviceSettingUIModel.TypeValue(
        type = type,
        value = setting.value == 1,
        label = setting.label,
        supported = supported,
    )

    private fun mapList(
        setting: SettingList,
        type: DeviceSettingType,
    ) = DeviceSettingUIModel.TypeList(
        type = type,
        values = setting.value.map {
            SettingOptionUi(
                id = it.id,
                label = it.label,
                description = it.description,
                selected = it.selected == 1,
            )
        },
        label = setting.label,
    )
}
