package com.kino.puber

import android.app.Application
import com.kino.puber.core.logger.LinkingDebugTree
import timber.log.Timber

class PuberApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogger()
    }

    private fun initLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(LinkingDebugTree())
        }
    }
} 