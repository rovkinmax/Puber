package com.kino.puber.ui.feature.contentlist.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState

@Immutable
internal data class ContentListViewState(
    val selectedItem: VideoDetailsUIState = VideoDetailsUIState.Loading,
)
