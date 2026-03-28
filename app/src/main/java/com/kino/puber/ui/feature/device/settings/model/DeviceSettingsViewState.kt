package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.domain.interactor.device.DeviceSettingType

internal data class DeviceSettingsViewState(
    val state: DeviceSettingsState = DeviceSettingsState.Loading,
)

internal sealed interface DeviceSettingsState {
    object Loading : DeviceSettingsState
    data class Error(val error: String) : DeviceSettingsState
    data class Success(
        val settings: DeviceSettingsListUi,
        val device: DeviceUi,
        val expandedType: DeviceSettingType? = null,
        val savingOptionId: Int? = null,
        val savingToggleType: DeviceSettingType? = null,
        val skipIntroEnabled: Boolean = true,
        val skipRecapEnabled: Boolean = true,
        val skipCreditsEnabled: Boolean = true,
        val debugOverlayEnabled: Boolean = false,
    ) : DeviceSettingsState
}