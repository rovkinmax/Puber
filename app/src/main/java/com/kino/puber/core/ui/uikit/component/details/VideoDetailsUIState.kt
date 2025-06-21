package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.RatingUIState

@Immutable
data class VideoDetailsUIState(
    val id: Int,
    val title: String,
    val description: String,
    val imageUrl: String,
    val trailerUrl: String,
    val ratings: List<RatingUIState>,
    val year: String,
    val genres: String,
    val duration: String,
    val country: String,
)