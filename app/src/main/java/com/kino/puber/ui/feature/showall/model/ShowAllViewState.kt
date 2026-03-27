package com.kino.puber.ui.feature.showall.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class ShowAllViewState {
    data object Loading : ShowAllViewState()

    data object Empty : ShowAllViewState()

    data class Error(val message: String) : ShowAllViewState()

    data class Content(
        val items: List<VideoItemUIState>,
        val selectedItem: VideoDetailsUIState = VideoDetailsUIState.Loading,
        val isLoadingMore: Boolean = false,
    ) : ShowAllViewState()
}
