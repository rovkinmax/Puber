package com.kino.puber.core.ui.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class TabRouter(
    private val scope: CoroutineScope,
) {
    private val sharedFlow = MutableSharedFlow<TabCommand>(extraBufferCapacity = 0, replay = 1)

    fun events(): SharedFlow<TabCommand> {
        return sharedFlow.asSharedFlow()
    }

    fun openTab(tab: PuberTab) {
        runCommand(TabCommand.Open(tab))
    }

    private fun runCommand(command: TabCommand) {
        scope.launch(Dispatchers.Main.immediate) {
            sharedFlow.emit(command)
        }
    }

}

sealed class TabCommand {
    data class Open(val tab: PuberTab) : TabCommand()
}