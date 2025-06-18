package com.kino.puber.ui.feature.favorites.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class FavoriteViewState {
    data object Loading : FavoriteViewState()

    data object Empty : FavoriteViewState()

    data class Error(val message: String) : FavoriteViewState()

    data class Content(
        val items: List<VideoItemUIState>,
    ) : FavoriteViewState()
}