package com.kino.puber.ui.feature.search.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class SearchViewState {
    data object Idle : SearchViewState()

    data object Loading : SearchViewState()

    data object Empty : SearchViewState()

    data class Error(val message: String) : SearchViewState()

    data class Content(
        val items: List<VideoItemUIState>,
    ) : SearchViewState()
}
