package com.kino.puber.ui.feature.player.vm

internal class Ac3FallbackPolicy {

    private var fallbackApplied = false

    fun reset() {
        fallbackApplied = false
    }

    fun onDecoderInitializationFailure(positionMs: Long): Decision {
        return if (fallbackApplied) {
            Decision.Terminal
        } else {
            fallbackApplied = true
            Decision.Retry(positionMs)
        }
    }

    sealed interface Decision {
        data class Retry(val positionMs: Long) : Decision

        data object Terminal : Decision
    }
}
