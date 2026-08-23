package com.kino.puber.domain.model

import kotlinx.datetime.LocalDate

enum class ScheduleProvider {
    TMDB,
}

sealed interface EpisodeScheduleResult {
    data class Available(
        val schedule: EpisodeSchedule,
    ) : EpisodeScheduleResult

    data object NoMatch : EpisodeScheduleResult

    data object NoUpcomingReleases : EpisodeScheduleResult

    data object MissingCredentials : EpisodeScheduleResult
}

data class EpisodeSchedule(
    val provider: ScheduleProvider,
    val seasons: List<ScheduledSeason>,
)

data class ScheduledSeason(
    val seasonNumber: Int,
    val announcementDate: LocalDate? = null,
    val episodes: List<ScheduledEpisode> = emptyList(),
)

data class ScheduledEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val airDate: LocalDate,
    val stillPath: String? = null,
)
