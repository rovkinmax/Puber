package com.kino.puber.ui.feature.details.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.model.UIAction

@Immutable
internal sealed class DetailsScreenState {
    data object Loading : DetailsScreenState()
    data class Error(val message: String) : DetailsScreenState()
    data class Content(
        val details: VideoDetailsUIState,
        val buttons: List<DetailsButtonUIState>,
        val isInWatchlist: Boolean,
    ) : DetailsScreenState()
}

@Immutable
internal sealed class DetailsButtonUIState {
    data class TextButton(
        val textRes: Int,
        val icon: ImageVector,
        val action: DetailsAction,
    ) : DetailsButtonUIState()

    data class IconOnly(
        val icon: ImageVector,
        val contentDescription: Int,
        val action: DetailsAction,
        val isActive: Boolean = false,
    ) : DetailsButtonUIState()
}

internal sealed class DetailsAction : UIAction {
    data object PlayClicked : DetailsAction()
    data object TrailerClicked : DetailsAction()
    data object SelectSeasonClicked : DetailsAction()
    data object WatchlistToggleClicked : DetailsAction()
}
