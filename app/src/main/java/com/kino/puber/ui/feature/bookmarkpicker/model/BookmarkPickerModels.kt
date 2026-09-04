package com.kino.puber.ui.feature.bookmarkpicker.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.model.UIAction
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class BookmarkPickerParams(
    val itemId: Int,
    val resultCode: Int,
) : Parcelable

@Parcelize
internal data class BookmarkPickerResult(
    val itemId: Int,
    val selectedFolderIds: List<Int>,
    /** Whether the quick folder is among [selectedFolderIds] — drives `VideoItemUIState.isSaved`. */
    val isInQuickFolder: Boolean = false,
) : Parcelable {
    val isBookmarked: Boolean
        get() = selectedFolderIds.isNotEmpty()
}

@Immutable
internal sealed interface BookmarkPickerViewState {
    data object Loading : BookmarkPickerViewState

    @Immutable
    data class Content(
        val folders: List<BookmarkFolderUi>,
        val newFolderTitle: String = "",
        val isCreateFolderDialogVisible: Boolean = false,
        val changingFolderIds: Set<Int> = emptySet(),
        val isCreatingFolder: Boolean = false,
        val quickFolderId: Int? = null,
    ) : BookmarkPickerViewState {
        val canCreateFolder: Boolean
            get() = newFolderTitle.isNotBlank() && !isCreatingFolder
    }

    data class Error(val message: String) : BookmarkPickerViewState
}

@Immutable
internal data class BookmarkFolderUi(
    val id: Int,
    val title: String,
    val isSelected: Boolean,
)

internal sealed interface BookmarkPickerAction : UIAction {
    data class FolderToggled(val folderId: Int) : BookmarkPickerAction
    data object AddFolderRequested : BookmarkPickerAction
    data object AddFolderDismissed : BookmarkPickerAction
    data class NewFolderTitleChanged(val title: String) : BookmarkPickerAction
    data object CreateFolder : BookmarkPickerAction
    data object Dismiss : BookmarkPickerAction
    data object Retry : BookmarkPickerAction
}
