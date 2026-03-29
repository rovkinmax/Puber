package com.kino.puber.ui.feature.collections.detail.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class CollectionDetailViewState {
    data object Loading : CollectionDetailViewState()

    @Immutable
    data class Content(
        val title: String,
        val items: List<VideoItemUIState>,
    ) : CollectionDetailViewState()

    data class Error(val message: String) : CollectionDetailViewState()
}
