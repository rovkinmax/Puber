package com.kino.puber.domain.interactor.device

import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.repository.IDeviceSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicLong

class DeviceSettingInteractor(
    private val deviceSettingsRepository: IDeviceSettingsRepository,
) : IDeviceSettingInteractor {

    private val currentDeviceId = AtomicLong(0)

    override fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>> =
        deviceSettingsRepository.getCurrentDeviceSettings()
            .onEach { result ->
                result.getOrNull()?.device?.id?.let { currentDeviceId.set(it) }
            }

    override suspend fun updateDeviceSetting(type: DeviceSettingType, optionId: Int) =
        deviceSettingsRepository.updateDeviceSetting(currentDeviceId.get(), type, optionId)
}