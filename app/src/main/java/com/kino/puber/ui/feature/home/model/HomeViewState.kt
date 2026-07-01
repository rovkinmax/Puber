package com.kino.puber.ui.feature.home.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState

@Immutable
internal sealed class HomeViewState {
    abstract val apiDomainDialog: ApiDomainDialogState?

    @Immutable
    data class Loading(
        val message: String? = null,
        override val apiDomainDialog: ApiDomainDialogState? = null,
    ) : HomeViewState()

    @Immutable
    data class Content(
        val heroItems: List<HeroItemState> = emptyList(),
        val sections: List<HomeSectionState> = emptyList(),
        override val apiDomainDialog: ApiDomainDialogState? = null,
    ) : HomeViewState()

    data class Error(
        val message: String,
        override val apiDomainDialog: ApiDomainDialogState? = null,
    ) : HomeViewState()
}

@Immutable
internal data class HomeSectionState(
    val title: String,
    val items: List<VideoItemUIState>,
    val type: HomeSectionType,
)

internal enum class HomeSectionType {
    ContinueWatching,
    WatchLater,
    Bookmarks,
    Fresh,
    PopularMovies,
    PopularSeries,
    Collections,
    Hot,
}
