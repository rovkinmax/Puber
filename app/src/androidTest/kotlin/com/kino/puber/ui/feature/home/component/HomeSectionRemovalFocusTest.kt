package com.kino.puber.ui.feature.home.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import org.junit.Rule
import org.junit.Test

internal class HomeSectionRemovalFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun removingTheFocusedSectionSelectsTheReplacementSection() {
        var state by mutableStateOf(
            homeState(
                sections = listOf(
                    section(HomeSectionType.ContinueWatching, "Focused"),
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = state,
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText("Focused card")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle {
            state = homeState(
                sections = listOf(
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            )
        }

        composeRule.onNodeWithText("Replacement card").assertIsFocused()
    }

    private fun homeState(
        sections: List<HomeSectionState>,
    ) = HomeViewState.Content(sections = sections)

    private fun section(
        type: HomeSectionType,
        title: String,
    ) = HomeSectionState(
        title = "$title section",
        type = type,
        items = listOf(
            VideoItemUIState(
                id = title.hashCode(),
                title = "$title card",
                imageUrl = "",
                bigImageUrl = "",
            ),
        ),
    )
}
