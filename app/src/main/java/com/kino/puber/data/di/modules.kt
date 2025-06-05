package com.kino.puber.data.di

import com.kino.puber.data.api.KinoPubClient
import com.kino.puber.data.repository.CryptoPreferenceRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.IKinoPubRepository
import com.kino.puber.data.repository.KinoPubRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val apiModule = module {
    single {
        KinoPubClient.create(
            context = get(),
        )
    }
}

val repositoryModule = module {
    singleOf(::KinoPubRepository) { bind<IKinoPubRepository>() }
    singleOf(::CryptoPreferenceRepository) { bind<ICryptoPreferenceRepository>() }
}