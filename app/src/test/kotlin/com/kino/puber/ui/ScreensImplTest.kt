package com.kino.puber.ui

import com.kino.puber.ui.feature.search.SearchScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScreensImplTest {

    @Test
    fun searchCreatesOrdinaryTitleSearchMode() {
        val screen = ScreensImpl.search()

        assertTrue(screen is SearchScreen)
        assertEquals("SearchScreen", screen.key)
    }

    @Test
    fun actorItemsCreatesStableActorSearchMode() {
        val first = ScreensImpl.actorItems("Tom Hanks")
        val same = ScreensImpl.actorItems("Tom Hanks")
        val different = ScreensImpl.actorItems("Meryl Streep")

        assertTrue(first is SearchScreen)
        assertEquals("SearchScreen_Actor_Tom Hanks", first.key)
        assertEquals(first.key, same.key)
        assertNotEquals(first.key, different.key)
    }
}
