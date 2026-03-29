package com.kino.puber.ui.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState

private val previewHeroItems = listOf(
    HeroItemState(
        id = 1,
        title = "Последний из нас",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2025",
        rating = "8.9",
        genres = "Драма, Фантастика",
    ),
    HeroItemState(
        id = 2,
        title = "Интерстеллар",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2014",
        rating = "8.6",
        genres = "Фантастика, Драма",
    ),
)

private fun previewVideoItem(id: Int, title: String) = VideoItemUIState(
    id = id,
    title = title,
    imageUrl = "",
    bigImageUrl = "",
    ratings = listOf(RatingUIState.KP("8.1")),
)

private val previewSections = listOf(
    HomeSectionState(
        title = "Продолжить просмотр",
        items = (1..5).map { previewVideoItem(it, "Фильм $it") },
        type = HomeSectionType.ContinueWatching,
    ),
    HomeSectionState(
        title = "Новинки",
        items = (10..15).map { previewVideoItem(it, "Новинка $it") },
        type = HomeSectionType.Fresh,
    ),
    HomeSectionState(
        title = "Популярные фильмы",
        items = (20..25).map { previewVideoItem(it, "Популярный $it") },
        type = HomeSectionType.PopularMovies,
    ),
)

@Preview(name = "Home — Loading", device = TV_1080p)
@Composable
private fun HomeLoadingPreview() = PuberTheme {
    HomeScreenContent(
        state = HomeViewState.Loading,
        onAction = {},
        onHeroClick = {},
        onCollectionClick = { _, _ -> },
    )
}

@Preview(name = "Home — Error", device = TV_1080p)
@Composable
private fun HomeErrorPreview() = PuberTheme {
    HomeScreenContent(
        state = HomeViewState.Error("Не удалось загрузить данные"),
        onAction = {},
        onHeroClick = {},
        onCollectionClick = { _, _ -> },
    )
}

@Preview(name = "Home — Content", device = TV_1080p)
@Composable
private fun HomeContentPreview() = PuberTheme {
    HomeScreenContent(
        state = HomeViewState.Content(
            heroItems = previewHeroItems,
            sections = previewSections,
        ),
        onAction = {},
        onHeroClick = {},
        onCollectionClick = { _, _ -> },
    )
}

@Preview(name = "Home — Content (no hero)", device = TV_1080p)
@Composable
private fun HomeContentNoHeroPreview() = PuberTheme {
    HomeScreenContent(
        state = HomeViewState.Content(
            heroItems = emptyList(),
            sections = previewSections,
        ),
        onAction = {},
        onHeroClick = {},
        onCollectionClick = { _, _ -> },
    )
}
