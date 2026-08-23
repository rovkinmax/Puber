package com.kino.puber.ui.feature.episodeschedule.component

import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class EpisodeScheduleScreenKeyTest {

    @Test
    fun itemIdentityIsIncludedInStableScreenKey() {
        val first = EpisodeScheduleScreen(
            EpisodeScheduleScreenParams(
                itemId = 42,
                title = "Series",
                imdbId = "tt123",
            ),
        )
        val second = EpisodeScheduleScreen(
            EpisodeScheduleScreenParams(
                itemId = 43,
                title = "Other series",
                imdbId = "tt456",
            ),
        )

        assertEquals("EpisodeScheduleScreen_42", first.key)
        assertNotEquals(first.key, second.key)
    }
}
