package com.kino.puber.data.repository

import com.kino.puber.data.api.models.DeviceResponse

import com.kino.puber.domain.interactor.device.DeviceSettingType
import kotlinx.coroutines.flow.Flow

interface IDeviceSettingsRepository {

    fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>>

    suspend fun updateDeviceSetting(deviceId: Long, type: DeviceSettingType, optionId: Int)
}