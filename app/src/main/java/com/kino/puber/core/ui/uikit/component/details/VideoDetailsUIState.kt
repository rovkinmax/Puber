package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.model.Lorem


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
    val isLoading: Boolean = false,
) {
    companion object {
        val Loading = VideoDetailsUIState(
            id = 0,
            title = "Movie Title \n Some Long Title",
            description = Lorem.words(10, newLineEachWordCount = 1),
            imageUrl = "",
            trailerUrl = "",
            ratings = listOf(
                RatingUIState.IMDB("9.9", isLoading = true),
                RatingUIState.KP("9.9", isLoading = true),
                RatingUIState.PUB("9.9", isLoading = true),
            ),
            year = "1991",
            genres = "horror, thriller, action",
            duration = "Длительность: 2:00",
            country = "KZ",
            isLoading = true
        )
    }
}