package com.kino.puber.ui.feature.auth.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainDetectionResult
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.auth.model.AuthState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.R
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.ui.feature.auth.model.AuthAction
import com.kino.puber.ui.feature.auth.model.AuthViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

internal class AuthVM(
    private val authInteractor: IAuthInteractor,
    private val deviceInfoInteractor: IDeviceInfoInteractor,
    private val apiDomainInteractor: ApiDomainInteractor,
    private val resources: ResourceProvider,
    override val errorHandler: ErrorHandler,
    router: AppRouter,
) : PuberVM<AuthViewState>(router) {

    override val initialViewState = AuthViewState.Loading()

    private var authJob: Job? = null
    private var timerJob: Job? = null
    private var loadingHintJob: Job? = null

    override fun onStart() {
        startAuth()
    }

    private fun startAuth() {
        authJob?.cancel()
        timerJob?.cancel()
        loadingHintJob?.cancel()
        updateViewState(AuthViewState.Loading(apiDomainDialog = currentDialogState()))
        scheduleLoadingHint()
        authJob = launch {
            authInteractor.getAuthState()
                .flatMapConcat { state ->
                    when (state) {
                        is AuthState.Success ->
                            deviceInfoInteractor.setDeviceInformation()
                                .map { state }

                        else -> flowOf(state)
                    }
                }
                .collect {
                    when (it) {
                        is AuthState.Code -> {
                            loadingHintJob?.cancel()
                            updateViewState(
                                AuthViewState.Content(
                                    code = it.code,
                                    url = it.url,
                                    apiDomainDialog = currentDialogState(),
                                )
                            )
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

    override fun onAction(action: UIAction) {
        when (action) {
            AuthAction.OpenApiDomainDialog -> openApiDomainDialog()
            AuthAction.CloseApiDomainDialog -> closeApiDomainDialog()
            is AuthAction.SaveApiDomain -> saveApiDomain(action.domain)
            AuthAction.DetectApiDomain -> detectApiDomain()
            AuthAction.ResetApiDomain -> resetApiDomain()
            else -> super.onAction(action)
        }
    }

    override fun dispatchError(error: ErrorEntity) {
        showMessage(error.message)
        updateViewState(AuthViewState.Loading(showMirrorHint = true, apiDomainDialog = currentDialogState()))
    }

    private fun startTimer(expireTimeSeconds: Int) {
        timerJob?.cancel()
        timerJob = launch {
            for (i in expireTimeSeconds downTo 0) {
                val minutes = i / SECONDS_IN_MINUTE
                val seconds = i % SECONDS_IN_MINUTE
                val formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                updateViewState<AuthViewState.Content> { copy(timeLeft = formatted) }
                delay(1000)
            }
        }
    }

    private fun scheduleLoadingHint() {
        loadingHintJob = launch {
            delay(LOADING_HINT_DELAY_MS)
            updateViewState<AuthViewState.Loading> { copy(showMirrorHint = true) }
        }
    }

    private fun openApiDomainDialog() {
        val dialogState = apiDomainInteractor.getState().toDialogState()
        updateApiDomainDialog(dialogState)
    }

    private fun closeApiDomainDialog() {
        updateApiDomainDialog(null)
    }

    private fun updateApiDomainDialog(dialogState: ApiDomainDialogState?) {
        updateViewState(
            when (val state = stateValue) {
                is AuthViewState.Content -> state.copy(apiDomainDialog = dialogState)
                is AuthViewState.Loading -> state.copy(apiDomainDialog = dialogState)
            }
        )
    }

    private fun saveApiDomain(domain: String) {
        when (val result = apiDomainInteractor.saveCustomDomain(domain)) {
            ApiDomainUpdateResult.Empty -> showMessage(resources.getString(R.string.api_domain_empty))
            ApiDomainUpdateResult.Invalid -> showMessage(resources.getString(R.string.api_domain_invalid))
            is ApiDomainUpdateResult.Success -> {
                closeApiDomainDialog()
                showMessage(resources.getString(R.string.api_domain_saved, result.state.domain))
                startAuth()
            }
        }
    }

    private fun detectApiDomain() {
        val dialogState = currentDialogState() ?: return
        if (dialogState.isDetecting) return
        updateApiDomainDialog(dialogState.copy(isDetecting = true))

        launch {
            when (val result = apiDomainInteractor.detectAndSaveWorkingDomain()) {
                ApiDomainDetectionResult.NotFound -> {
                    updateApiDomainDialog(dialogState.copy(isDetecting = false))
                    showMessage(resources.getString(R.string.api_domain_detect_failed))
                }

                is ApiDomainDetectionResult.Success -> {
                    closeApiDomainDialog()
                    showMessage(resources.getString(R.string.api_domain_detected, result.state.domain))
                    startAuth()
                }
            }
        }
    }

    private fun resetApiDomain() {
        apiDomainInteractor.resetToDefault()
        closeApiDomainDialog()
        showMessage(resources.getString(R.string.api_domain_reset_done))
        startAuth()
    }

    private fun currentDialogState(): ApiDomainDialogState? {
        return when (val state = stateValue) {
            is AuthViewState.Content -> state.apiDomainDialog
            is AuthViewState.Loading -> state.apiDomainDialog
        }
    }

    private fun ApiDomainState.toDialogState(): ApiDomainDialogState {
        return ApiDomainDialogState(
            currentDomain = domain,
            customDomain = customDomain,
        )
    }

    private companion object {
        private const val SECONDS_IN_MINUTE = 60
        private const val LOADING_HINT_DELAY_MS = 20_000L
    }
}
