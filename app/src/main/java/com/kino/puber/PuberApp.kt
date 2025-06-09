package com.kino.puber

import android.app.Application
import com.kino.puber.core.logger.LinkingDebugTree
import com.kino.puber.core.system.AndroidResourceProvider
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.data.di.apiModule
import com.kino.puber.data.di.repositoryModule
import com.kino.puber.domain.di.interactorModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

private val resourceModule = module {
    single<ResourceProvider> { AndroidResourceProvider(get()) }
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