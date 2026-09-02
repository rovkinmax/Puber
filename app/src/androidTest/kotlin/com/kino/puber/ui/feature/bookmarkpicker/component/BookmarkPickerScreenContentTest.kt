package com.kino.puber.ui.feature.bookmarkpicker.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkFolderUi
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerAction
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class BookmarkPickerScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_rendersCompactFolderPopup() {
        setContent(contentState())

        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val dialogBounds = composeRule
            .onNodeWithTag(BOOKMARK_PICKER_DIALOG_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(
            dialogBounds.right - dialogBounds.left < rootBounds.right - rootBounds.left,
        )
        assertTrue(dialogBounds.left > rootBounds.left)
        assertTrue(dialogBounds.right < rootBounds.right)
        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Буду смотреть").assertIsDisplayed()
        composeRule.onNodeWithText("Для детей").assertIsDisplayed()
        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_add_folder)).assertIsDisplayed()
        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_new_folder_hint))
            .assertDoesNotExist()
    }

    @Test
    fun addFolder_opensSeparateNamePopupAndDispatchesCreate() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                var state by remember { mutableStateOf(contentState()) }
                BookmarkPickerScreenContent(
                    state = state,
                    onAction = { action ->
                        actions += action
                        state = when (action) {
                            BookmarkPickerAction.AddFolderRequested -> state.copy(
                                isCreateFolderDialogVisible = true,
                            )
                            is BookmarkPickerAction.NewFolderTitleChanged -> state.copy(
                                newFolderTitle = action.title,
                            )
                            else -> state
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_add_folder)).performClick()

        composeRule.onNodeWithTag(BOOKMARK_PICKER_DIALOG_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(BOOKMARK_CREATE_FOLDER_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_new_folder_title))
            .assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("Weekend")
        composeRule.onNodeWithText(targetString(R.string.bookmark_picker_create)).performClick()

        assertEquals(BookmarkPickerAction.AddFolderRequested, actions.first())
        assertEquals(
            "Weekend",
            actions.filterIsInstance<BookmarkPickerAction.NewFolderTitleChanged>().last().title,
        )
        assertEquals(BookmarkPickerAction.CreateFolder, actions.last())
    }

    @Test
    fun folderClick_dispatchesFolderSpecificToggle() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                BookmarkPickerScreenContent(
                    state = contentState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag(bookmarkFolderRowTag(2)).performClick()

        assertEquals(listOf(BookmarkPickerAction.FolderToggled(2)), actions)
    }

    private fun setContent(state: BookmarkPickerViewState) {
        composeRule.setContent {
            PuberTheme {
                BookmarkPickerScreenContent(state = state, onAction = {})
            }
        }
    }

    private fun contentState() = BookmarkPickerViewState.Content(
        folders = listOf(
            BookmarkFolderUi(id = 1, title = "Буду смотреть", isSelected = true),
            BookmarkFolderUi(id = 2, title = "Для детей", isSelected = false),
        ),
    )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
