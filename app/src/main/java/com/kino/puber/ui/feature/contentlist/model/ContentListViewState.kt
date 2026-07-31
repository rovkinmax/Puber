package com.kino.puber.ui.feature.contentlist.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.data.api.models.Genre

@Immutable
internal data class ContentListViewState(
    val heroItems: List<HeroItemState> = emptyList(),
    val isHeroLoading: Boolean = false,
    val selectedItem: VideoDetailsUIState = VideoDetailsUIState.Loading,
    val genres: List<Genre> = emptyList(),
    val selectedGenreId: Int? = null,
    val showDetailPanel: Boolean = true,
    val showGenreChips: Boolean = false,
)
