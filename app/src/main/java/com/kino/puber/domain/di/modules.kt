package com.kino.puber.domain.di

import com.kino.puber.domain.interactor.AuthInteractor
import com.kino.puber.domain.interactor.IAuthInteractor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val interactorModule = module {
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
}