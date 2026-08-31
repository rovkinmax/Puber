package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class Ac3FallbackPolicyTest {

    @Test
    fun decoderFailure_retriesOnceAtCurrentPosition_thenBecomesTerminal() {
        val policy = Ac3FallbackPolicy()

        assertEquals(
            Ac3FallbackPolicy.Decision.Retry(positionMs = 137_000L),
            policy.onDecoderInitializationFailure(positionMs = 137_000L),
        )
        assertEquals(
            Ac3FallbackPolicy.Decision.Terminal,
            policy.onDecoderInitializationFailure(positionMs = 141_000L),
        )
    }

    @Test
    fun reset_startsANewFallbackWindow() {
        val policy = Ac3FallbackPolicy()
        policy.onDecoderInitializationFailure(positionMs = 10_000L)

        policy.reset()

        assertEquals(
            Ac3FallbackPolicy.Decision.Retry(positionMs = 25_000L),
            policy.onDecoderInitializationFailure(positionMs = 25_000L),
        )
    }
}
