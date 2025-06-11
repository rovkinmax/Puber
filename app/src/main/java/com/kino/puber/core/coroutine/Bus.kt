package com.kino.puber.core.coroutine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

interface Bus<T> {
    fun content(): Flow<T>

    fun setValue(value: T)
}

abstract class SubscriptionBus<T>(
    replay: Int = 0,
    extraBufferCapacity: Int = 1,
) : Bus<T> {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName(""))

    private val channel = MutableSharedFlow<T>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity,
    )

    override fun content(): Flow<T> {
        return channel.asSharedFlow()
    }

    override fun setValue(value: T) {
        scope.launch {
            channel.emit(value)
        }

        // channel.tryEmit(value)
    }

    inline fun <reified K : T> observe(): Flow<K> {
        return content().filterIsInstance()
    }

    protected open fun clean() {
        channel.resetReplayCache()
    }
}

abstract class StateBus<T> : Bus<T> {
    private val state = MutableStateFlow<T?>(null)

    override fun content(): Flow<T> {
        return state.filterNotNull()
    }

    override fun setValue(value: T) {
        state.value = value
    }

    fun clearValue() {
        state.value = null
    }

    val currentValue: T?
        get() = state.value
}