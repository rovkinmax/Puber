package com.kino.puber.ui.feature.search.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal data class SearchPresentation(
    val title: String?,
    val inputHint: String,
    val emptyMessage: String,
    val showSearchInput: Boolean,
    val focusResultsOnContent: Boolean,
    val showRetryOnError: Boolean,
)

@Immutable
internal sealed class SearchViewState {
    abstract val presentation: SearchPresentation

    data class Idle(
        override val presentation: SearchPresentation,
    ) : SearchViewState()

    data class Loading(
        override val presentation: SearchPresentation,
    ) : SearchViewState()

    data class Empty(
        override val presentation: SearchPresentation,
    ) : SearchViewState()

    data class Error(
        val message: String,
        override val presentation: SearchPresentation,
    ) : SearchViewState()

    data class Content(
        val items: List<VideoItemUIState>,
        override val presentation: SearchPresentation,
    ) : SearchViewState()
}
