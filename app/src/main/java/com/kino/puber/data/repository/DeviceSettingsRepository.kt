package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.domain.interactor.device.DeviceSettingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class DeviceSettingsRepository(
    private val kinoPubApiClient: KinoPubApiClient,
) : IDeviceSettingsRepository {

    override fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>> = flow {
        val result = kinoPubApiClient.getDeviceSettings()
        emit(result)
    }

    override suspend fun updateDeviceSetting(deviceId: Long, type: DeviceSettingType, optionId: Int) {
        when (type) {
            DeviceSettingType.STREAMING_TYPE -> kinoPubApiClient.updateDeviceSettings(deviceId, streamingType = optionId).getOrThrow()
            DeviceSettingType.SERVER_LOCATION -> kinoPubApiClient.updateDeviceSettings(deviceId, serverLocation = optionId).getOrThrow()
        }
    }
}