package com.kino.puber.ui.feature.episodeschedule.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
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
        return EpisodeScheduleScreenState.Content(
            title = title,
            provider = schedule.provider,
            seasons = schedule.seasons
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
                },
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
}
