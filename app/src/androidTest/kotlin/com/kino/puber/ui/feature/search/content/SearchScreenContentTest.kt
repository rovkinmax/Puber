package com.kino.puber.ui.feature.search.content

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.search.model.SearchPresentation
import com.kino.puber.ui.feature.search.model.SearchViewState
import org.junit.Rule
import org.junit.Test

private const val RESULT_TITLE = "Unique search result"

internal class SearchScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directionDownFromFocusedInputFocusesFirstResult() {
        composeRule.setContent {
            PuberTheme {
                SearchScreenContent(
                    state = SearchViewState.Content(
                        items = listOf(
                            VideoItemUIState(
                                id = 1,
                                title = RESULT_TITLE,
                                imageUrl = "",
                                bigImageUrl = "",
                            ),
                        ),
                        presentation = titlePresentation(),
                    ),
                    onAction = {},
                )
            }
        }

        val searchInput = composeRule.onNode(hasSetTextAction())
        composeRule.waitUntil(timeoutMillis = 5_000) {
            searchInput.fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Focused) == true
        }

        searchInput
            .assertIsFocused()
            .performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }

        composeRule.onNodeWithText(RESULT_TITLE).assertIsFocused()
    }

    @Test
    fun actorMode_hidesSearchInputAndFocusesFirstResult() {
        composeRule.setContent {
            PuberTheme {
                SearchScreenContent(
                    state = SearchViewState.Content(
                        items = listOf(
                            VideoItemUIState(
                                id = 1,
                                title = RESULT_TITLE,
                                imageUrl = "",
                                bigImageUrl = "",
                            ),
                        ),
                        presentation = actorPresentation(),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithText(RESULT_TITLE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(RESULT_TITLE).assertIsFocused()
    }

    @Test
    fun actorMode_errorRetryDispatchesRetryAction() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                SearchScreenContent(
                    state = SearchViewState.Error(
                        message = "Request failed",
                        presentation = actorPresentation(),
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Повторить").performClick()

        assert(actions == listOf(CommonAction.RetryClicked))
    }

    private fun titlePresentation() = SearchPresentation(
        title = null,
        inputHint = "Введите название…",
        emptyMessage = "Ничего не найдено",
        showSearchInput = true,
        focusResultsOnContent = false,
        showRetryOnError = false,
    )

    private fun actorPresentation() = SearchPresentation(
        title = "Featuring: Tom Hanks",
        inputHint = "",
        emptyMessage = "Ничего не найдено по этому актёру",
        showSearchInput = false,
        focusResultsOnContent = true,
        showRetryOnError = true,
    )
}
