package com.kino.puber.ui.feature.root.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.repository.ICryptoPreferenceRepository

internal class LauncherVM(
    router: AppRouter,
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
) : PuberVM<Any>(router) {
    override val initialViewState: Any = Unit

    override fun onStart() {
        val isAuthenticated = cryptoPreferenceRepository.getAccessToken().isNullOrEmpty().not()
        if (isAuthenticated) {
            router.newRootScreen(router.screens.main())
        } else {
            router.newRootScreen(router.screens.auth())
        }
    }
}