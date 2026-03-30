package com.kino.puber.core.ui.uikit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private val previewHeroItems = listOf(
    HeroItemState(
        id = 1,
        title = "Последний из нас / The Last of Us",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2025",
        ratings = listOf(RatingUIState.KP("8.9"), RatingUIState.IMDB("8.8")),
        genres = "Драма, Фантастика, Приключение",
        country = "США",
        duration = "Сезонов: 2",
    ),
    HeroItemState(
        id = 2,
        title = "Интерстеллар / Interstellar",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2014",
        ratings = listOf(RatingUIState.KP("8.6"), RatingUIState.IMDB("8.7")),
        genres = "Фантастика, Драма",
        country = "США, Великобритания",
        duration = "Длительность: 2 ч 49 мин",
    ),
    HeroItemState(
        id = 3,
        title = "Дюна: Часть вторая / Dune: Part Two",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2024",
        ratings = listOf(RatingUIState.IMDB("8.3"), RatingUIState.PUB("9.0")),
        genres = "Фантастика, Приключение",
        country = "США, Канада",
        duration = "Длительность: 2 ч 46 мин",
    ),
)

@Preview(name = "HeroCarousel — multiple items", device = TV_1080p)
@Composable
private fun HeroCarouselMultiplePreview() = PuberTheme {
    HeroCarousel(
        items = previewHeroItems,
        onItemClick = {},
    )
}

@Preview(name = "HeroCarousel — single item", device = TV_1080p)
@Composable
private fun HeroCarouselSinglePreview() = PuberTheme {
    HeroCarousel(
        items = listOf(previewHeroItems.first()),
        onItemClick = {},
    )
}
