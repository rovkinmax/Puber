package com.kino.puber.ui.feature.episodeschedule.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.domain.model.ScheduledEpisode
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

internal class EpisodeScheduleUIMapper(
    private val resources: ResourceProvider,
) {

    fun mapToContent(
        title: String,
        schedule: EpisodeSchedule,
    ): EpisodeScheduleScreenState.Content {
        val seasons = schedule.seasons
            .sortedBy { it.seasonNumber }
            .map { season ->
                EpisodeScheduleScreenState.Season(
                    seasonNumber = season.seasonNumber,
                    announcementDate = season.announcementDate,
                    announcementDateLabel = season.announcementDate?.localizedDate(),
                    episodes = season.episodes
                        .sortedBy(ScheduledEpisode::episodeNumber)
                        .map { episode ->
                            EpisodeScheduleScreenState.Episode(
                                episodeNumber = episode.episodeNumber,
                                title = episode.title.orEmpty(),
                                airDate = episode.airDate,
                                airDateLabel = episode.airDate.localizedDate(),
                            )
                        },
                )
            }
        return EpisodeScheduleScreenState.Content(
            title = title,
            provider = schedule.provider,
            seasons = seasons,
            grid = mapGrid(seasons),
        )
    }

    fun mapToContent(
        params: EpisodeScheduleScreenParams,
        schedule: EpisodeSchedule,
    ): EpisodeScheduleScreenState.Content {
        return mapToContent(title = params.title, schedule = schedule)
    }

    fun map(
        params: EpisodeScheduleScreenParams,
        result: EpisodeScheduleResult.Available,
    ): EpisodeScheduleScreenState.Content {
        return mapToContent(params = params, schedule = result.schedule)
    }

    fun mapEmpty(result: EpisodeScheduleResult): EpisodeScheduleScreenState.Empty {
        val reason = when (result) {
            EpisodeScheduleResult.MissingCredentials ->
                EpisodeScheduleScreenState.EmptyReason.MissingCredentials

            EpisodeScheduleResult.NoMatch ->
                EpisodeScheduleScreenState.EmptyReason.NoMatch

            EpisodeScheduleResult.NoUpcomingReleases ->
                EpisodeScheduleScreenState.EmptyReason.NoUpcomingReleases

            is EpisodeScheduleResult.Available ->
                error("Available schedule cannot be mapped to an empty state")
        }
        return EpisodeScheduleScreenState.Empty(reason)
    }

    fun mapError(error: Throwable): EpisodeScheduleScreenState.Error {
        return EpisodeScheduleScreenState.Error(
            message = resources.getString(R.string.error_generic),
        )
    }

    private fun LocalDate.localizedDate(): String {
        return java.time.LocalDate.of(year, month.ordinal + 1, day)
            .format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault()),
            )
    }

    private fun mapGrid(
        seasons: List<EpisodeScheduleScreenState.Season>,
    ): VideoGridUIState {
        return VideoGridUIState(
            list = buildList {
                seasons.forEach { season ->
                    val cards = buildList {
                        if (season.episodes.isEmpty()) {
                            season.announcementDateLabel?.let { dateLabel ->
                                add(mapSeasonAnnouncement(season.seasonNumber, dateLabel))
                            }
                        }
                        addAll(
                            season.episodes.map { episode ->
                                mapEpisode(season.seasonNumber, episode)
                            },
                        )
                    }
                    add(
                        VideoGridItemUIState.Title(
                            resources.getString(
                                R.string.player_season_episodes_count,
                                season.seasonNumber,
                                cards.size,
                            ),
                        ),
                    )
                    add(
                        VideoGridItemUIState.Items(
                            items = cards,
                            rowKey = "season_${season.seasonNumber}",
                        ),
                    )
                }
            },
        )
    }

    private fun mapSeasonAnnouncement(
        seasonNumber: Int,
        dateLabel: String,
    ): VideoItemUIState {
        return scheduledCard(
            id = scheduledItemId(seasonNumber, ANNOUNCEMENT_EPISODE_NUMBER),
            title = resources.getString(R.string.episode_schedule_season_announcement),
            dateLabel = dateLabel,
            seasonNumber = seasonNumber,
            episodeNumber = null,
        )
    }

    private fun mapEpisode(
        seasonNumber: Int,
        episode: EpisodeScheduleScreenState.Episode,
    ): VideoItemUIState {
        val episodeTitle = episode.title.ifBlank {
            resources.getString(R.string.player_episode_untitled)
        }
        return scheduledCard(
            id = scheduledItemId(seasonNumber, episode.episodeNumber),
            title = "${episode.episodeNumber}. $episodeTitle",
            dateLabel = episode.airDateLabel,
            seasonNumber = seasonNumber,
            episodeNumber = episode.episodeNumber,
        )
    }

    private fun scheduledCard(
        id: Int,
        title: String,
        dateLabel: String,
        seasonNumber: Int,
        episodeNumber: Int?,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = title,
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            isWatched = null,
            isSeriesLike = false,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            presentation = VideoItemPresentation.Scheduled,
            scheduledReleaseDate = resources.getString(
                R.string.episode_schedule_release_date,
                dateLabel,
            ),
        )
    }

    private fun scheduledItemId(seasonNumber: Int, episodeNumber: Int): Int {
        val encoded = seasonNumber.toLong() * SCHEDULED_SEASON_MULTIPLIER + episodeNumber + 1L
        return -encoded.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val ANNOUNCEMENT_EPISODE_NUMBER = 0
        const val SCHEDULED_SEASON_MULTIPLIER = 1_000_000L
    }
}
