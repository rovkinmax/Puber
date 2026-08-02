package com.kino.puber.ui.feature.home.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class HomeFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dpadFocusOnNonFallbackItemInNonTargetHomeRowRecordsActualIdentity() {
        var focusedItemId: Int? = null
        composeRule.setContent {
            PuberTheme {
                HomeSectionRow(
                    rowKey = "home_row",
                    items = listOf(item(0, 0), item(0, 1), item(0, 2)),
                    isTargetRow = false,
                    onSectionFocused = {},
                    onItemClick = {},
                    onItemContextMenu = null,
                    onItemFocused = { focusedItemId = it.id },
                    onRowEmpty = {},
                )
            }
        }

        requestFocus(itemTitle(row = 0, column = 0))
        composeRule.runOnIdle {
            focusedItemId = null
        }
        pressCurrent(Key.DirectionRight)

        composeRule.runOnIdle {
            assertEquals(1, focusedItemId)
        }
        composeRule.onNodeWithText(itemTitle(row = 0, column = 1)).assertIsFocused()
    }

    @Test
    fun downToEndRightThenUpRestoresHomeRowTargetsInsideViewport() {
        val sections = HOME_ROW_TYPES.mapIndexed { row, type ->
            HomeSectionState(
                title = "Home row $row",
                type = type,
                items = (0 until ITEM_COUNT).map { column -> item(row, column) },
            )
        }
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = HomeViewState.Content(sections = sections),
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        requestFocus(itemTitle(0, 0))
        seedStableTargets()

        val snapshots = buildList {
            repeat(HOME_ROW_TYPES.lastIndex) {
                add(pressCurrentAndCapture(Key.DirectionDown))
            }
            add(pressCurrentAndCapture(Key.DirectionRight))
            repeat(HOME_ROW_TYPES.lastIndex) {
                add(pressCurrentAndCapture(Key.DirectionUp))
            }
        }
        val expected = buildList {
            add(FocusTarget(HomeSectionType.WatchLater.name, 11))
            add(FocusTarget(HomeSectionType.Bookmarks.name, 20))
            add(FocusTarget(HomeSectionType.Fresh.name, 31))
            add(FocusTarget(HomeSectionType.PopularMovies.name, 41))
            add(FocusTarget(HomeSectionType.PopularMovies.name, 42))
            add(FocusTarget(HomeSectionType.Fresh.name, 31))
            add(FocusTarget(HomeSectionType.Bookmarks.name, 20))
            add(FocusTarget(HomeSectionType.WatchLater.name, 11))
            add(FocusTarget(HomeSectionType.ContinueWatching.name, 0))
        }

        assertEquals(
            "Home focus trace for Down-to-end → Right → Up",
            expected,
            snapshots.map(FocusSnapshot::target),
        )
        assertBoundsAndViewport(snapshots)
    }

    private fun seedStableTargets() {
        repeat(HOME_ROW_TYPES.lastIndex) { index ->
            val row = index + 1
            pressCurrent(Key.DirectionDown)
            requestFocus(
                when (row) {
                    1, 3, 4 -> itemTitle(row, 1)
                    else -> itemTitle(row, 0)
                },
            )
        }
        repeat(HOME_ROW_TYPES.lastIndex) {
            pressCurrent(Key.DirectionUp)
        }
        requestFocus(itemTitle(0, 0))
    }

    private fun requestFocus(title: String) {
        composeRule
            .onNodeWithText(title)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()
    }

    private fun pressCurrent(key: Key) {
        focusedCard().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }

    private fun pressCurrentAndCapture(key: Key): FocusSnapshot {
        pressCurrent(key)
        val focusedCard = focusedCard()
        val title = focusedCard.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = " ") { it.text }
        val match = requireNotNull(ITEM_TITLE_PATTERN.find(title)) {
            "Focused Home card has no item identity: $title"
        }
        val row = match.groupValues[1].toInt()
        val column = match.groupValues[2].toInt()
        return FocusSnapshot(
            target = FocusTarget(
                row = HOME_ROW_TYPES[row].name,
                item = row * 10 + column,
            ),
            bounds = focusedCard.getUnclippedBoundsInRoot(),
        )
    }

    private fun focusedCard(): SemanticsNodeInteraction {
        return composeRule.onNode(
            isFocused() and hasText(ITEM_TITLE_PREFIX, substring = true),
        )
    }

    private fun assertBoundsAndViewport(snapshots: List<FocusSnapshot>) {
        val viewport = composeRule.onRoot().getUnclippedBoundsInRoot()
        snapshots.forEach { snapshot ->
            assertTrue(
                "${snapshot.target} top ${snapshot.bounds.top} is above viewport ${viewport.top}",
                snapshot.bounds.top.value >= viewport.top.value - BOUNDS_TOLERANCE,
            )
            assertTrue(
                "${snapshot.target} bottom ${snapshot.bounds.bottom} is below viewport ${viewport.bottom}",
                snapshot.bounds.bottom.value <= viewport.bottom.value + BOUNDS_TOLERANCE,
            )
        }
        snapshots.zipWithNext().forEach { (before, after) ->
            assertTrue(
                "vertical focus delta from ${before.target} to ${after.target} was " +
                    "${abs(after.bounds.top.value - before.bounds.top.value)}dp",
                abs(after.bounds.top.value - before.bounds.top.value) <= MAX_VERTICAL_DELTA.value,
            )
        }
        assertVerticalBoundsEqual(
            before = snapshots[HOME_ROW_TYPES.lastIndex - 1].bounds,
            after = snapshots[HOME_ROW_TYPES.lastIndex].bounds,
        )
    }

    private fun assertVerticalBoundsEqual(before: DpRect, after: DpRect) {
        assertEquals("top after Right", before.top.value, after.top.value, BOUNDS_TOLERANCE)
        assertEquals("bottom after Right", before.bottom.value, after.bottom.value, BOUNDS_TOLERANCE)
    }

    private data class FocusTarget(
        val row: String,
        val item: Int,
    )

    private data class FocusSnapshot(
        val target: FocusTarget,
        val bounds: DpRect,
    )

    private companion object {
        const val ITEM_COUNT = 3
        const val ITEM_TITLE_PREFIX = "home-row-"
        const val BOUNDS_TOLERANCE = 1f
        val MAX_VERTICAL_DELTA = 240.dp
        val ITEM_TITLE_PATTERN = Regex("""home-row-(\d+)-item-(\d+)""")
        val HOME_ROW_TYPES = listOf(
            HomeSectionType.ContinueWatching,
            HomeSectionType.WatchLater,
            HomeSectionType.Bookmarks,
            HomeSectionType.Fresh,
            HomeSectionType.PopularMovies,
        )

        fun item(row: Int, column: Int) = VideoItemUIState(
            id = row * 10 + column,
            title = itemTitle(row, column),
            imageUrl = "",
            bigImageUrl = "",
        )

        fun itemTitle(row: Int, column: Int) = "home-row-$row-item-$column"
    }
}
