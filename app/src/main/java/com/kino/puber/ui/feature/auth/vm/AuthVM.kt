package com.kino.puber.ui.feature.auth.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.auth.model.AuthState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.ui.feature.auth.model.AuthViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

internal class AuthVM(
    private val authInteractor: IAuthInteractor,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<AuthViewState>(router) {

    override val initialViewState = AuthViewState.Loading

    private val _timeLeft = MutableStateFlow("")
    val timeLeft: StateFlow<String> = _timeLeft.asStateFlow()

    private var timerJob: Job? = null

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
                        is AuthState.Code -> {
                            updateViewState(AuthViewState.Content(code = it.code, url = it.url))
                            startTimer(it.expireTimeSeconds)
                        }

                        AuthState.Success -> {
                            timerJob?.cancel()
                            router.newRootScreen(router.screens.main())
                        }
                    }
                }
        }
    }

    private fun startTimer(expireTimeSeconds: Int) {
        timerJob?.cancel()
        timerJob = launch {
            for (i in expireTimeSeconds downTo 0) {
                val minutes = i / 60
                val seconds = i % 60
                _timeLeft.value = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }
}
