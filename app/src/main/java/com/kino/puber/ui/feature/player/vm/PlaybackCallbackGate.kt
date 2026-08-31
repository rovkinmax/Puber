package com.kino.puber.ui.feature.player.vm

internal class PlaybackCallbackGate {
    class Session internal constructor(internal val generation: Long)

    private val lock = Any()
    private var callback: PlaybackControl.Callback? = null
    private var generation = 0L

    fun setCallback(callback: PlaybackControl.Callback) {
        synchronized(lock) {
            this.callback = callback
        }
    }

    fun beginSession(): Session {
        return synchronized(lock) {
            Session(++generation)
        }
    }

    fun invalidate() {
        synchronized(lock) {
            generation += 1
        }
    }

    fun dispatch(
        session: Session,
        action: (PlaybackControl.Callback?) -> Unit,
    ) {
        synchronized(lock) {
            if (session.generation == generation) {
                action(callback)
            }
        }
    }
}
