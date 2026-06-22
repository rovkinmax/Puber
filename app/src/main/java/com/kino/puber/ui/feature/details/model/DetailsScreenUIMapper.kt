package com.kino.puber.ui.feature.details.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Play
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.VideoCamera
import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike

internal class DetailsScreenUIMapper(
    private val resources: ResourceProvider,
    private val itemMapper: VideoItemUIMapper,
) {

    fun map(item: Item, isInWatchlist: Boolean = item.inWatchlist ?: false): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = itemMapper.mapDetailedItem(item),
            buttons = buildButtons(item),
            isInWatchlist = isInWatchlist,
            isWatched = itemMapper.isItemWatched(item),
            episodes = if (item.type.isSeriesLike()) mapEpisodes(item) else null,
        )
    }

    private fun mapEpisodes(item: Item): VideoGridUIState? {
        val seasons = item.seasons ?: return null
        val gridItems = mutableListOf<VideoGridItemUIState>()
        for (season in seasons) {
            val episodeCount = season.episodes?.size ?: 0
            gridItems.add(
                VideoGridItemUIState.Title(
                    resources.getString(R.string.player_season_episodes_count, season.number, episodeCount)
                )
            )
            val items = season.episodes?.map { episode ->
                val title = buildString {
                    append(episode.number)
                    append(". ")
                    append(episode.title ?: resources.getString(R.string.player_episode_untitled))
                }
                VideoItemUIState(
                    id = episode.id,
                    title = title,
                    imageUrl = episode.thumbnail ?: "",
                    bigImageUrl = episode.thumbnail ?: "",
                    showTitle = true,
                )
            } ?: emptyList()
            gridItems.add(VideoGridItemUIState.Items(items))
        }
        return VideoGridUIState(list = gridItems)
    }

    private fun buildButtons(item: Item): List<DetailsButtonUIState> {
        val isSeriesLike = item.type.isSeriesLike()
        return if (isSeriesLike) {
            buildSeriesButtons(item)
        } else {
            buildMovieButtons(item)
        } + buildStatusButtons(isSeriesLike)
    }

    private fun buildSeriesButtons(item: Item): List<DetailsButtonUIState> = buildList {
        val continueText = findFirstUnwatchedEpisode(item)?.let { (season, episode) ->
            resources.getString(R.string.player_season_episode, season, episode)
        }
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_series,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
                textOverride = continueText,
            )
        )
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_select_season,
                icon = PhosphorIcons.Duotone.Playlist,
                action = DetailsAction.SelectSeasonClicked,
            )
        )
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.IconOnly(
                    icon = PhosphorIcons.Duotone.VideoCamera,
                    contentDescription = R.string.video_details_button_trailer,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
    }

    private fun buildMovieButtons(item: Item): List<DetailsButtonUIState> = buildList {
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_movie,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
            )
        )
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_trailer,
                    icon = PhosphorIcons.Duotone.FilmSlate,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
    }

    private fun buildStatusButtons(isSeriesLike: Boolean): List<DetailsButtonUIState> = buildList {
        add(
            DetailsButtonUIState.WatchlistToggle(
                contentDescription = if (isSeriesLike) {
                    R.string.video_details_button_add_to_watchlist
                } else {
                    R.string.video_details_button_add_to_bookmarks
                },
                action = DetailsAction.WatchlistToggleClicked,
            )
        )
        if (!isSeriesLike) {
            add(
                DetailsButtonUIState.WatchedToggle(
                    contentDescription = R.string.video_details_button_mark_watched,
                    action = DetailsAction.WatchedToggleClicked,
                )
            )
        }
    }

    private fun findFirstUnwatchedEpisode(item: Item): Pair<Int, Int>? {
        val seasons = item.seasons ?: return null
        for (season in seasons) {
            val episodes = season.episodes ?: continue
            for (episode in episodes) {
                if (episode.watched != 1) {
                    return season.number to episode.number
                }
            }
        }
        return null
    }

}
