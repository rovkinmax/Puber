package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.core.ui.uikit.model.TvContextMenuAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val SOURCE_TAG = "context-menu-source"
private const val MENU_TITLE = "Context menu"
private const val FIRST_ACTION = "First action"
private const val LONG_PRESS_DELAY_MS = 500L
private const val NEXT_REPEAT_DELAY_MS = 50L

internal class TvContextMenuLongPressTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun releasingLongSelectAfterDialogFocusesAction_doesNotInvokeAction() {
        var actionInvocations = 0
        setContextMenuContent(onAction = { actionInvocations += 1 })
        val source = composeRule.onNodeWithTag(SOURCE_TAG)

        source.performKeyInput {
            keyDown(Key.Enter)
            advanceEventTime(LONG_PRESS_DELAY_MS)
        }

        val firstAction = composeRule.waitForFirstActionFocus()
        firstAction.performKeyInput {
            advanceEventTime(NEXT_REPEAT_DELAY_MS)
            keyUp(Key.Enter)
        }

        composeRule.runOnIdle {
            assertEquals(0, actionInvocations)
        }

        firstAction.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }

        composeRule.runOnIdle {
            assertEquals(1, actionInvocations)
        }
    }

    @Test
    fun openingWithDedicatedMenuKey_heldSelectWithRepeatsInvokesActionOnce() {
        var actionInvocations = 0
        setContextMenuContent(onAction = { actionInvocations += 1 })
        val source = composeRule.onNodeWithTag(SOURCE_TAG)

        source.performKeyInput {
            keyDown(Key.Menu)
            keyUp(Key.Menu)
        }

        val firstAction = composeRule.waitForFirstActionFocus()
        firstAction.performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(LONG_PRESS_DELAY_MS)
            advanceEventTime(NEXT_REPEAT_DELAY_MS)
            keyUp(Key.DirectionCenter)
        }

        composeRule.runOnIdle {
            assertEquals(1, actionInvocations)
        }
    }

    private fun setContextMenuContent(onAction: () -> Unit) {
        composeRule.setContent {
            PuberTheme {
                var menuVisible by remember { mutableStateOf(false) }
                BasicText(
                    text = "Open menu",
                    modifier = Modifier
                        .testTag(SOURCE_TAG)
                        .onTvContextMenuKey { menuVisible = true }
                        .focusable(),
                )
                if (menuVisible) {
                    TvContextMenuDialog(
                        title = MENU_TITLE,
                        actions = listOf(
                            TvContextMenuAction(
                                id = "first",
                                title = FIRST_ACTION,
                            ),
                        ),
                        onAction = { onAction() },
                        onDismiss = { menuVisible = false },
                    )
                }
            }
        }
        composeRule
            .onNodeWithTag(SOURCE_TAG)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForFirstActionFocus() =
        onNodeWithText(FIRST_ACTION, useUnmergedTree = true)
            .onParent()
            .also { action ->
                waitUntil {
                    action.fetchSemanticsNode().config
                        .getOrNull(SemanticsProperties.Focused) == true
                }
                onNodeWithText(MENU_TITLE).assertExists()
                action.assertIsFocused()
            }
}
