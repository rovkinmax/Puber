package com.kino.puber.core.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

typealias OnResult<T> = (T?) -> (Unit)

class AppRouter(
    val coroutineScope: CoroutineScope,
    //val scopeName: String, // for debug info
) {
    private val sharedFlow = MutableSharedFlow<Command>(extraBufferCapacity = 0, replay = 1)
    private val onceListeners = HashMap<Int, OnResult<Any>>()
    private val backDispatchersStack = ArrayDeque<BackButtonDispatcher>()

    fun events(): Flow<Command> {
        return sharedFlow.asSharedFlow()
    }

    fun navigateTo(screen: PuberScreen) {
        runCommand(Command.NavigateTo(screen))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> navigateForResult(screen: PuberScreen, requestCode: Int, listener: OnResult<T>) {
        onceListeners[requestCode] = listener as OnResult<Any>
        navigateTo(screen)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> setOnceResultListener(resultCode: Int, listener: OnResult<T>) {
        onceListeners[resultCode] = listener as OnResult<Any>
    }

    fun newRootScreen(vararg screen: PuberScreen) {
        runCommand(Command.NewRoot(screen.toList()))
    }

    fun newRootScreens(screens: List<PuberScreen>) {
        runCommand(Command.NewRoot(screens.toList()))
    }


    fun replaceScreen(screen: PuberScreen) {
        runCommand(Command.Replace(screen))
    }

    fun showOver(screen: PuberScreen) {
        runCommand(Command.ShowOver(screen))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> showOver(screen: PuberScreen, resultCode: Int, listener: OnResult<T>) {
        onceListeners[resultCode] = listener as OnResult<Any>
        runCommand(Command.ShowOver(screen))
    }

    fun back(resultCode: Int? = null, result: Any? = null) {
        runCommand(Command.Back)
        resultCode?.let { dispatchResult(it, result) }
    }

    fun hideBottomSheet(resultCode: Int? = null, result: Any? = null) {
        runCommand(Command.HideOver)
        resultCode?.let { dispatchResult(it, result) }
    }

    fun backTo(screen: PuberScreen) {
        runCommand(Command.BackTo(screen))
    }

    fun closeRootFlow() {
        runCommand(Command.FinishFlow)
    }

    fun addBackDispatcher(dispatcher: BackButtonDispatcher) {
        backDispatchersStack.addLast(dispatcher)
    }

    fun hasBackDispatchers(): Boolean {
        return backDispatchersStack.isNotEmpty()
    }

    fun removeBackDispatcher(dispatcher: BackButtonDispatcher) {
        backDispatchersStack.remove(dispatcher)
    }

    fun dispatchBackPressed(): Boolean {
        return if (backDispatchersStack.isNotEmpty()) {
            val lastDispatcher = backDispatchersStack.removeLast()
            lastDispatcher.onBackPressed()
            true
        } else {
            false
        }
    }

    private fun dispatchResult(resultCode: Int, result: Any?) {
        val resultListener = onceListeners[resultCode]
        if (resultListener != null) {
            resultListener.invoke(result)
            onceListeners.remove(resultCode)
        }
    }

    private fun runCommand(command: Command) {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            sharedFlow.emit(command)
        }
    }
}

sealed class Command(open val screen: PuberScreen? = null) {
    data class NavigateTo(override val screen: PuberScreen) : Command(screen)
    data class ShowOver(override val screen: PuberScreen) : Command()
    data object HideOver : Command()
    data class NewRoot(val screens: List<PuberScreen>) : Command(screens.lastOrNull())
    data class Replace(override val screen: PuberScreen) : Command(screen)
    data object Back : Command()
    data object FinishFlow : Command()
    data class BackTo(override val screen: PuberScreen) : Command(screen)
}