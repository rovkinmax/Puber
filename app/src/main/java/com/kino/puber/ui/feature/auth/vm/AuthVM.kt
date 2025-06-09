package com.kino.puber.ui.feature.auth.vm

import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.auth.model.AuthState
import com.kino.puber.ui.feature.auth.model.AuthViewState

internal class AuthVM(
    private val authInteractor: IAuthInteractor,
    router: AppRouter,
) : PuberVM<AuthViewState>(router) {

    override val errorHandler: ErrorHandler = DefaultErrorHandler
    override val initialViewState = AuthViewState.Loading

    override fun onStart() {
        startAuth()
    }

    private fun startAuth() {
        launch {
            authInteractor.getAuthState()
                .collect {
                    when (it) {
                        is AuthState.Code -> updateViewState(AuthViewState.Content(it.code))
                        AuthState.Success -> {
                            router.newRootScreen(router.screens.main())
                        }
                    }
                }
        }
    }
}