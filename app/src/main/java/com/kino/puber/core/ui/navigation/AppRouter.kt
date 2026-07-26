package com.kino.puber.core.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

typealias OnResult<T> = (T?) -> (Unit)

class AppRouter(
    val screens: Screens,
    val coroutineScope: CoroutineScope,
    //val scopeName: String, // for debug info
) {
    private data class SessionCommand(
        val sessionId: Long,
        val command: Command,
    )

    private val sharedFlow = MutableSharedFlow<SessionCommand>(extraBufferCapacity = 0, replay = 1)
    private val onceListeners = HashMap<Int, ArrayDeque<OnResult<Any>>>()
    private val backDispatchersStack = ArrayDeque<BackButtonDispatcher>()
    private val sessionId = AtomicLong()

    fun events(): Flow<Command> {
        return sharedFlow
            .asSharedFlow()
            .mapNotNull { event ->
                event.command.takeIf { event.sessionId == sessionId.get() }
            }
    }

    fun navigateTo(screen: PuberScreen) {
        runCommand(Command.NavigateTo(screen))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> navigateForResult(screen: PuberScreen, requestCode: Int, listener: OnResult<T>) {
        runCommand(Command.NavigateForResult(screen, requestCode, listener as OnResult<Any>))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> setOnceResultListener(resultCode: Int, listener: OnResult<T>) {
        onceListeners.getOrPut(resultCode) { ArrayDeque() }.addLast(listener as OnResult<Any>)
    }

    fun newRootScreen(vararg screen: PuberScreen) {
        onceListeners.clear()
        runCommand(Command.NewRoot(screen.toList()))
    }

    fun newRootScreens(screens: List<PuberScreen>) {
        onceListeners.clear()
        runCommand(Command.NewRoot(screens.toList()))
    }


    fun replaceScreen(screen: PuberScreen) {
        runCommand(Command.Replace(screen))
    }

    fun back(resultCode: Int? = null, result: Any? = null) {
        runCommand(Command.Back)
        resultCode?.let { dispatchResult(it, result) }
    }

    fun backTo(screen: PuberScreen) {
        runCommand(Command.BackTo(screen))
    }

    fun closeRootFlow() {
        runCommand(Command.FinishFlow)
    }

    fun addBackDispatcher(dispatcher: BackButtonDispatcher) {
        backDispatchersStack.remove(dispatcher)
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

    fun clearPendingCommands() {
        sharedFlow.resetReplayCache()
    }

    internal fun resetSession() {
        sessionId.incrementAndGet()
        clearPendingCommands()
        onceListeners.clear()
        backDispatchersStack.clear()
    }

    private fun dispatchResult(resultCode: Int, result: Any?) {
        val resultListeners = onceListeners[resultCode] ?: return
        val resultListener = resultListeners.pollLast() ?: return
        resultListener.invoke(result)
        if (resultListeners.isEmpty()) {
            onceListeners.remove(resultCode)
        }
    }

    private fun runCommand(command: Command) {
        val commandSessionId = sessionId.get()
        coroutineScope.launch(Dispatchers.Main.immediate) {
            if (commandSessionId == sessionId.get()) {
                sharedFlow.emit(
                    SessionCommand(
                        sessionId = commandSessionId,
                        command = command,
                    )
                )
            }
        }
    }
}

sealed class Command(open val screen: PuberScreen? = null) {
    data class NavigateTo(override val screen: PuberScreen) : Command(screen)
    data class NavigateForResult(
        override val screen: PuberScreen,
        val requestCode: Int,
        val listener: OnResult<Any>,
    ) : Command(screen)

    data class NewRoot(val screens: List<PuberScreen>) : Command(screens.lastOrNull())
    data class Replace(override val screen: PuberScreen) : Command(screen)
    data object Back : Command()
    data object FinishFlow : Command()
    data class BackTo(override val screen: PuberScreen) : Command(screen)
}
