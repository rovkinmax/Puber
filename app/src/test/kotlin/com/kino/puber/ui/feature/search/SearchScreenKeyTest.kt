package com.kino.puber.ui.feature.search

import com.kino.puber.ui.feature.search.model.SearchScreenParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SearchScreenKeyTest {

    @Test
    fun actorQueriesHaveDistinctStableKeys() {
        val first = SearchScreen(
            SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")),
        )
        val same = SearchScreen(
            SearchScreenParams(SearchScreenParams.SearchMode.Actor("Tom Hanks")),
        )
        val different = SearchScreen(
            SearchScreenParams(SearchScreenParams.SearchMode.Actor("Meryl Streep")),
        )

        assertEquals("SearchScreen_Actor_Tom Hanks", first.key)
        assertEquals(first.key, same.key)
        assertNotEquals(first.key, different.key)
    }
}
