package com.kino.puber.ui.feature.bookmarkpicker

import com.kino.puber.core.system.IdGenerator
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult

/**
 * Request codes share one namespace with the fixed ones such as `RESULT_CONTENT_CHANGED` (1001),
 * and [IdGenerator] hands out 1, 2, 3, ... — so raw generated ids eventually collide with a fixed
 * code and a result would be handed to a listener expecting a different type. Offsetting the
 * generated id into its own range keeps picker codes unique and clear of the fixed ones.
 */
private const val BOOKMARK_PICKER_REQUEST_CODE_BASE = 0x0B00_0000

internal fun AppRouter.openBookmarkPicker(
    item: VideoItemUIState,
    listener: (BookmarkPickerResult?) -> Unit,
) {
    openBookmarkPicker(
        itemId = item.id,
        listener = listener,
    )
}

internal fun AppRouter.openBookmarkPicker(
    itemId: Int,
    listener: (BookmarkPickerResult?) -> Unit,
) {
    val requestCode = BOOKMARK_PICKER_REQUEST_CODE_BASE + IdGenerator.generateId()
    navigateForResult(
        screen = screens.bookmarkPicker(
            itemId = itemId,
            resultCode = requestCode,
        ),
        requestCode = requestCode,
        listener = listener,
    )
}

internal fun VideoItemUIState.withBookmarkResult(
    result: BookmarkPickerResult,
): VideoItemUIState {
    if (id != result.itemId) return this
    return copy(
        isBookmarked = result.isBookmarked,
        // `isSaved` tracks the quick folder alone, not membership in any folder.
        isSaved = if (isSeriesLike) isSaved else result.isInQuickFolder,
    )
}
