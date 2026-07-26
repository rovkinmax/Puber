package com.kino.puber.ui.feature.details.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Play
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import org.junit.Rule
import org.junit.Test

private const val PRIMARY_ACTION = "Primary details action"
private const val DEFAULT_EPISODE = "S1E1"
private const val TARGET_EPISODE = "S8E4 target"

internal class DetailsScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleEpisodePanelFocusesExactTargetInLaterSeason() {
        val episodes = episodes()
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes,
                        seasonsPanelVisible = true,
                        initialEpisodeFocusId = TARGET_EPISODE_ID,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Season 8").assertIsDisplayed()
        composeRule.onNodeWithText(TARGET_EPISODE).assertIsDisplayed()
        composeRule.waitUntil {
            composeRule
                .onNodeWithText(TARGET_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(TARGET_EPISODE).assertIsFocused()
    }

    @Test
    fun ordinaryDetailsWithoutEpisodeTargetKeepsPrimaryActionFocused() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = false,
                        initialEpisodeFocusId = null,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithText(PRIMARY_ACTION)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsFocused()
        composeRule.onNodeWithText(TARGET_EPISODE).assertDoesNotExist()
    }

    @Test
    fun visibleEpisodePanelWithoutTargetFocusesDefaultEpisode() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = true,
                        initialEpisodeFocusId = null,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithText(DEFAULT_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(DEFAULT_EPISODE).assertIsFocused()
    }

    private fun content(
        episodes: VideoGridUIState,
        seasonsPanelVisible: Boolean,
        initialEpisodeFocusId: Int?,
    ): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = VideoDetailsUIState(
                id = 42,
                title = "Synthetic details",
                description = "",
                imageUrl = "",
                trailerUrl = "",
                ratings = emptyList(),
                year = "",
                genres = "",
                duration = "",
                country = "",
            ),
            info = DetailsInfoUIState(
                description = "",
                ratings = emptyList(),
                primaryRows = emptyList(),
                secondaryRows = emptyList(),
                castMembers = emptyList(),
            ),
            buttons = listOf(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_watch_movie,
                    icon = PhosphorIcons.Duotone.Play,
                    action = DetailsAction.PlayClicked,
                    textOverride = PRIMARY_ACTION,
                ),
            ),
            isInWatchlist = false,
            isWatched = false,
            seasonsPanelVisible = seasonsPanelVisible,
            episodes = episodes,
            initialEpisodeFocusId = initialEpisodeFocusId,
        )
    }

    private fun episodes(): VideoGridUIState {
        return VideoGridUIState(
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
                                episode(
                                    id = TARGET_EPISODE_ID,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = 4,
                                    title = TARGET_EPISODE,
                                )
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
        )
    }

    private fun episode(
        id: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String = "S${seasonNumber}E$episodeNumber",
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = title,
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    private companion object {
        const val TARGET_EPISODE_ID = 804
    }
}
