package com.kino.puber

import android.app.Application
import com.kino.puber.core.logger.LinkingDebugTree
import com.kino.puber.data.di.apiModule
import com.kino.puber.data.di.repositoryModule
import com.kino.puber.domain.di.interactorModule
import com.kino.puber.ui.feature.auth.vm.MainViewmodel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import timber.log.Timber

private val viewModelModule = module {
    viewModelOf(::MainViewmodel)
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
                viewModelModule,
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