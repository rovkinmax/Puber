package com.kino.puber.data.repository

import com.kino.puber.data.api.models.DeviceResponse
import kotlinx.coroutines.flow.Flow

interface IDeviceSettingsRepository {

    fun getCurrentDeviceSettings(): Flow<Result<DeviceResponse>>

}