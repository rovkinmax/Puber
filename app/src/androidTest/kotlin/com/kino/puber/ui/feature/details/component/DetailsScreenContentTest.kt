package com.kino.puber.ui.feature.details.component

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Play
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.SCHEDULED_VIDEO_ITEM_TEST_TAG
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsCastMemberUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoRowUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val PRIMARY_ACTION = "Primary details action"
private const val DEFAULT_EPISODE = "S1E1"
private const val TARGET_EPISODE = "S8E4 target"
private const val SERIES_STATUS = "Series ongoing"
private const val MAX_VERTICAL_KEY_PRESSES = 20

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

    @Test
    fun embeddedScheduledEpisodePanel_showsTmdbNotice_andKeepsGridFocus() {
        val scheduledEpisodeId = -9001
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = VideoGridUIState(
                            list = listOf(
                                VideoGridItemUIState.Title("Season 9"),
                                VideoGridItemUIState.Items(
                                    items = listOf(
                                        scheduledEpisode(
                                            id = scheduledEpisodeId,
                                            title = "S9E1 scheduled",
                                        ),
                                    ),
                                    rowKey = "season_9",
                                ),
                            ),
                        ),
                        seasonsPanelVisible = true,
                        initialEpisodeFocusId = scheduledEpisodeId,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Season 9").assertIsDisplayed()
        composeRule.onNodeWithText("S9E1 scheduled").assertIsDisplayed()
        composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG).assertIsDisplayed()
        composeRule.waitUntil {
            composeRule
                .onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG).assertIsFocused()
        composeRule
            .onNodeWithText("Источник данных и изображений — TMDB; даты могут измениться.")
            .assertIsDisplayed()
    }

    @Test
    fun actorCardsUseWholeSurfaceForPortraitAndNameClicks() {
        val actions = mutableListOf<UIAction>()
        val focusRequester = FocusRequester()
        val firstActor = DetailsCastMemberUIState(
            actorQuery = "First Actor",
            displayName = "First Actor",
            photoUrl = "photo://first",
        )
        val secondActor = DetailsCastMemberUIState(
            actorQuery = "Second Actor",
            displayName = "Second Actor With A Long Display Name",
        )
        val thirdActor = DetailsCastMemberUIState(
            actorQuery = "Third Actor",
            displayName = "Third Actor",
        )

        composeRule.setContent {
            PuberTheme {
                DetailsInfoPage(
                    info = DetailsInfoUIState(
                        description = "",
                        ratings = emptyList(),
                        primaryRows = emptyList(),
                        secondaryRows = emptyList(),
                        castCards = listOf(firstActor, secondActor, thirdActor),
                    ),
                    hasNextPage = false,
                    showPageChevrons = false,
                    focusRequester = focusRequester,
                    onAction = { actions += it },
                    onPreviousPageRequested = {},
                    onNextPageRequested = {},
                )
            }
        }

        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.onNodeWithContentDescription(firstActor.displayName).assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText(secondActor.displayName).assertIsDisplayed()
        composeRule.onNodeWithText(thirdActor.displayName).assertIsDisplayed()

        val portraitBounds = composeRule
            .onNodeWithTag(ACTOR_PORTRAIT_TEST_TAG_PREFIX + secondActor.actorQuery, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val longNameBounds = composeRule
            .onNodeWithText(secondActor.displayName, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val secondCardBounds = composeRule
            .onNodeWithContentDescription(secondActor.displayName)
            .getUnclippedBoundsInRoot()
        val thirdCardBounds = composeRule
            .onNodeWithContentDescription(thirdActor.displayName)
            .getUnclippedBoundsInRoot()
        assertTrue(portraitBounds.right - portraitBounds.left > 56.dp)
        assertTrue(longNameBounds.top >= portraitBounds.bottom)
        assertEquals(secondCardBounds.right - secondCardBounds.left, thirdCardBounds.right - thirdCardBounds.left)
        assertEquals(secondCardBounds.bottom - secondCardBounds.top, thirdCardBounds.bottom - thirdCardBounds.top)

        composeRule
            .onNodeWithContentDescription(firstActor.displayName)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule
            .onNodeWithText(secondActor.displayName)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule
            .onNodeWithText("Источник данных и изображений — TMDB; даты могут измениться.")
            .assertIsDisplayed()

        assertEquals(
            listOf(
                DetailsAction.CastMemberSelected(firstActor.actorQuery),
                DetailsAction.CastMemberSelected(secondActor.actorQuery),
            ),
            actions,
        )
    }

    @Test
    fun seriesStatusIsRenderedOnMainDetailsPage() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = false,
                        initialEpisodeFocusId = null,
                        seriesStatus = SERIES_STATUS,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(SERIES_STATUS).assertIsDisplayed()
    }

    @Test
    fun longPlotAndFullMetadataKeepStatusReachableBeforePagerHandoff() {
        val focusRequester = FocusRequester()
        var previousPageRequests = 0
        var nextPageRequests = 0
        val actor = DetailsCastMemberUIState(
            actorQuery = "Focused Actor",
            displayName = "Focused Actor",
        )
        val metadata = (1..10).map { index ->
            DetailsInfoRowUIState(
                label = "Metadata $index",
                value = "Complete metadata value $index",
            )
        }

        composeRule.setContent {
            PuberTheme {
                DetailsInfoPage(
                    info = DetailsInfoUIState(
                        description = List(80) {
                            "A deliberately long plot keeps the complete metadata below the initial viewport."
                        }.joinToString(separator = " "),
                        ratings = emptyList(),
                        primaryRows = metadata.take(5),
                        secondaryRows = metadata.drop(5) + DetailsInfoRowUIState(
                            label = "Status",
                            value = SERIES_STATUS,
                        ),
                        castCards = listOf(actor),
                    ),
                    hasNextPage = true,
                    showPageChevrons = false,
                    focusRequester = focusRequester,
                    onAction = {},
                    onPreviousPageRequested = { previousPageRequests += 1 },
                    onNextPageRequested = { nextPageRequests += 1 },
                    modifier = Modifier.size(width = 640.dp, height = 360.dp),
                )
            }
        }

        composeRule.runOnIdle { focusRequester.requestFocus() }
        val actorCard = composeRule.onNodeWithContentDescription(actor.displayName)
        val status = composeRule.onNodeWithText(SERIES_STATUS)
        actorCard.assertIsFocused()
        assertTrue(!status.isDisplayedForTest())

        composeRule.pressDirectionUntil(Key.DirectionDown) {
            status.isDisplayedForTest()
        }

        status.assertIsDisplayed()
        actorCard.assertIsFocused()
        assertEquals(0, nextPageRequests)

        composeRule.pressDirectionUntil(Key.DirectionDown) {
            nextPageRequests == 1
        }
        assertEquals(1, nextPageRequests)

        composeRule.pressDirectionUntil(Key.DirectionUp) {
            previousPageRequests == 1
        }
        assertEquals(1, previousPageRequests)
        actorCard.assertIsFocused()
    }

    private fun content(
        episodes: VideoGridUIState,
        seasonsPanelVisible: Boolean,
        initialEpisodeFocusId: Int?,
        seriesStatus: String? = null,
        castCards: List<DetailsCastMemberUIState> = emptyList(),
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
                castCards = castCards,
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
            seriesStatus = seriesStatus,
        )
    }

    private fun episodes(): VideoGridUIState {
        return VideoGridUIState(
            list = (1..8).flatMap { seasonNumber ->
                listOf(
                    VideoGridItemUIState.Title("Season $seasonNumber"),
                    VideoGridItemUIState.Items(
                        items = listOf(
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
                        rowKey = "season_$seasonNumber",
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

    private fun scheduledEpisode(
        id: Int,
        title: String,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = title,
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            seasonNumber = 9,
            episodeNumber = 1,
            presentation = VideoItemPresentation.Scheduled,
            scheduledSubtitle = "Серия 1",
            scheduledReleaseDate = "Дата выхода: 1 сент. 2026 г.",
        )
    }

    private companion object {
        const val TARGET_EPISODE_ID = 804
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.pressDirectionUntil(
    key: Key,
    condition: () -> Boolean,
) {
    var attempts = 0
    while (!condition() && attempts < MAX_VERTICAL_KEY_PRESSES) {
        onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
        attempts += 1
    }
    assertTrue("Direction $key did not reach the expected state", condition())
}

private fun SemanticsNodeInteraction.isDisplayedForTest(): Boolean {
    return runCatching { assertIsDisplayed() }.isSuccess
}
