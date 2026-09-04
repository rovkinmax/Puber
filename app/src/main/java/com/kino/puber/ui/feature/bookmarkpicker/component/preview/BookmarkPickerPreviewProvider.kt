package com.kino.puber.ui.feature.bookmarkpicker.component.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkFolderUi
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerViewState

internal class BookmarkPickerPreviewProvider : PreviewParameterProvider<BookmarkPickerViewState> {
    override val values = sequenceOf(
        BookmarkPickerViewState.Loading,
        BookmarkPickerViewState.Content(
            folders = listOf(
                BookmarkFolderUi(1, "Буду смотреть", true),
                BookmarkFolderUi(2, "Для детей", false),
            ),
        ),
        BookmarkPickerViewState.Content(
            folders = emptyList(),
        ),
        BookmarkPickerViewState.Content(
            folders = listOf(BookmarkFolderUi(1, "Буду смотреть", true)),
            isCreateFolderDialogVisible = true,
        ),
        BookmarkPickerViewState.Error("Unable to load bookmark folders"),
    )
}
