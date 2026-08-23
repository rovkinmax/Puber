package com.kino.puber.domain.interactor.schedule

import com.kino.puber.data.repository.EpisodeScheduleRepository
import com.kino.puber.domain.model.EpisodeScheduleResult

internal class EpisodeScheduleInteractor(
    private val repository: EpisodeScheduleRepository,
) {

    suspend fun getSchedule(imdbId: String): EpisodeScheduleResult {
        return repository.getSchedule(imdbId)
    }
}
