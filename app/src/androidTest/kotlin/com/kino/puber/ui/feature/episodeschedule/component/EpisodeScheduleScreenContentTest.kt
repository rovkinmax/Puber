package com.kino.puber.ui.feature.episodeschedule.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.core.ui.uikit.component.moviesList.SCHEDULED_VIDEO_ITEM_TEST_TAG
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DATE_LABEL_SENTINEL = "DATE_LABEL_SENTINEL"

internal class EpisodeScheduleScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_rendersGroupedSchedule_andCardsDoNotDispatchActions() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                EpisodeScheduleScreenContent(
                    state = contentState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Дом дракона").assertIsDisplayed()
        composeRule.onNodeWithText("Сезон 2").assertIsDisplayed()
        composeRule.onNodeWithText("1. Новая серия").assertIsDisplayed()
        composeRule.onNodeWithText(DATE_LABEL_SENTINEL, substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG).assertIsFocused().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }

        composeRule.runOnIdle {
            assertTrue(actions.isEmpty())
        }
    }

    @Test
    fun loading_exposesProgressSemantics() {
        composeRule.setContent {
            PuberTheme {
                EpisodeScheduleScreenContent(
                    state = EpisodeScheduleScreenState.Loading,
                    onAction = {},
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        ).assertIsDisplayed()
    }

    @Test
    fun empty_rendersReasonAndProviderFooter() {
        composeRule.setContent {
            PuberTheme {
                EpisodeScheduleScreenContent(
                    state = EpisodeScheduleScreenState.Empty(
                        reason = EpisodeScheduleScreenState.EmptyReason.NoMatch,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Расписание недоступно").assertIsDisplayed()
        composeRule.onNodeWithText("Сериал не найден в TMDB.").assertIsDisplayed()
        composeRule.onNodeWithText("Даты выхода предоставлены TMDB и могут измениться.")
            .assertIsDisplayed()
    }

    @Test
    fun error_retryDispatchesTypedRetryAction() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                EpisodeScheduleScreenContent(
                    state = EpisodeScheduleScreenState.Error("Ошибка сети"),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Повторить", useUnmergedTree = true)
            .onParent()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(actions == listOf(EpisodeScheduleScreenState.Action.Retry))
        }
    }

    private fun contentState(): EpisodeScheduleScreenState.Content {
        return EpisodeScheduleScreenState.Content(
            title = "Дом дракона",
            provider = com.kino.puber.domain.model.ScheduleProvider.TMDB,
            seasons = listOf(
                EpisodeScheduleScreenState.Season(
                    seasonNumber = 2,
                    announcementDate = null,
                    announcementDateLabel = null,
                    episodes = listOf(
                        EpisodeScheduleScreenState.Episode(
                            episodeNumber = 1,
                            title = "Новая серия",
                            airDate = LocalDate(2026, 9, 1),
                            airDateLabel = DATE_LABEL_SENTINEL,
                        ),
                    ),
                ),
            ),
            grid = VideoGridUIState(
                list = listOf(
                    VideoGridItemUIState.Title("Сезон 2"),
                    VideoGridItemUIState.Items(
                        items = listOf(
                            VideoItemUIState(
                                id = -2_000_002,
                                title = "1. Новая серия",
                                imageUrl = "",
                                bigImageUrl = "",
                                seasonNumber = 2,
                                episodeNumber = 1,
                                presentation = VideoItemPresentation.Scheduled,
                                scheduledReleaseDate = DATE_LABEL_SENTINEL,
                            ),
                        ),
                        rowKey = "season_2",
                    ),
                ),
            ),
        )
    }
}
