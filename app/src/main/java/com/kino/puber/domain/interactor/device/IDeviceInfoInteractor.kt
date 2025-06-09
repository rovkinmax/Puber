package com.kino.puber.domain.interactor.device

import kotlinx.coroutines.flow.Flow

interface IDeviceInfoInteractor {
    fun is4kSupported(): Boolean
    fun isHdrSupported(): Boolean
    fun getAndroidVersion(): String
    fun getDeviceBrand(): String
    fun getDeviceModel(): String
    fun setDeviceInformation(): Flow<Unit>
}