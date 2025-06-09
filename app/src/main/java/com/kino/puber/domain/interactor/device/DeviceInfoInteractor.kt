package com.kino.puber.domain.interactor.device

import com.kino.puber.data.repository.IDeviceInfoRepository
import kotlinx.coroutines.flow.Flow

class DeviceInfoInteractor(
    private val deviceInfoRepository: IDeviceInfoRepository,
) : IDeviceInfoInteractor {
    override fun is4kSupported(): Boolean = deviceInfoRepository.is4kSupported()

    override fun isHdrSupported(): Boolean = deviceInfoRepository.isHdrSupported()

    override fun getAndroidVersion(): String = deviceInfoRepository.getAndroidVersion()

    override fun getDeviceBrand(): String = deviceInfoRepository.getDeviceBrand()

    override fun getDeviceModel(): String = deviceInfoRepository.getDeviceModel()

    override fun setDeviceInformation(): Flow<Unit> =
        deviceInfoRepository.saveDeviceInformation(
            getDeviceModel(),
            "${getDeviceBrand()} ${getDeviceModel()}",
            getAndroidVersion()
        )
}