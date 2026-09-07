package com.kino.puber.core.ui.uikit.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The context menu is how a bookmark request reaches a ViewModel, and which entry it offers
 * depends on the configured bookmark mode: the folder picker replaces the one-tap save in
 * extended mode, while a series keeps its watchlist entry either way.
 */
internal class VideoItemContextMenuBookmarkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun extendedMode_offersTheFolderPickerAndDispatchesTheBookmarkRequest() {
        val item = videoItem(bookmarkMode = BookmarkMode.Extended)
        val actions = setContent(item)

        composeRule.onNodeWithText(manageBookmarksLabel).assertIsDisplayed()
        composeRule.onNodeWithText(addToBookmarksLabel).assertDoesNotExist()

        composeRule.menuRow(manageBookmarksLabel).activate()

        val dispatched = actions.single()
        assertTrue("Expected a bookmark request, got $dispatched", dispatched is CommonAction.ItemBookmarksRequested<*>)
        assertEquals(item, (dispatched as CommonAction.ItemBookmarksRequested<*>).item)
    }

    @Test
    fun simpleMode_keepsTheOneTapSaveAndHidesTheFolderPicker() {
        val item = videoItem(bookmarkMode = BookmarkMode.Simple)
        val actions = setContent(item)

        composeRule.onNodeWithText(addToBookmarksLabel).assertIsDisplayed()
        composeRule.onNodeWithText(manageBookmarksLabel).assertDoesNotExist()

        composeRule.menuRow(addToBookmarksLabel).activate()

        val dispatched = actions.single()
        assertTrue("Expected a saved toggle, got $dispatched", dispatched is CommonAction.ItemSavedChanged<*>)
        val savedChange = dispatched as CommonAction.ItemSavedChanged<*>
        assertEquals(item, savedChange.item)
        assertTrue("The menu offers 'add', so it must request saving", savedChange.isSaved)
    }

    @Test
    fun extendedModeSeries_keepsTheWatchlistEntryAlongsideTheFolderPicker() {
        val item = videoItem(bookmarkMode = BookmarkMode.Extended, isSeriesLike = true)
        setContent(item)

        composeRule.onNodeWithText(addToWatchlistLabel).assertIsDisplayed()
        composeRule.onNodeWithText(manageBookmarksLabel).assertIsDisplayed()
    }

    private fun setContent(item: VideoItemUIState): List<UIAction> {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                VideoItemContextMenuDialog(
                    item = item,
                    onDismiss = {},
                    onAction = actions::add,
                )
            }
        }
        return actions
    }

    private fun videoItem(
        bookmarkMode: BookmarkMode,
        isSeriesLike: Boolean = false,
    ) = VideoItemUIState(
        id = 42,
        title = "Item 42",
        imageUrl = "",
        bigImageUrl = "",
        isSeriesLike = isSeriesLike,
        bookmarkMode = bookmarkMode,
    )

    private val manageBookmarksLabel: String get() = targetString(R.string.context_menu_manage_bookmarks)
    private val addToBookmarksLabel: String get() = targetString(R.string.context_menu_add_to_bookmarks)
    private val addToWatchlistLabel: String get() = targetString(R.string.context_menu_add_to_watchlist)

    /** The clickable row is the parent of the label the menu renders. */
    private fun androidx.compose.ui.test.junit4.ComposeTestRule.menuRow(
        label: String,
    ): SemanticsNodeInteraction = onNodeWithText(label, useUnmergedTree = true).onParent()

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}

/**
 * `androidx.tv.material3` menu rows activate on DPAD enter while focused and expose that as a
 * semantics click; they register no pointer input, so `performClick()` never reaches their
 * `onClick`.
 */
private fun SemanticsNodeInteraction.activate(): SemanticsNodeInteraction = apply {
    performSemanticsAction(SemanticsActions.OnClick)
}
