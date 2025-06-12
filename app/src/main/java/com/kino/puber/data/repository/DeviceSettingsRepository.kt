package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.DeviceResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class DeviceSettingsRepository(
    private val kinoPubApiClient: KinoPubApiClient,
) : IDeviceSettingsRepository {

    override fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>> = flow {
        val result = kinoPubApiClient.getDeviceSettings()
        emit(result)
    }
}