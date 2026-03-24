package com.kino.puber.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kino.puber.core.coroutine.DefaultExceptionHandler
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.BackButtonDispatcher
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.SnackbarMessage
import com.kino.puber.core.ui.uikit.model.UIAction
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

abstract class PuberVM<ViewState>(protected val router: AppRouter) : ViewModel(),
    BackButtonDispatcher {

    protected open val errorHandler: ErrorHandler? = null
    protected abstract val initialViewState: ViewState
    private val mutableViewState: MutableStateFlow<ViewState> by lazy {
        MutableStateFlow(initialViewState)
    }
    private val snackBarMessageFlow = MutableStateFlow<SnackbarMessage?>(null)
    private val started = AtomicBoolean(false)
    private val viewState: Flow<ViewState> get() = mutableViewState

    protected val coroutineContext: CoroutineContext
        get() = viewModelScope.coroutineContext

    protected val stateValue
        get() = mutableViewState.value

    protected inline fun <reified T : ViewState> updateViewState(
        crossinline updates: T.() -> ViewState,
    ) {
        if (stateValue is T) {
            updateViewState(updates(stateValue as T))
        }
    }

    protected open fun updateViewState(viewState: ViewState) {
        mutableViewState.value = viewState
    }

    protected fun launch(
        context: CoroutineContext = exceptionHandler(),
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch(
            context = context,
            CoroutineStart.DEFAULT
        ) { block.invoke(this) }
    }

    private fun exceptionHandler(): CoroutineExceptionHandler = DefaultExceptionHandler {
        errorHandler?.proceedInvoke(it, ::dispatchError)
        log(it)
    }

    protected open fun dispatchError(error: ErrorEntity) {
    }

    protected fun showMessage(message: String) {
        showMessage(SnackbarMessage.Short(message))
    }

    protected fun showMessage(message: SnackbarMessage) {
        snackBarMessageFlow.value = message
    }

    protected fun cleanMessage() {
        snackBarMessageFlow.value = null
    }

    @Composable
    fun collectMessage(initial: SnackbarMessage? = null): State<SnackbarMessage?> {
        return snackBarMessageFlow
            .asSharedFlow()
            .collectAsStateWithLifecycle(initial)
    }

    protected open fun onStart() {}

    open fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.SnackBarDismissed -> cleanMessage()
            is CommonAction.SnackBarActionPerformed -> cleanMessage()
        }
    }

    override fun onBackPressed() {
        router.back()
    }

    @Composable
    fun collectViewState(initial: ViewState = stateValue): State<ViewState> {
        if (started.compareAndSet(false, true)) {
            onStart()
        }
        DisposableEffect(this@PuberVM) {
            registerBackDispatcher()
            onDispose {
                removeBackDispatcher()
            }
        }
        return viewState.collectAsStateWithLifecycle(initial)
    }

    private fun registerBackDispatcher() {
        router.addBackDispatcher(this)
    }

    private fun removeBackDispatcher() {
        router.removeBackDispatcher(this)
    }
}