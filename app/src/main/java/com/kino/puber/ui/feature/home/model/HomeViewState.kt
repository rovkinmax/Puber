package com.kino.puber.ui.feature.home.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState

@Immutable
internal sealed class HomeViewState {
    data object Loading : HomeViewState()

    @Immutable
    data class Content(
        val heroItems: List<HeroItemState> = emptyList(),
        val sections: List<HomeSectionState> = emptyList(),
    ) : HomeViewState()

    data class Error(val message: String) : HomeViewState()
}

@Immutable
internal data class HomeSectionState(
    val title: String,
    val items: List<VideoItemUIState>,
    val type: HomeSectionType,
)

internal enum class HomeSectionType {
    ContinueWatching,
    Fresh,
    PopularMovies,
    PopularSeries,
    Bookmarks,
    Collections,
    Hot,
}
