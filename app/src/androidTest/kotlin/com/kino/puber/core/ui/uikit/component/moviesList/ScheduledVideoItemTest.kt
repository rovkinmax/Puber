package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class ScheduledVideoItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scheduledCard_isFocusableAndDoesNotForwardSelectOrContextMenu() {
        var clickCount = 0
        var contextMenuCount = 0
        composeRule.setContent {
            PuberTheme {
                VideoGrid(
                    state = VideoGridUIState(
                        list = listOf(
                            VideoGridItemUIState.Items(
                                items = listOf(
                                    VideoItemUIState(
                                        id = -1001,
                                        title = "Будущая серия",
                                        imageUrl = "",
                                        bigImageUrl = "",
                                        showTitle = true,
                                        presentation = VideoItemPresentation.Scheduled,
                                        scheduledReleaseDate = "Дата выхода: 23.08.2026",
                                    ),
                                ),
                                rowKey = "season_2",
                            ),
                        ),
                    ),
                    onItemClick = { clickCount++ },
                    onItemContextMenu = { contextMenuCount++ },
                )
            }
        }

        val card = composeRule.onNodeWithTag(SCHEDULED_VIDEO_ITEM_TEST_TAG)
        card.assertIsFocused()
        card.performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        card.performKeyInput {
            keyDown(Key.Menu)
            keyUp(Key.Menu)
        }

        composeRule.runOnIdle {
            assertEquals(0, clickCount)
            assertEquals(0, contextMenuCount)
        }
    }
}
