package com.kino.puber.ui.feature.contentlist.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class SectionState {
    data object Loading : SectionState()

    data class Content(
        val items: List<VideoItemUIState>,
        val isLoadingMore: Boolean = false,
    ) : SectionState()

    data class Error(val message: String) : SectionState()

    data object Empty : SectionState()
}
