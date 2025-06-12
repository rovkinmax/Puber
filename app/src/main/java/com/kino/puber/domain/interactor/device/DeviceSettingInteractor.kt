package com.kino.puber.domain.interactor.device

import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.repository.IDeviceSettingsRepository
import kotlinx.coroutines.flow.Flow

class DeviceSettingInteractor(
    private val deviceSettingsRepository: IDeviceSettingsRepository,
) : IDeviceSettingInteractor {

    override fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>> =
        deviceSettingsRepository.getCurrentDeviceSettings()
}