package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.app.AppInteractor

internal class AppVM(
    router: AppRouter,
    private val appInteractor: AppInteractor,
    private val appLauncher: AppLauncher,
) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit

    override fun onStart() {
        launch {
            appInteractor.logOutEvet()
                .collect {
                    appLauncher.restart()
                }
        }
    }
}