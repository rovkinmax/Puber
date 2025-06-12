package com.kino.puber.ui.feature.device.settings.model

internal data class DeviceSettingsViewState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val settings: DeviceSettingsListUi? = null,
    val deviceUI: DeviceUi? = null,
)