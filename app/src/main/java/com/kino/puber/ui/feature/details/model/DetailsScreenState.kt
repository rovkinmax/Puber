package com.kino.puber.ui.feature.details.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState

@Immutable
internal sealed class DetailsScreenState {
    data object Loading : DetailsScreenState()
    data class Error(val message: String) : DetailsScreenState()
    data class Content(val details: VideoDetailsUIState)
}