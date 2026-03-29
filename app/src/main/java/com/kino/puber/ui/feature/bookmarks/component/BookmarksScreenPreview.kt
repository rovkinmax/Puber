package com.kino.puber.ui.feature.bookmarks.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.ui.feature.bookmarks.model.BookmarksViewState

private val previewFolders = listOf(
    Bookmark(id = 1, title = "Буду смотреть", count = 15),
    Bookmark(id = 2, title = "Любимые", count = 8),
    Bookmark(id = 3, title = "Для детей", count = 23),
)

private val previewItems = (1..6).map { i ->
    VideoItemUIState(
        id = i,
        title = "Фильм из закладок $i",
        imageUrl = "",
        bigImageUrl = "",
        ratings = listOf(RatingUIState.KP("${7 + i % 3}.${i}")),
    )
}

@Preview(name = "Bookmarks — Loading", device = TV_1080p)
@Composable
private fun BookmarksLoadingPreview() = PuberTheme {
    BookmarksScreenContent(
        state = BookmarksViewState.Loading,
        onAction = {},
        onFolderSelected = {},
    )
}

@Preview(name = "Bookmarks — Error", device = TV_1080p)
@Composable
private fun BookmarksErrorPreview() = PuberTheme {
    BookmarksScreenContent(
        state = BookmarksViewState.Error("Не удалось загрузить закладки"),
        onAction = {},
        onFolderSelected = {},
    )
}

@Preview(name = "Bookmarks — Content", device = TV_1080p)
@Composable
private fun BookmarksContentPreview() = PuberTheme {
    BookmarksScreenContent(
        state = BookmarksViewState.Content(
            folders = previewFolders,
            selectedFolderId = 1,
            items = previewItems,
        ),
        onAction = {},
        onFolderSelected = {},
    )
}

@Preview(name = "Bookmarks — Loading items", device = TV_1080p)
@Composable
private fun BookmarksLoadingItemsPreview() = PuberTheme {
    BookmarksScreenContent(
        state = BookmarksViewState.Content(
            folders = previewFolders,
            selectedFolderId = 2,
            isLoadingItems = true,
        ),
        onAction = {},
        onFolderSelected = {},
    )
}
