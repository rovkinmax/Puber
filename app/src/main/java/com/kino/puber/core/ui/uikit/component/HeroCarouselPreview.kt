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
        rating = "8.9",
        genres = "Драма, Фантастика, Приключение",
    ),
    HeroItemState(
        id = 2,
        title = "Интерстеллар / Interstellar",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2014",
        rating = "8.6",
        genres = "Фантастика, Драма",
    ),
    HeroItemState(
        id = 3,
        title = "Дюна: Часть вторая / Dune: Part Two",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2024",
        rating = "8.3",
        genres = "Фантастика, Приключение",
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
