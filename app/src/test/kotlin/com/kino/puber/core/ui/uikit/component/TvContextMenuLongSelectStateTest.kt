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
}
