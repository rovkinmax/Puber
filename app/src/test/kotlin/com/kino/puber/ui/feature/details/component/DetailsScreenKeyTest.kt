package com.kino.puber.ui.feature.details.component

import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DetailsScreenKeyTest {

    @Test
    fun initialEpisodeUsesStableDistinctLifecycleKey() {
        val plain = DetailsScreen(DetailsScreenParams(itemId = 42))
        val episode = DetailsScreen(
            DetailsScreenParams(
                itemId = 42,
                initialEpisode = DetailsEpisodeTarget(
                    seasonNumber = 3,
                    episodeNumber = 4,
                ),
            ),
        )

        assertEquals("DetailsScreen_42", plain.key)
        assertEquals("DetailsScreen_42_s3_e4", episode.key)
        assertNotEquals(plain.key, episode.key)
    }
}
