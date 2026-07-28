package com.kino.puber.core.ui.uikit.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvContextMenuLongSelectStateTest {

    @Test
    fun onSelectKeyDown_ignoresRegularPressBeforeRepeatThreshold() {
        val state = TvContextMenuLongSelectState(repeatThreshold = 1)

        assertEquals(TvContextMenuLongSelectDecision.Ignore, state.onSelectKeyDown(repeatCount = 0))
        assertFalse(state.onSelectKeyUp())
    }

    @Test
    fun onSelectKeyDown_opensImmediatelyWhenLongPressThresholdReached() {
        val state = TvContextMenuLongSelectState(repeatThreshold = 1)

        assertEquals(TvContextMenuLongSelectDecision.Open, state.onSelectKeyDown(repeatCount = 1))

        assertTrue(state.onSelectKeyUp())
    }

    @Test
    fun onSelectKeyDown_consumesRepeatedEventsAfterMenuOpened() {
        val state = TvContextMenuLongSelectState(repeatThreshold = 1)

        assertEquals(TvContextMenuLongSelectDecision.Open, state.onSelectKeyDown(repeatCount = 1))
        assertEquals(TvContextMenuLongSelectDecision.Consume, state.onSelectKeyDown(repeatCount = 2))
        assertEquals(TvContextMenuLongSelectDecision.Consume, state.onSelectKeyDown(repeatCount = 3))

        assertTrue(state.onSelectKeyUp())
    }

    @Test
    fun onSelectKeyDown_allowsNextLongPressAfterKeyUp() {
        val state = TvContextMenuLongSelectState(repeatThreshold = 1)

        assertEquals(TvContextMenuLongSelectDecision.Open, state.onSelectKeyDown(repeatCount = 1))
        assertTrue(state.onSelectKeyUp())

        assertEquals(TvContextMenuLongSelectDecision.Open, state.onSelectKeyDown(repeatCount = 1))
    }

    @Test
    fun sharedState_consumesOpeningGestureAcrossFocusHandoff() {
        val sharedState = TvContextMenuLongSelectState(repeatThreshold = 1)
        val sourceConsumer = LongSelectConsumer(sharedState)
        val dialogConsumer = DialogLongSelectConsumer(sharedState)

        assertEquals(TvContextMenuLongSelectDecision.Open, sourceConsumer.onKeyDown(repeatCount = 1))
        assertTrue(dialogConsumer.onKeyDown(repeatCount = 2))
        assertTrue(dialogConsumer.onKeyDown(repeatCount = 3))
        assertTrue(dialogConsumer.onKeyUp())
        assertFalse(sourceConsumer.onKeyUp())

        assertEquals(TvContextMenuLongSelectDecision.Open, sourceConsumer.onKeyDown(repeatCount = 1))
        assertTrue(dialogConsumer.onKeyUp())
    }

    @Test
    fun sharedState_doesNotClaimOrdinarySelectAfterDedicatedMenuOpening() {
        val sharedState = TvContextMenuLongSelectState(repeatThreshold = 1)
        val dialogConsumer = DialogLongSelectConsumer(sharedState)

        assertFalse(dialogConsumer.onKeyDown(repeatCount = 0))
        assertFalse(dialogConsumer.onKeyDown(repeatCount = 1))
        assertFalse(dialogConsumer.onKeyDown(repeatCount = 2))
        assertFalse(dialogConsumer.onKeyUp())
        assertEquals(TvContextMenuLongSelectDecision.Open, sharedState.onSelectKeyDown(repeatCount = 1))
    }

    private class LongSelectConsumer(
        private val state: TvContextMenuLongSelectState,
    ) {
        fun onKeyDown(repeatCount: Int): TvContextMenuLongSelectDecision =
            state.onSelectKeyDown(repeatCount)

        fun onKeyUp(): Boolean = state.onSelectKeyUp()
    }

    private class DialogLongSelectConsumer(
        private val state: TvContextMenuLongSelectState,
    ) {
        fun onKeyDown(repeatCount: Int): Boolean =
            state.consumeSelectRepeatIfTracking(repeatCount)

        fun onKeyUp(): Boolean = state.consumeSelectKeyUpIfTracking()
    }
}
