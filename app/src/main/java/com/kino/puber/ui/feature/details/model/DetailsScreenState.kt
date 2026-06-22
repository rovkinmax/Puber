package com.kino.puber.ui.feature.details.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction

@Immutable
internal sealed class DetailsScreenState {
    data object Loading : DetailsScreenState()
    data class Error(val message: String) : DetailsScreenState()
    data class Content(
        val details: VideoDetailsUIState,
        val info: DetailsInfoUIState,
        val buttons: List<DetailsButtonUIState>,
        val isInWatchlist: Boolean,
        val isWatched: Boolean,
        val seasonsPanelVisible: Boolean = false,
        val episodes: VideoGridUIState? = null,
        val trailerUrl: String? = null,
    ) : DetailsScreenState()
}

@Immutable
internal sealed class DetailsButtonUIState {
    data class TextButton(
        val textRes: Int,
        val icon: ImageVector,
        val action: DetailsAction,
        val textOverride: String? = null,
    ) : DetailsButtonUIState()

    data class IconOnly(
        val icon: ImageVector,
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState()

    data class WatchlistToggle(
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState()

    data class WatchedToggle(
        val contentDescription: Int,
        val action: DetailsAction,
    ) : DetailsButtonUIState()
}

@Immutable
internal data class DetailsInfoUIState(
    val description: String,
    val ratings: List<RatingUIState>,
    val primaryRows: List<DetailsInfoRowUIState>,
    val secondaryRows: List<DetailsInfoRowUIState>,
)

@Immutable
internal data class DetailsInfoRowUIState(
    val label: String,
    val value: String,
)

@Immutable
internal sealed class DetailsAction : UIAction {
    data object PlayClicked : DetailsAction()
    data object TrailerClicked : DetailsAction()
    data object SelectSeasonClicked : DetailsAction()
    data object WatchlistToggleClicked : DetailsAction()
    data object WatchedToggleClicked : DetailsAction()
    data class EpisodeSelected(val item: VideoItemUIState) : DetailsAction()
    data object CloseSeasonsPanel : DetailsAction()
    data object CloseTrailer : DetailsAction()
}
