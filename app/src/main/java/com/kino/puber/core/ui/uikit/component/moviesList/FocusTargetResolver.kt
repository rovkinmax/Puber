package com.kino.puber.core.ui.uikit.component.moviesList

/**
 * Resolves the card that should inherit focus after a row changes.
 *
 * The focused item is kept by identity while it remains in the new list. If
 * it was removed, the card that now occupies its old position wins; when the
 * removed card was last, the previous card is used instead.
 */
internal fun resolveFocusedItemId(
    previousItems: List<VideoItemUIState>,
    updatedItems: List<VideoItemUIState>,
    focusedItemId: Int?,
): Int? {
    if (updatedItems.isEmpty()) return null
    if (focusedItemId != null && updatedItems.any { it.id == focusedItemId }) {
        return focusedItemId
    }

    val removedIndex = previousItems.indexOfFirst { it.id == focusedItemId }
    return updatedItems.getOrNull(removedIndex)?.id
        ?: updatedItems.getOrNull(removedIndex - 1)?.id
        ?: updatedItems.first().id
}

internal data class FocusableRow(
    val key: String,
    val itemCount: Int,
)

/**
 * Finds the closest remaining content row after its focused row becomes
 * empty. The following row wins ties so focus continues in reading order.
 */
internal fun nearestNonEmptyRowKey(
    rows: List<FocusableRow>,
    emptyRowIndex: Int,
): String? {
    return rows
        .mapIndexedNotNull { index, row ->
            if (row.itemCount == 0) {
                null
            } else {
                Triple(
                    kotlin.math.abs(index - emptyRowIndex),
                    if (index < emptyRowIndex) 1 else 0,
                    row.key,
                )
            }
        }
        .minWithOrNull(compareBy<Triple<Int, Int, String>> { it.first }.thenBy { it.second })
        ?.third
}

/**
 * Reconciles the focused row when the published row list changes.
 *
 * A mapper may remove a whole row instead of publishing an empty row. In
 * that case the old row index still identifies the best replacement: prefer
 * the row now occupying that index, then the preceding row.
 */
internal fun resolveFocusedRowKey(
    previousRows: List<FocusableRow>,
    updatedRows: List<FocusableRow>,
    focusedRowKey: String?,
): String? {
    if (updatedRows.isEmpty()) return null
    if (focusedRowKey != null && updatedRows.any { it.key == focusedRowKey && it.itemCount > 0 }) {
        return focusedRowKey
    }

    val oldIndex = previousRows.indexOfFirst { it.key == focusedRowKey }
    val replacementIndex = if (oldIndex >= 0) oldIndex else 0
    return nearestNonEmptyRowKey(
        rows = updatedRows,
        emptyRowIndex = replacementIndex.coerceIn(0, updatedRows.lastIndex),
    )
}
