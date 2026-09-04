package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Rule
import org.junit.Test

internal class BookmarkIndicatorBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun badgeIsShown_forAnItemFiledInAFolder_inExtendedMode() {
        setGridContent(isBookmarked = true, bookmarkMode = BookmarkMode.Extended)

        composeRule
            .onNodeWithTag(BOOKMARK_INDICATOR_TEST_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun badgeIsAbsent_inSimpleMode_whereTheSaveRowCannotUnfileTheItem() {
        setGridContent(isBookmarked = true, bookmarkMode = BookmarkMode.Simple)

        composeRule
            .onNodeWithTag(BOOKMARK_INDICATOR_TEST_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun badgeIsAbsent_forAnItemThatIsInNoFolder() {
        setGridContent(isBookmarked = false, bookmarkMode = BookmarkMode.Extended)

        composeRule
            .onNodeWithTag(BOOKMARK_INDICATOR_TEST_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    private fun setGridContent(isBookmarked: Boolean, bookmarkMode: BookmarkMode) {
        composeRule.setContent {
            PuberTheme {
                VideoGrid(
                    state = VideoGridUIState(
                        list = listOf(
                            VideoGridItemUIState.Items(
                                items = listOf(
                                    VideoItemUIState(
                                        id = 42,
                                        title = "Фильм",
                                        imageUrl = "",
                                        bigImageUrl = "",
                                        isBookmarked = isBookmarked,
                                        bookmarkMode = bookmarkMode,
                                    ),
                                ),
                                rowKey = "row",
                            ),
                        ),
                    ),
                    onItemClick = {},
                    onItemContextMenu = {},
                )
            }
        }
    }
}
