package com.kino.puber.domain.di

import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.auth.AuthInteractor
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.device.DeviceInfoInteractor
import com.kino.puber.domain.interactor.device.DeviceSettingInteractor
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.interactor.update.AppUpdateInteractor
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val interactorModule = module {
    singleOf(::AppUpdateInteractor) { bind<IAppUpdateInteractor>() }
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
    singleOf(::DeviceInfoInteractor) { bind<IDeviceInfoInteractor>() }
    singleOf(::DeviceSettingInteractor) { bind<IDeviceSettingInteractor>() }
    singleOf(::GenreInteractor)
    singleOf(::WatchLaterBookmarkInteractor)
    singleOf(::SavedItemInteractor)
    singleOf(::ApiDomainInteractor)
}
