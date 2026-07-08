package com.kino.puber.ui.feature.device.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi

private val previewDevice = DeviceUi(
    title = "Android TV",
    hardware = "amlogic s905x4",
    software = "Android 12",
)

private val previewToggleSettings = listOf(
    DeviceSettingUIModel.TypeValue(
        type = DeviceSettingType.SUPPORT_SSL,
        label = "Поддержка SSL",
        value = true,
    ),
    DeviceSettingUIModel.TypeValue(
        type = DeviceSettingType.SUPPORT_HEVC,
        label = "Поддержка HEVC",
        value = true,
    ),
    DeviceSettingUIModel.TypeValue(
        type = DeviceSettingType.SUPPORT_HDR,
        label = "Поддержка HDR",
        value = false,
        supported = false,
    ),
    DeviceSettingUIModel.TypeValue(
        type = DeviceSettingType.SUPPORT_4K,
        label = "Поддержка 4K",
        value = false,
        supported = false,
    ),
    DeviceSettingUIModel.TypeValue(
        type = DeviceSettingType.MIXED_PLAYLIST,
        label = "Смешанный плейлист",
        value = true,
    ),
)

private val previewStreamingType = DeviceSettingUIModel.TypeList(
    type = DeviceSettingType.STREAMING_TYPE,
    label = "Тип стриминга",
    values = listOf(
        SettingOptionUi(1, "HLS", selected = true),
        SettingOptionUi(2, "HLS2", selected = false),
        SettingOptionUi(3, "HLS4", selected = false),
        SettingOptionUi(4, "DASH", selected = false),
    )
)

private val previewServerLocation = DeviceSettingUIModel.TypeList(
    type = DeviceSettingType.SERVER_LOCATION,
    label = "Сервер",
    values = listOf(
        SettingOptionUi(1, "Автоматически", selected = true),
        SettingOptionUi(2, "Москва", selected = false),
    )
)

private val previewAllSettings = DeviceSettingsListUi(
    previewToggleSettings + previewStreamingType + previewServerLocation
)

private val previewApiDomain = ApiDomainDialogState(
    currentDomain = "service-kp.com",
    customDomain = null,
)

internal class DeviceSettingsStateProvider : PreviewParameterProvider<DeviceSettingsState> {
    override val values = sequenceOf(
        DeviceSettingsState.Loading,
        DeviceSettingsState.Error("Не удалось загрузить настройки"),
        DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
        ),
        DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            expandedType = DeviceSettingType.STREAMING_TYPE,
        ),
        DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            expandedType = DeviceSettingType.STREAMING_TYPE,
            savingOptionId = 2,
        ),
    )
}

@Preview(name = "Loading", device = TV_1080p)
@Composable
private fun LoadingPreview() = PuberTheme {
    DeviceSettingsContent(state = DeviceSettingsState.Loading, apiDomain = previewApiDomain)
}

@Preview(name = "Error", device = TV_1080p)
@Composable
private fun ErrorPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Error("Не удалось загрузить настройки"),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — collapsed", device = TV_1080p)
@Composable
private fun SuccessCollapsedPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
        ),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — expanded", device = TV_1080p)
@Composable
private fun SuccessExpandedPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            expandedType = DeviceSettingType.STREAMING_TYPE,
        ),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — saving option", device = TV_1080p)
@Composable
private fun SuccessSavingPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            expandedType = DeviceSettingType.STREAMING_TYPE,
            savingOptionId = 2,
        ),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — saving toggle", device = TV_1080p)
@Composable
private fun SuccessSavingTogglePreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            savingToggleType = DeviceSettingType.SUPPORT_SSL,
        ),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — updates disabled", device = TV_1080p)
@Composable
private fun SuccessUpdatesDisabledPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = previewAllSettings,
            device = previewDevice,
            autoUpdateCheckEnabled = false,
        ),
        apiDomain = previewApiDomain,
    )
}

@Preview(name = "Success — unsupported items", device = TV_1080p)
@Composable
private fun UnsupportedPreview() = PuberTheme {
    DeviceSettingsContent(
        state = DeviceSettingsState.Success(
            settings = DeviceSettingsListUi(
                listOf(
                    DeviceSettingUIModel.TypeValue(
                        type = DeviceSettingType.SUPPORT_SSL,
                        label = "Поддержка SSL",
                        value = true,
                    ),
                    DeviceSettingUIModel.TypeValue(
                        type = DeviceSettingType.SUPPORT_HDR,
                        label = "Поддержка HDR",
                        value = false,
                        supported = false,
                    ),
                    DeviceSettingUIModel.TypeValue(
                        type = DeviceSettingType.SUPPORT_4K,
                        label = "Поддержка 4K",
                        value = false,
                        supported = false,
                    ),
                )
            ),
            device = previewDevice,
        ),
        apiDomain = previewApiDomain,
    )
}
