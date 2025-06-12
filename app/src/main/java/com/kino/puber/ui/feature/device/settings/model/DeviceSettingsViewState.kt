package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.data.api.models.DeviceResponse

data class DeviceSettingsViewState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentDevice: DeviceResponse? = null,
)