package com.kino.puber.ui.feature.details.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Play
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.VideoCamera
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState

// region Preview Data

private val previewSeriesDetails = VideoDetailsUIState(
    id = 1,
    title = "Кибердеревня",
    description = "Год 2049-й. Уже давно изобретены межпланетные перелёты, суперкомпьютеры и роботы. Но в российской глубинке об этом не слышали.",
    imageUrl = "",
    trailerUrl = "",
    ratings = listOf(
        RatingUIState.KP("8.1"),
        RatingUIState.IMDB("7.4"),
    ),
    year = "2023",
    genres = "Фантастика, Комедия",
    duration = "Сезонов: 2",
    country = "Россия",
)

private val previewMovieDetails = VideoDetailsUIState(
    id = 2,
    title = "Граф Монте-Кристо",
    description = "Молодой моряк Эдмон Дантес оказывается несправедливо заключён в замке Иф. Через 14 лет он бежит и возвращается, чтобы отомстить.",
    imageUrl = "",
    trailerUrl = "",
    ratings = listOf(
        RatingUIState.KP("8.5"),
        RatingUIState.IMDB("8.2"),
        RatingUIState.PUB("9.1"),
    ),
    year = "2024",
    genres = "Драма, Приключения",
    duration = "Длительность: 2 ч 58 мин",
    country = "Франция",
)

private val previewSeriesButtons = listOf(
    DetailsButtonUIState.TextButton(
        textRes = R.string.video_details_button_watch_series,
        icon = PhosphorIcons.Duotone.Play,
        action = DetailsAction.PlayClicked,
        textOverride = "2 сезон, 6 серия",
    ),
    DetailsButtonUIState.TextButton(
        textRes = R.string.video_details_button_select_season,
        icon = PhosphorIcons.Duotone.Playlist,
        action = DetailsAction.SelectSeasonClicked,
    ),
    DetailsButtonUIState.IconOnly(
        icon = PhosphorIcons.Duotone.VideoCamera,
        contentDescription = R.string.video_details_button_trailer,
        action = DetailsAction.TrailerClicked,
    ),
    DetailsButtonUIState.WatchlistToggle(
        contentDescription = R.string.video_details_button_add_to_watchlist,
        action = DetailsAction.WatchlistToggleClicked,
    ),
)

private val previewMovieButtons = listOf(
    DetailsButtonUIState.TextButton(
        textRes = R.string.video_details_button_watch_movie,
        icon = PhosphorIcons.Duotone.Play,
        action = DetailsAction.PlayClicked,
    ),
    DetailsButtonUIState.TextButton(
        textRes = R.string.video_details_button_trailer,
        icon = PhosphorIcons.Duotone.FilmSlate,
        action = DetailsAction.TrailerClicked,
    ),
    DetailsButtonUIState.WatchlistToggle(
        contentDescription = R.string.video_details_button_add_to_bookmarks,
        action = DetailsAction.WatchlistToggleClicked,
    ),
)

private val previewEpisodes = VideoGridUIState(
    list = listOf(
        VideoGridItemUIState.Title("1 сезон, 8 серий"),
        VideoGridItemUIState.Items(
            listOf(
                VideoItemUIState(1, "1. Чужой в деревне", "", "", showTitle = true),
                VideoItemUIState(2, "2. Молоко с доставкой", "", "", showTitle = true),
                VideoItemUIState(3, "3. Трактористы", "", "", showTitle = true),
                VideoItemUIState(4, "4. Большой брат", "", "", showTitle = true),
            )
        ),
        VideoGridItemUIState.Title("2 сезон, 8 серий"),
        VideoGridItemUIState.Items(
            listOf(
                VideoItemUIState(5, "1. Новые горизонты", "", "", showTitle = true),
                VideoItemUIState(6, "2. Код деревни", "", "", showTitle = true),
                VideoItemUIState(7, "3. Извинигород", "", "", showTitle = true),
                VideoItemUIState(8, "4. Дед-Код", "", "", showTitle = true),
            )
        ),
    )
)

private fun previewSeriesContent(
    seasonsPanelVisible: Boolean = false,
    trailerUrl: String? = null,
) = DetailsScreenState.Content(
    details = previewSeriesDetails,
    buttons = previewSeriesButtons,
    isInWatchlist = true,
    seasonsPanelVisible = seasonsPanelVisible,
    episodes = previewEpisodes,
    trailerUrl = trailerUrl,
)

private fun previewMovieContent(
    trailerUrl: String? = null,
) = DetailsScreenState.Content(
    details = previewMovieDetails,
    buttons = previewMovieButtons,
    isInWatchlist = false,
    trailerUrl = trailerUrl,
)

// endregion

// region Previews

@Preview(name = "Loading", device = TV_1080p)
@Composable
private fun LoadingPreview() = PuberTheme {
    DetailsScreenContent(
        state = DetailsScreenState.Loading,
        onAction = {},
    )
}

@Preview(name = "Error", device = TV_1080p)
@Composable
private fun ErrorPreview() = PuberTheme {
    DetailsScreenContent(
        state = DetailsScreenState.Error("Не удалось загрузить данные. Проверьте подключение к интернету."),
        onAction = {},
    )
}

@Preview(name = "Series — content", device = TV_1080p)
@Composable
private fun SeriesContentPreview() = PuberTheme {
    DetailsScreenContent(
        state = previewSeriesContent(),
        onAction = {},
    )
}

@Preview(name = "Movie — content", device = TV_1080p)
@Composable
private fun MovieContentPreview() = PuberTheme {
    DetailsScreenContent(
        state = previewMovieContent(),
        onAction = {},
    )
}

@Preview(name = "Series — in watchlist", device = TV_1080p)
@Composable
private fun SeriesInWatchlistPreview() = PuberTheme {
    DetailsScreenContent(
        state = previewSeriesContent(),
        onAction = {},
    )
}

@Preview(name = "Movie — not in watchlist", device = TV_1080p)
@Composable
private fun MovieNotInWatchlistPreview() = PuberTheme {
    DetailsScreenContent(
        state = previewMovieContent(),
        onAction = {},
    )
}

@Preview(name = "Series — seasons panel open", device = TV_1080p)
@Composable
private fun SeasonsPanelPreview() = PuberTheme {
    DetailsScreenContent(
        state = previewSeriesContent(seasonsPanelVisible = true),
        onAction = {},
    )
}

// Trailer previews are not possible — ExoPlayer cannot be instantiated in the preview renderer.

// endregion
