package com.kino.puber.ui.feature.collections.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class CollectionsViewState {
    data object Loading : CollectionsViewState()

    @Immutable
    data class Content(
        val collections: List<CollectionUIState> = emptyList(),
    ) : CollectionsViewState()

    data class Error(val message: String) : CollectionsViewState()
}

@Immutable
internal data class CollectionUIState(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val wideImageUrl: String,
    val count: Int,
)
