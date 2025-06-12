package com.kino.puber.domain.di

import com.kino.puber.domain.interactor.auth.AuthInteractor
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.device.DeviceInfoInteractor
import com.kino.puber.domain.interactor.device.DeviceSettingInteractor
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val interactorModule = module {
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
    singleOf(::DeviceInfoInteractor) { bind<IDeviceInfoInteractor>() }
    singleOf(::DeviceSettingInteractor) { bind<IDeviceSettingInteractor>() }
}