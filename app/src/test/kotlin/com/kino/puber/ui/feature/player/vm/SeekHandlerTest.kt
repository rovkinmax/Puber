package com.kino.puber.ui.feature.player.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeekHandlerTest {

    // Default steps: [10, 10, 20, 30, 60, 60]

    @Test
    fun firstCall_returnsFirstStep() {
        val handler = SeekHandler()
        assertEquals(10, handler.nextStep())
    }

    @Test
    fun rapidConsecutiveCalls_incrementsStep() {
        // With default timeout, the first call resets (lastSeekTime=0, so elapsed > 1500ms).
        // Subsequent calls within ~ms are within timeout, so they increment stepIndex.
        val handler = SeekHandler()
        val first = handler.nextStep()   // resets to 0, returns steps[0] = 10
        val second = handler.nextStep()  // increments to 1, returns steps[1] = 10
        val third = handler.nextStep()   // increments to 2, returns steps[2] = 20
        assertEquals(10, first)
        assertEquals(10, second)
        assertEquals(20, third)
        assertTrue(third >= second, "Each rapid call should advance to a larger or equal step")
    }

    @Test
    fun maxStep_clampsAtLastValue() {
        val steps = intArrayOf(10, 10, 20, 30, 60, 60)
        val handler = SeekHandler(steps = steps, resetTimeoutMs = Long.MAX_VALUE)
        // Exhaust all index advances.
        repeat(steps.size + 5) { handler.nextStep() }
        // The next call must still return the last step value.
        assertEquals(steps.last(), handler.nextStep())
    }

    @Test
    fun callAfterTimeout_resetsToFirstStep() {
        // resetTimeoutMs = 0 means any positive elapsed time triggers a reset,
        // so two back-to-back calls always see a timeout between them without sleeping.
        val handler = SeekHandler(resetTimeoutMs = 0L)
        handler.nextStep() // warm-up; advances stepIndex if no reset, but with 0 ms timeout
        // Because resetTimeoutMs == 0, even 1 ms later counts as "timed out".
        // Spin briefly to ensure at least 1 ms has passed.
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() == start) { /* busy-wait < 1 ms */ }
        val result = handler.nextStep()
        assertEquals(10, result)
    }

    @Test
    fun reset_resetsIndexAndTime() {
        val handler = SeekHandler(resetTimeoutMs = Long.MAX_VALUE)
        // Advance the index several times.
        repeat(4) { handler.nextStep() }
        // After reset the next call must return the first step.
        handler.reset()
        assertEquals(10, handler.nextStep())
    }
}
