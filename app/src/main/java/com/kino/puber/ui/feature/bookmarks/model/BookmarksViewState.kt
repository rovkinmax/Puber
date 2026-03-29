package com.kino.puber.ui.feature.bookmarks.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Bookmark

@Immutable
internal sealed class BookmarksViewState {
    data object Loading : BookmarksViewState()

    @Immutable
    data class Content(
        val folders: List<Bookmark> = emptyList(),
        val selectedFolderId: Int? = null,
        val items: List<VideoItemUIState> = emptyList(),
        val isLoadingItems: Boolean = false,
    ) : BookmarksViewState()

    data class Error(val message: String) : BookmarksViewState()
}
