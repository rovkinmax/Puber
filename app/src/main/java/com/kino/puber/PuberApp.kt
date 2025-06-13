package com.kino.puber

import android.app.Application
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.LinkingDebugTree
import com.kino.puber.core.system.AndroidResourceProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.data.di.apiModule
import com.kino.puber.data.di.repositoryModule
import com.kino.puber.domain.di.interactorModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import timber.log.Timber

private val resourceModule = module {
    single<ResourceProvider> { AndroidResourceProvider(get()) }
}

private val handlersModule = module {
    singleOf(::DefaultErrorHandler) { bind<ErrorHandler>() }
}

class PuberApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initDi()
        initLogger()
    }

    private fun initDi() {
        startKoin {
            androidContext(this@PuberApp)
            modules(
                resourceModule,
                handlersModule,
                apiModule,
                repositoryModule,
                interactorModule,
            )
        }
    }

    private fun initLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(LinkingDebugTree())
        }
    }

}