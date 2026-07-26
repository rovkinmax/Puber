package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.Item

internal fun Item.currentMediaWatched(
    isMovie: Boolean,
    seasonNumber: Int?,
    episodeNumber: Int?,
    videoNumber: Int?,
): Boolean? {
    return if (isMovie) {
        videos
            ?.find { it.number == videoNumber }
            ?.let { video ->
                watchedStatus(video.watched)
                    ?: watchedStatus(video.watching?.status)
            }
            ?: watchedStatus(watched)
    } else {
        seasons
            ?.find { it.number == seasonNumber }
            ?.episodes
            ?.find { it.number == episodeNumber }
            ?.let { episode -> watchedStatus(episode.watched) }
    }
}

internal fun Item.withCurrentMediaWatched(
    watched: Boolean,
    isMovie: Boolean,
    seasonNumber: Int?,
    episodeNumber: Int?,
    videoNumber: Int?,
): Item {
    val status = watched.toStatus()
    return if (isMovie) {
        withCurrentMovieVideoWatched(videoNumber, status)
    } else {
        withCurrentEpisodeWatched(seasonNumber, episodeNumber, status)
    }
}

private fun Item.withCurrentMovieVideoWatched(videoNumber: Int?, status: Int): Item {
    if (videos?.any { it.number == videoNumber } != true) {
        return copy(watched = status)
    }
    return copy(
        videos = videos.map { video ->
            if (video.number == videoNumber) {
                video.copy(
                    watched = status,
                    watching = video.watching?.copy(status = status),
                )
            } else {
                video
            }
        },
    )
}

private fun Item.withCurrentEpisodeWatched(
    seasonNumber: Int?,
    episodeNumber: Int?,
    status: Int,
): Item {
    return copy(
        seasons = seasons?.map { season ->
            if (season.number != seasonNumber) {
                season
            } else {
                season.copy(
                    episodes = season.episodes?.map { episode ->
                        if (episode.number == episodeNumber) episode.copy(watched = status) else episode
                    },
                )
            }
        },
    )
}

private fun watchedStatus(status: Int?): Boolean? = status?.let { it == WATCHED_STATUS }

internal fun Boolean.toStatus(): Int = if (this) WATCHED_STATUS else UNWATCHED_STATUS

private const val WATCHED_STATUS = 1
private const val UNWATCHED_STATUS = 0
