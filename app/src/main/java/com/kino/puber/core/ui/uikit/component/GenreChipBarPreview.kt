package com.kino.puber.core.ui.uikit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.models.Genre

private val previewGenres = listOf(
    Genre(id = 1, title = "Боевик"),
    Genre(id = 2, title = "Комедия"),
    Genre(id = 3, title = "Драма"),
    Genre(id = 4, title = "Триллер"),
    Genre(id = 5, title = "Ужасы"),
    Genre(id = 6, title = "Фантастика"),
    Genre(id = 7, title = "Мелодрама"),
)

@Preview(name = "GenreChipBar — none selected", device = TV_1080p)
@Composable
private fun GenreChipBarNonePreview() = PuberTheme {
    GenreChipBar(
        genres = previewGenres,
        selectedGenreId = null,
        onGenreSelected = {},
    )
}

@Preview(name = "GenreChipBar — genre selected", device = TV_1080p)
@Composable
private fun GenreChipBarSelectedPreview() = PuberTheme {
    GenreChipBar(
        genres = previewGenres,
        selectedGenreId = 3,
        onGenreSelected = {},
    )
}
