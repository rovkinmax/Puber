package com.kino.puber.ui.feature.main.toptabs

import com.kino.puber.ui.feature.main.model.TabType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TopTabFocusIntentTest {

    @Test
    fun selectedStateDoesNotReplaceAStillVisibleFocusedTab() {
        val intent = TopTabFocusIntent(TabType.Movies).focus(TabType.Series)

        val reconciled = intent.reconcile(
            visibleTabs = listOf(TabType.Home, TabType.Movies, TabType.Series),
            selectedTab = TabType.Movies,
        )

        assertEquals(TabType.Series, reconciled.focusedTab)
        assertEquals(intent.token, reconciled.token)
    }

    @Test
    fun removedFocusedTabFallsBackToSelectedVisibleTab() {
        val intent = TopTabFocusIntent(TabType.Series).focus(TabType.Series)

        val reconciled = intent.reconcile(
            visibleTabs = listOf(TabType.Home, TabType.Movies),
            selectedTab = TabType.Movies,
        )

        assertEquals(TabType.Movies, reconciled.focusedTab)
        assertTrue(reconciled.token > intent.token)
    }

    @Test
    fun staleDelayedSelectionTokenIsRejectedAfterAnotherTabGetsFocus() {
        val firstIntent = TopTabFocusIntent(TabType.Movies).focus(TabType.Movies)
        val latestIntent = firstIntent.focus(TabType.Series)

        assertFalse(latestIntent.isLatest(firstIntent.token))
        assertTrue(latestIntent.isLatest(latestIntent.token))
    }
}
