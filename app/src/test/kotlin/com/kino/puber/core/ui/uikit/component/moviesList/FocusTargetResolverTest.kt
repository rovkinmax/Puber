package com.kino.puber.core.ui.uikit.component.moviesList

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FocusTargetResolverTest {

    @Test
    fun focusedFirstItemFallsThroughToTheItemAtTheRemovedIndex() {
        assertEquals(
            2,
            resolveFocusedItemId(
                previousItems = items(1, 2, 3),
                updatedItems = items(2, 3),
                focusedItemId = 1,
            ),
        )
    }

    @Test
    fun focusedMiddleItemFallsThroughToTheRightNeighbor() {
        assertEquals(
            3,
            resolveFocusedItemId(
                previousItems = items(1, 2, 3, 4),
                updatedItems = items(1, 3, 4),
                focusedItemId = 2,
            ),
        )
    }

    @Test
    fun focusedLastItemFallsBackToThePreviousItem() {
        assertEquals(
            2,
            resolveFocusedItemId(
                previousItems = items(1, 2, 3),
                updatedItems = items(1, 2),
                focusedItemId = 3,
            ),
        )
    }

    @Test
    fun removingOnlyItemLeavesNoItemTarget() {
        assertEquals(
            null,
            resolveFocusedItemId(
                previousItems = items(1),
                updatedItems = emptyList(),
                focusedItemId = 1,
            ),
        )
    }

    @Test
    fun emptyRowSelectsTheNearestRemainingRow() {
        assertEquals(
            "row_after",
            nearestNonEmptyRowKey(
                rows = listOf(
                    FocusableRow("row_before", 2),
                    FocusableRow("row_empty", 0),
                    FocusableRow("row_after", 2),
                ),
                emptyRowIndex = 1,
            ),
        )
    }

    @Test
    fun removingFocusedRowSelectsTheRowNowAtItsIndex() {
        assertEquals(
            "row_after",
            resolveFocusedRowKey(
                previousRows = rows("row_before", "row_removed", "row_after"),
                updatedRows = rows("row_before", "row_after"),
                focusedRowKey = "row_removed",
            ),
        )
    }

    @Test
    fun removingTheOnlyFocusedRowSelectsTheRemainingContentRow() {
        assertEquals(
            "row_after",
            resolveFocusedRowKey(
                previousRows = rows("row_removed", "row_after"),
                updatedRows = rows("row_after"),
                focusedRowKey = "row_removed",
            ),
        )
    }

    @Test
    fun removingFocusedLastRowSelectsThePreviousContentRow() {
        assertEquals(
            "row_before",
            resolveFocusedRowKey(
                previousRows = rows("row_before", "row_removed"),
                updatedRows = rows("row_before"),
                focusedRowKey = "row_removed",
            ),
        )
    }

    private fun items(vararg ids: Int): List<VideoItemUIState> {
        return ids.map { id ->
            VideoItemUIState(
                id = id,
                title = "Item $id",
                imageUrl = "",
                bigImageUrl = "",
                showTitle = true,
            )
        }
    }

    private fun rows(vararg keys: String): List<FocusableRow> {
        return keys.map { FocusableRow(key = it, itemCount = 1) }
    }
}
