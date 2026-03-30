package com.kino.puber.core.ui.uikit.component

import androidx.compose.runtime.Immutable

@Immutable
data class HeroItemState(
    val id: Int,
    val title: String,
    val wideImageUrl: String,
    val fallbackImageUrl: String,
    val year: String,
    val ratings: List<RatingUIState> = emptyList(),
    val genres: String,
    val country: String = "",
    val duration: String = "",
)
