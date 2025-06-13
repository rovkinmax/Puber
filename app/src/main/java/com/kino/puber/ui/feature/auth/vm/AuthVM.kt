package com.kino.puber.ui.feature.auth.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.auth.model.AuthState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.ui.feature.auth.model.AuthViewState
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class AuthVM(
    private val authInteractor: IAuthInteractor,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<AuthViewState>(router) {

    override val initialViewState = AuthViewState.Loading

    override fun onStart() {
        startAuth()
    }

    private fun startAuth() {
        launch {
            authInteractor.getAuthState()
                .flatMapConcat { state ->
                    when (state) {
                        is AuthState.Success -> {
                            deviceInfoInteractor.setDeviceInformation()
                                .map { state }
                        }

                        else -> flowOf(state)
                    }
                }
                .collect {
                    when (it) {
                        is AuthState.Code -> updateViewState(
                            AuthViewState.Content(
                                code = it.code, url = it.url, expireTimeSeconds = it.expireTimeSeconds
                            )
                        )

                        AuthState.Success -> {
                            router.newRootScreen(router.screens.main())
                        }
                    }
                }
        }
    }
}