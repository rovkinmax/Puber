package com.kino.puber.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kino.puber.core.coroutine.DefaultExceptionHandler
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.BackButtonDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val started = AtomicBoolean(false)
    private val viewState: Flow<ViewState> get() = mutableViewState
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
    }

    protected open fun dispatchError(error: ErrorEntity) {
    }

    protected open fun onStart() {}

    @Composable
    fun collectViewState(initial: ViewState = stateValue): State<ViewState> {
        if (started.compareAndSet(false, true)) {
            router.addBackDispatcher(this)
            onStart()
        }
        return viewState.collectAsStateWithLifecycle(initial)
    }

    override fun onCleared() {
        router.removeBackDispatcher(this)
    }
}