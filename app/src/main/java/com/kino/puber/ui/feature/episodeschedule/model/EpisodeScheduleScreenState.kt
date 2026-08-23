package com.kino.puber.ui.feature.episodeschedule.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.model.ScheduleProvider
import kotlinx.datetime.LocalDate

@Immutable
internal sealed class EpisodeScheduleScreenState {
    data object Loading : EpisodeScheduleScreenState()

    @Immutable
    data class Content(
        val title: String,
        val provider: ScheduleProvider,
        val seasons: List<Season>,
    ) : EpisodeScheduleScreenState()

    @Immutable
    data class Empty(
        val reason: EmptyReason,
    ) : EpisodeScheduleScreenState()

    @Immutable
    data class Error(
        val message: String,
    ) : EpisodeScheduleScreenState()

    enum class EmptyReason {
        MissingCredentials,
        NoMatch,
        NoUpcomingReleases,
    }

    @Immutable
    data class Season(
        val seasonNumber: Int,
        val announcementDate: LocalDate?,
        val announcementDateLabel: String?,
        val episodes: List<Episode>,
    )

    @Immutable
    data class Episode(
        val episodeNumber: Int,
        val title: String,
        val airDate: LocalDate,
        val airDateLabel: String,
    ) {
        val releaseDate: LocalDate
            get() = airDate

        val releaseDateLabel: String
            get() = airDateLabel
    }

    sealed interface Action : UIAction {
        data object Retry : Action
    }
}
