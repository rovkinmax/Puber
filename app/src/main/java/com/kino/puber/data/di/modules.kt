package com.kino.puber.data.di

import android.net.ConnectivityManager
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.repository.CryptoPreferenceRepository
import com.kino.puber.data.repository.DeviceInfoRepository
import com.kino.puber.data.repository.DeviceSettingsRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.IDeviceInfoRepository
import com.kino.puber.data.repository.IDeviceSettingsRepository
import com.kino.puber.data.repository.IKinoPubRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.KinoPubRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.SkipSegmentRepository
import com.kino.puber.data.repository.SkipSegmentService
import com.kino.puber.data.repository.TmdbIdRepository
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    single { SessionEventBus() }
    single {
        KinoPubApiClient(
            cacheDir = androidContext().cacheDir,
            connectivityManager = androidContext().getSystemService(ConnectivityManager::class.java),
            cryptoPreferenceRepository = get(),
            sessionEventBus = get(),
        )
    }
    singleOf(::TmdbApiClient)
    singleOf(::TheIntroDbApiClient)
    singleOf(::IntroDbAppApiClient)
}

val repositoryModule = module {
    singleOf(::KinoPubRepository) { bind<IKinoPubRepository>() }
    singleOf(::CryptoPreferenceRepository) { bind<ICryptoPreferenceRepository>() }
    singleOf(::DeviceInfoRepository) { bind<IDeviceInfoRepository>() }
    singleOf(::DeviceSettingsRepository) { bind<IDeviceSettingsRepository>() }
    singleOf(::ItemDetailsRepository)
    singleOf(::PlayerPreferencesRepository)
    singleOf(::TmdbIdRepository)
    singleOf(::SkipSegmentRepository)
    singleOf(::SkipSegmentService)
    singleOf(::NavigationPreferencesRepository)
}