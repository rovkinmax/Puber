package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
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
                                    items = listOf(
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
                                    rowKey = "season_$seasonNumber",
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

    @Test
    fun changedInitialFocusedItemIdRefocusesTheRetainedRow() {
        var initialFocusedItemId by mutableStateOf(1)
        val state = VideoGridUIState(
            list = listOf(
                VideoGridItemUIState.Items(
                    items = listOf(videoItem(1), videoItem(2), videoItem(3)),
                    rowKey = "retained",
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                VideoGrid(
                    state = state,
                    initialFocusedItemId = initialFocusedItemId,
                )
            }
        }

        composeRule.onNodeWithText("Item 1").assertIsFocused()
        composeRule.runOnIdle {
            initialFocusedItemId = 3
        }
        composeRule.onNodeWithText("Item 3").assertIsFocused()
    }

    @Test
    fun removingFocusedFirstItemFocusesTheRightNeighborAndNotSearch() {
        assertRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 1,
            expectedId = 2,
        )
    }

    @Test
    fun removingFocusedMiddleItemFocusesTheRightNeighborAndNotSearch() {
        assertRemovalFocus(
            previousIds = listOf(1, 2, 3, 4),
            removedId = 2,
            expectedId = 3,
        )
    }

    @Test
    fun removingFocusedLastItemFocusesThePreviousItemAndNotSearch() {
        assertRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 3,
            expectedId = 2,
        )
    }

    @Test
    fun removingOnlyItemFocusesTheNearestContentRowAndNotSearch() {
        var state by mutableStateOf(
            VideoGridUIState(
                list = listOf(
                    VideoGridItemUIState.Items(
                        items = listOf(videoItem(1)),
                        rowKey = "removed",
                    ),
                    VideoGridItemUIState.Items(
                        items = listOf(videoItem(2)),
                        rowKey = "remaining",
                    ),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Search", Modifier.focusable(), style = TextStyle.Default)
                    VideoGrid(
                        modifier = Modifier.weight(1f),
                        state = state,
                        initialFocusedItemId = 1,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Item 1").assertIsFocused()
        composeRule.runOnIdle {
            state = state.copy(
                list = listOf(
                    VideoGridItemUIState.Items(
                        items = emptyList(),
                        rowKey = "removed",
                    ),
                    VideoGridItemUIState.Items(
                        items = listOf(videoItem(2)),
                        rowKey = "remaining",
                    ),
                ),
            )
        }
        composeRule.onNodeWithText("Item 2").assertIsFocused()
        composeRule.onNodeWithText("Search").assertIsNotFocused()
    }

    @Test
    fun removingFocusedRowFocusesTheRowNowAtTheSameIndexAndNotSearch() {
        var state by mutableStateOf(
            VideoGridUIState(
                list = listOf(
                    row(key = "before", itemId = 1),
                    row(key = "removed", itemId = 2),
                    row(key = "after", itemId = 3),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Search", Modifier.focusable(), style = TextStyle.Default)
                    VideoGrid(
                        modifier = Modifier.weight(1f),
                        state = state,
                        initialFocusedItemId = 2,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Item 2").assertIsFocused()
        composeRule.runOnIdle {
            state = state.copy(
                list = listOf(
                    row(key = "before", itemId = 1),
                    row(key = "after", itemId = 3),
                ),
            )
        }
        composeRule.onNodeWithText("Item 3").assertIsFocused()
        composeRule.onNodeWithText("Search").assertIsNotFocused()
    }

    private fun assertRemovalFocus(
        previousIds: List<Int>,
        removedId: Int,
        expectedId: Int,
    ) {
        var state by mutableStateOf(
            VideoGridUIState(
                list = listOf(
                    VideoGridItemUIState.Items(
                        items = previousIds.map(::videoItem),
                        rowKey = "removable",
                    ),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Search", Modifier.focusable(), style = TextStyle.Default)
                    VideoGrid(
                        modifier = Modifier.weight(1f),
                        state = state,
                        initialFocusedItemId = removedId,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Item $removedId").assertIsFocused()
        composeRule.runOnIdle {
            state = state.copy(
                list = listOf(
                    VideoGridItemUIState.Items(
                        items = previousIds
                            .filterNot { it == removedId }
                            .map(::videoItem),
                        rowKey = "removable",
                    ),
                ),
            )
        }
        composeRule.onNodeWithText("Item $expectedId").assertIsFocused()
        composeRule.onNodeWithText("Search").assertIsNotFocused()
    }

    private fun videoItem(id: Int): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = "Item $id",
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
        )
    }

    private fun row(key: String, itemId: Int): VideoGridItemUIState.Items {
        return VideoGridItemUIState.Items(
            items = listOf(videoItem(itemId)),
            rowKey = key,
        )
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
