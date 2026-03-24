package com.kino.puber.domain.interactor.device

import com.kino.puber.data.api.models.DeviceResponse
import kotlinx.coroutines.flow.Flow

interface IDeviceSettingInteractor {

    fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>>

    suspend fun updateDeviceSetting(type: DeviceSettingType, optionId: Int)
}