package com.kino.puber.data.repository

import kotlinx.coroutines.flow.Flow

interface IDeviceInfoRepository {

    fun is4kSupported(): Boolean
    fun isHdrSupported(): Boolean
    fun getAndroidVersion(): String
    fun getDeviceBrand(): String
    fun getDeviceModel(): String
    fun saveDeviceInformation(title: String, hardware: String, software: String): Flow<Unit>
}