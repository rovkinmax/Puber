package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Rule
import org.junit.Test

internal class VideoGridInitialFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialFocusedItemIdFocusesExactEpisodeAcrossSeasons() {
        val target = episode(id = 804, seasonNumber = 8, episodeNumber = 4)
        composeRule.setContent {
            PuberTheme {
                VideoGrid(
                    state = VideoGridUIState(
                        list = (1..8).flatMap { seasonNumber ->
                            listOf(
                                VideoGridItemUIState.Title("Season $seasonNumber"),
                                VideoGridItemUIState.Items(
                                    listOf(
                                        episode(
                                            id = seasonNumber * 100 + 1,
                                            seasonNumber = seasonNumber,
                                            episodeNumber = 1,
                                        ),
                                        if (seasonNumber == 8) {
                                            target
                                        } else {
                                            episode(
                                                id = seasonNumber * 100 + 2,
                                                seasonNumber = seasonNumber,
                                                episodeNumber = 2,
                                            )
                                        },
                                    ),
                                ),
                            )
                        },
                    ),
                    initialFocusedItemId = target.id,
                )
            }
        }

        composeRule.onNodeWithText(target.title).assertIsFocused()
    }

    private fun episode(
        id: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = "S${seasonNumber}E$episodeNumber",
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }
}
