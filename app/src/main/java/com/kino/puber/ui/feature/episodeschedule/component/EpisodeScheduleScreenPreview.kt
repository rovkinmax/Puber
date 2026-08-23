package com.kino.puber.ui.feature.episodeschedule.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import kotlinx.datetime.LocalDate

private const val FIRST_PREVIEW_YEAR = 2026
private const val SECOND_PREVIEW_YEAR = 2027
private const val SEPTEMBER = 9
private const val SECOND_EPISODE_DAY = 8

private val previewContent = EpisodeScheduleScreenState.Content(
    title = "Дом дракона",
    provider = ScheduleProvider.TMDB,
    seasons = listOf(
        EpisodeScheduleScreenState.Season(
            seasonNumber = 3,
            announcementDate = null,
            announcementDateLabel = null,
            episodes = listOf(
                EpisodeScheduleScreenState.Episode(
                    episodeNumber = 1,
                    title = "Серия 1",
                    airDate = LocalDate(FIRST_PREVIEW_YEAR, SEPTEMBER, 1),
                    airDateLabel = "1 сент. 2026 г.",
                ),
                EpisodeScheduleScreenState.Episode(
                    episodeNumber = 2,
                    title = "Серия 2",
                    airDate = LocalDate(FIRST_PREVIEW_YEAR, SEPTEMBER, SECOND_EPISODE_DAY),
                    airDateLabel = "8 сент. 2026 г.",
                ),
            ),
        ),
        EpisodeScheduleScreenState.Season(
            seasonNumber = 4,
            announcementDate = LocalDate(SECOND_PREVIEW_YEAR, 2, 1),
            announcementDateLabel = "1 февр. 2027 г.",
            episodes = emptyList(),
        ),
    ),
)

@Preview(name = "Episode schedule — Loading", device = TV_1080p)
@Composable
private fun EpisodeScheduleLoadingPreview() = PuberTheme {
    EpisodeScheduleScreenContent(
        state = EpisodeScheduleScreenState.Loading,
        onAction = {},
    )
}

@Preview(name = "Episode schedule — Content", device = TV_1080p)
@Composable
private fun EpisodeScheduleContentPreview() = PuberTheme {
    EpisodeScheduleScreenContent(
        state = previewContent,
        onAction = {},
    )
}

@Preview(name = "Episode schedule — Empty", device = TV_1080p)
@Composable
private fun EpisodeScheduleEmptyPreview() = PuberTheme {
    EpisodeScheduleScreenContent(
        state = EpisodeScheduleScreenState.Empty(
            reason = EpisodeScheduleScreenState.EmptyReason.NoUpcomingReleases,
        ),
        onAction = {},
    )
}

@Preview(name = "Episode schedule — Error", device = TV_1080p)
@Composable
private fun EpisodeScheduleErrorPreview() = PuberTheme {
    EpisodeScheduleScreenContent(
        state = EpisodeScheduleScreenState.Error(
            message = "Не удалось загрузить расписание.",
        ),
        onAction = {},
    )
}
