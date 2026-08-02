package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class SectionRowFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dpadFocusOnNonFallbackItemInNonTargetRowRecordsActualIdentity() {
        var focusedItemId: Int? = null
        composeRule.setContent {
            PuberTheme {
                SectionRowContent(
                    state = SectionState.Content(
                        items = listOf(item(0, 0), item(0, 1), item(0, 2)),
                    ),
                    config = SectionConfig(id = "row_0", title = "Row 0"),
                    isTargetRow = false,
                    onItemClick = {},
                    onItemContextMenu = {},
                    onItemFocused = { focusedItemId = it.id },
                    onSectionFocused = {},
                    onRetry = {},
                    onLoadMore = {},
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
    fun downToEndRightThenUpRestoresGenericRowTargetsInsideViewport() {
        val rows = (0 until ROW_COUNT).map { rowIndex ->
            RowFixture(
                config = SectionConfig(id = "row_$rowIndex", title = "Row $rowIndex"),
                state = SectionState.Content(
                    items = listOf(
                        item(rowIndex, 0),
                        item(rowIndex, 1),
                        item(rowIndex, 2),
                    ),
                ),
            )
        }
        var focusedRowIndex by mutableIntStateOf(0)
        val trace = mutableStateOf<List<FocusTarget>>(emptyList())

        composeRule.setContent {
            PuberTheme {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(VIEWPORT_HEIGHT)
                        .testTag(VIEWPORT_TAG),
                ) {
                    PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(VIEWPORT_HEIGHT)
                                .focusGroup(),
                        ) {
                            items(rows, key = { it.config.id }) { row ->
                                SectionRowContent(
                                    state = row.state,
                                    config = row.config,
                                    isTargetRow = rows.indexOf(row) == focusedRowIndex,
                                    onItemClick = {},
                                    onItemContextMenu = {},
                                    onItemFocused = { item ->
                                        trace.value += FocusTarget(
                                            row = row.config.id,
                                            item = item.id,
                                        )
                                    },
                                    onSectionFocused = {
                                        focusedRowIndex = rows.indexOf(row)
                                    },
                                    onRetry = {},
                                    onLoadMore = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        requestFocus(itemTitle(0, 0))
        seedStableTargets()
        composeRule.runOnIdle {
            trace.value = emptyList()
        }

        val snapshots = buildList {
            repeat(ROW_COUNT - 1) {
                add(pressCurrentAndCapture(Key.DirectionDown, trace))
            }
            add(pressCurrentAndCapture(Key.DirectionRight, trace))
            repeat(ROW_COUNT - 1) {
                add(pressCurrentAndCapture(Key.DirectionUp, trace))
            }
        }
        val expected = buildList {
            add(FocusTarget("row_1", 11))
            add(FocusTarget("row_2", 20))
            add(FocusTarget("row_3", 31))
            add(FocusTarget("row_4", 41))
            add(FocusTarget("row_4", 42))
            add(FocusTarget("row_3", 31))
            add(FocusTarget("row_2", 20))
            add(FocusTarget("row_1", 11))
            add(FocusTarget("row_0", 0))
        }

        assertEquals(
            "generic settled focus trace for Down-to-end → Right → Up; " +
                "raw focus callbacks=${trace.value}",
            expected,
            snapshots.map(FocusSnapshot::target),
        )
        assertBoundsAndViewport(snapshots)
    }

    private fun seedStableTargets() {
        repeat(ROW_COUNT - 1) { index ->
            val row = index + 1
            pressCurrent(Key.DirectionDown)
            requestFocus(
                when (row) {
                    1, 3, 4 -> itemTitle(row, 1)
                    else -> itemTitle(row, 0)
                },
            )
        }
        repeat(ROW_COUNT - 1) {
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
        composeRule
            .onNode(focusedCardMatcher, useUnmergedTree = true)
            .performKeyInput {
                keyDown(key)
                keyUp(key)
            }
        composeRule.waitForIdle()
    }

    private fun pressCurrentAndCapture(
        key: Key,
        trace: androidx.compose.runtime.State<List<FocusTarget>>,
    ): FocusSnapshot {
        pressCurrent(key)
        val target = trace.value.last()
        val row = target.row.removePrefix("row_").toInt()
        val column = target.item % 10
        val bounds = composeRule
            .onNodeWithText(itemTitle(row, column))
            .assertIsFocused()
            .getUnclippedBoundsInRoot()
        return FocusSnapshot(target = target, bounds = bounds)
    }

    private fun assertBoundsAndViewport(snapshots: List<FocusSnapshot>) {
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).getUnclippedBoundsInRoot()
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
            before = snapshots[ROW_COUNT - 2].bounds,
            after = snapshots[ROW_COUNT - 1].bounds,
        )
    }

    private fun assertVerticalBoundsEqual(before: DpRect, after: DpRect) {
        assertEquals("top after Right", before.top.value, after.top.value, BOUNDS_TOLERANCE)
        assertEquals("bottom after Right", before.bottom.value, after.bottom.value, BOUNDS_TOLERANCE)
    }

    private data class RowFixture(
        val config: SectionConfig,
        val state: SectionState.Content,
    )

    private data class FocusTarget(
        val row: String,
        val item: Int,
    )

    private data class FocusSnapshot(
        val target: FocusTarget,
        val bounds: DpRect,
    )

    private companion object {
        const val ROW_COUNT = 5
        const val VIEWPORT_TAG = "section_traversal_viewport"
        const val BOUNDS_TOLERANCE = 1f
        val VIEWPORT_HEIGHT = 420.dp
        val MAX_VERTICAL_DELTA = 210.dp
        val focusedCardMatcher = isFocused() and hasAnyDescendant(
            hasText("row-", substring = true),
        )

        fun item(row: Int, column: Int) = VideoItemUIState(
            id = row * 10 + column,
            title = itemTitle(row, column),
            imageUrl = "",
            bigImageUrl = "",
        )

        fun itemTitle(row: Int, column: Int) = "row-$row-item-$column"
    }
}
