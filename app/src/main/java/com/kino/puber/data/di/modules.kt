package com.kino.puber.data.di

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.repository.CryptoPreferenceRepository
import com.kino.puber.data.repository.DeviceInfoRepository
import com.kino.puber.data.repository.DeviceSettingsRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.IDeviceInfoRepository
import com.kino.puber.data.repository.IDeviceSettingsRepository
import com.kino.puber.data.repository.IKinoPubRepository
import com.kino.puber.data.repository.KinoPubRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    singleOf(::KinoPubApiClient)
}

val repositoryModule = module {
    singleOf(::KinoPubRepository) { bind<IKinoPubRepository>() }
    singleOf(::CryptoPreferenceRepository) { bind<ICryptoPreferenceRepository>() }
    singleOf(::DeviceInfoRepository) { bind<IDeviceInfoRepository>() }
    singleOf(::DeviceSettingsRepository) { bind<IDeviceSettingsRepository>() }
}