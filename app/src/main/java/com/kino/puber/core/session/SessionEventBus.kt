package com.kino.puber.core.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface SessionEvent {
    data object Unauthorized : SessionEvent
}

class SessionEventBus {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val events: Flow<SessionEvent> = _events.asSharedFlow()

    fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }
}
