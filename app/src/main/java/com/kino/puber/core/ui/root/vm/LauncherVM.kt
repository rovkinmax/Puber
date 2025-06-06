package com.kino.puber.core.ui.root.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter

internal class LauncherVM(
    router: AppRouter,
) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit

    override fun onStart() {
        val isAuthenticated = false
        if (isAuthenticated) {
            router.newRootScreen(router.screens.main())
        } else {
            router.newRootScreen(router.screens.auth())
        }
    }
}