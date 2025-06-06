package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.ui.feature.main.model.MainViewState

internal class MainVM(router: AppRouter) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()
}