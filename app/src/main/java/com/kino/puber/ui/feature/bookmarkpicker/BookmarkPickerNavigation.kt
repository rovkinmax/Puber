package com.kino.puber.ui.feature.bookmarkpicker

import com.kino.puber.core.system.IdGenerator
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult

internal fun AppRouter.openBookmarkPicker(
    item: VideoItemUIState,
    listener: (BookmarkPickerResult?) -> Unit,
) {
    openBookmarkPicker(
        itemId = item.id,
        itemTitle = item.title,
        listener = listener,
    )
}

internal fun AppRouter.openBookmarkPicker(
    itemId: Int,
    itemTitle: String,
    listener: (BookmarkPickerResult?) -> Unit,
) {
    val requestCode = IdGenerator.generateId()
    navigateForResult(
        screen = screens.bookmarkPicker(
            itemId = itemId,
            itemTitle = itemTitle,
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
        isSaved = if (isSeriesLike) isSaved else result.isBookmarked,
    )
}
