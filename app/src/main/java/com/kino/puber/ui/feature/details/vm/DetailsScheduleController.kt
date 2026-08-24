package com.kino.puber.ui.feature.details.vm

import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.EpisodeScheduleResult
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class DetailsScheduleController(
    private val interactor: EpisodeScheduleInteractor,
    private val mapper: DetailsScreenUIMapper,
    private val initialEpisode: DetailsEpisodeTarget?,
) {
    private var currentSchedule: EpisodeSchedule? = null
    private var currentIdentity: ScheduleIdentity? = null
    private var loadJob: Job? = null
    private var requestGeneration = 0L

    fun clearIfIdentityChanged(item: Item) {
        if (currentIdentity != item.scheduleIdentity()) {
            currentSchedule = null
            currentIdentity = null
        }
    }

    fun map(item: Item, isInWatchlist: Boolean): DetailsScreenState.Content {
        val schedule = currentSchedule.takeIf {
            currentIdentity == item.scheduleIdentity()
        }
        return initialEpisode?.let { episode ->
            if (schedule == null) {
                mapper.map(
                    item = item,
                    isInWatchlist = isInWatchlist,
                    initialEpisode = episode,
                )
            } else {
                mapper.map(
                    item = item,
                    isInWatchlist = isInWatchlist,
                    initialEpisode = episode,
                    schedule = schedule,
                )
            }
        } ?: if (schedule == null) {
            mapper.map(item, isInWatchlist = isInWatchlist)
        } else {
            mapper.map(
                item = item,
                isInWatchlist = isInWatchlist,
                schedule = schedule,
            )
        }
    }

    fun load(
        item: Item,
        currentItem: () -> Item?,
        launchRequest: (suspend () -> Unit) -> Job,
        onScheduleChanged: () -> Unit,
    ) {
        val generation = ++requestGeneration
        loadJob?.cancel()
        val requestIdentity = item.scheduleIdentity()
        if (requestIdentity == null) {
            if (clear()) onScheduleChanged()
            return
        }

        loadJob = launchRequest {
            val result = try {
                interactor.getSchedule(requestIdentity.imdbId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                return@launchRequest
            }
            if (
                generation != requestGeneration ||
                currentItem()?.scheduleIdentity() != requestIdentity
            ) {
                return@launchRequest
            }
            if (apply(result, requestIdentity)) {
                onScheduleChanged()
            }
        }
    }

    fun screenParams(item: Item): EpisodeScheduleScreenParams? {
        val identity = item.scheduleIdentity() ?: return null
        return EpisodeScheduleScreenParams(
            itemId = identity.itemId,
            title = item.title,
            imdbId = identity.imdbId,
        )
    }

    private fun apply(
        result: EpisodeScheduleResult,
        identity: ScheduleIdentity,
    ): Boolean {
        return when (result) {
            is EpisodeScheduleResult.Available -> {
                currentSchedule = result.schedule
                currentIdentity = identity
                true
            }
            EpisodeScheduleResult.MissingCredentials,
            EpisodeScheduleResult.NoMatch,
            EpisodeScheduleResult.NoUpcomingReleases,
            -> clear()
        }
    }

    private fun clear(): Boolean {
        val changed = currentSchedule != null || currentIdentity != null
        currentSchedule = null
        currentIdentity = null
        return changed
    }

    private fun Item.scheduleIdentity(): ScheduleIdentity? {
        if (!type.isSeriesLike()) return null
        val imdbId = imdb?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return ScheduleIdentity(itemId = id, imdbId = imdbId)
    }

    private data class ScheduleIdentity(
        val itemId: Int,
        val imdbId: String,
    )
}
