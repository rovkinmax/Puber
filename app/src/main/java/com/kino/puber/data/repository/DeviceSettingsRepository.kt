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
            DeviceSettingType.STREAMING_TYPE -> kinoPubApiClient.updateDeviceSettings(deviceId, streamingType = optionId)
            DeviceSettingType.SERVER_LOCATION -> kinoPubApiClient.updateDeviceSettings(deviceId, serverLocation = optionId)
            DeviceSettingType.SUPPORT_SSL -> kinoPubApiClient.updateDeviceSettings(deviceId, supportSsl = optionId)
            DeviceSettingType.SUPPORT_HEVC -> kinoPubApiClient.updateDeviceSettings(deviceId, supportHevc = optionId)
            DeviceSettingType.SUPPORT_HDR -> kinoPubApiClient.updateDeviceSettings(deviceId, supportHdr = optionId)
            DeviceSettingType.SUPPORT_4K -> kinoPubApiClient.updateDeviceSettings(deviceId, support4k = optionId)
            DeviceSettingType.MIXED_PLAYLIST -> kinoPubApiClient.updateDeviceSettings(deviceId, mixedPlaylist = optionId)
        }.getOrThrow()
    }
}