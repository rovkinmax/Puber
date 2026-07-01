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
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.isSeriesLike

internal class DetailsScreenUIMapper(
    private val resources: ResourceProvider,
    private val itemMapper: VideoItemUIMapper,
) {

    fun map(item: Item, isInWatchlist: Boolean = item.inWatchlist ?: false): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = itemMapper.mapDetailedItem(item),
            info = buildInfo(item),
            buttons = buildButtons(item),
            isInWatchlist = isInWatchlist,
            isWatched = itemMapper.isItemWatched(item),
            episodes = if (item.type.isSeriesLike()) mapEpisodes(item) else null,
        )
    }

    fun mapSimilarItems(items: List<Item>): List<VideoItemUIState> {
        return itemMapper.mapShortItemList(items)
            .map { item -> item.copy(showTitle = true) }
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
                val thumbnailUrls = itemMapper.mapPosterUrls(episode.thumbnail)
                val title = buildString {
                    append(episode.number)
                    append(". ")
                    append(episode.title ?: resources.getString(R.string.player_episode_untitled))
                }
                VideoItemUIState(
                    id = episode.id,
                    title = title,
                    imageUrl = thumbnailUrls.firstOrNull().orEmpty(),
                    bigImageUrl = thumbnailUrls.firstOrNull().orEmpty(),
                    imageFallbackUrls = thumbnailUrls.drop(1),
                    showTitle = true,
                    isWatched = episode.watched == 1,
                    showWatchedIndicator = itemMapper.watchedIndicatorsEnabled(),
                    progressPercent = episode.watching?.let { watching ->
                        if (watching.duration > 0) {
                            watching.time.toFloat() / watching.duration.toFloat()
                        } else {
                            null
                        }
                    },
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

    private fun buildInfo(item: Item): DetailsInfoUIState {
        val details = itemMapper.mapDetailedItem(item)
        return DetailsInfoUIState(
            description = item.plot.orEmpty(),
            ratings = details.ratings,
            primaryRows = buildPrimaryRows(item),
            secondaryRows = buildSecondaryRows(item),
            castMembers = item.castMembers(),
        )
    }

    private fun buildPrimaryRows(item: Item): List<DetailsInfoRowUIState> = buildList {
        item.originalTitle()?.let { add(row(R.string.video_details_info_original_title, it)) }
        item.year?.let { add(row(R.string.video_details_info_year, it.toString())) }
        item.durationRowValue()?.let { add(row(it.first, it.second)) }
        item.genres.orEmpty()
            .joinToString(", ") { it.title }
            .takeIf { it.isNotBlank() }
            ?.let { add(row(R.string.video_details_info_genres, it)) }
        item.countries.orEmpty()
            .joinToString(", ") { it.title }
            .takeIf { it.isNotBlank() }
            ?.let { add(row(R.string.video_details_info_country, it)) }
        item.ageRating?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_age_rating, it)) }
    }

    private fun buildSecondaryRows(item: Item): List<DetailsInfoRowUIState> = buildList {
        item.voice?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_translation, it)) }
        item.playbackAudioTrackCount().takeIf { it > 0 }?.let { count ->
            add(row(R.string.video_details_info_audio_tracks, count.toString()))
        }
        item.subtitleCount().takeIf { it > 0 }?.let {
            add(row(R.string.video_details_info_subtitles, it.toString()))
        }
        item.director?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_director, it)) }
        item.displayQuality()?.let { add(row(R.string.video_details_info_quality, it)) }
        if (item.ac3 == 1 || item.mediaItemsHaveSurroundSound()) {
            add(row(R.string.video_details_info_sound, resources.getString(R.string.video_details_info_sound_surround)))
        }
    }

    private fun row(labelRes: Int, value: String): DetailsInfoRowUIState {
        return DetailsInfoRowUIState(
            label = resources.getString(labelRes),
            value = value,
        )
    }

    private fun Item.originalTitle(): String? {
        return title.substringAfter("/", missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun Item.durationRowValue(): Pair<Int, String>? {
        return if (type.isSeriesLike()) {
            seasons?.size?.takeIf { it > 0 }?.let { R.string.video_details_info_seasons to it.toString() }
        } else {
            duration?.total?.let { total ->
                R.string.video_details_info_duration to itemMapper.run { total.formatDurationWithResources() }
            }
        }
    }

    private fun Item.subtitleCount(): Int {
        return videos.orEmpty().sumOf { video -> video.subtitles.orEmpty().size } +
            seasons.orEmpty()
                .flatMap { season -> season.episodes.orEmpty() }
                .sumOf { episode -> episode.subtitles.orEmpty().size }
    }

    private fun Item.playbackAudioTrackCount(): Int {
        return if (type.isSeriesLike()) {
            firstPlayableEpisode()?.audios.orEmpty().size
        } else {
            videos?.firstOrNull()?.audios.orEmpty().size
        }
    }

    private fun Item.firstPlayableEpisode(): Episode? {
        val seasons = seasons.orEmpty()
        for (season in seasons) {
            val firstUnwatched = season.episodes.orEmpty().firstOrNull { episode -> episode.watched != 1 }
            if (firstUnwatched != null) return firstUnwatched
        }
        return seasons.firstOrNull()?.episodes?.firstOrNull()
    }

    private fun Item.displayQuality(): String? {
        return videos.orEmpty()
            .flatMap { video -> video.files.orEmpty() }
            .mapNotNull { file ->
                file.quality
                    ?: file.h?.takeIf { it > 0 }?.let { "${it}p" }
                    ?: file.url?.hls4?.takeIf { it.isNotBlank() }?.let { "4K" }
            }
            .firstOrNull()
    }

    private fun Item.mediaItemsHaveSurroundSound(): Boolean {
        return videos.orEmpty().any { video -> video.hasSurroundSound() } ||
            seasons.orEmpty()
                .flatMap { it.episodes.orEmpty() }
                .any { episode -> episode.hasSurroundSound() }
    }

    private fun Item.castMembers(): List<String> {
        return cast.orEmpty()
            .split(",")
            .map { actor -> actor.trim() }
            .filter { actor -> actor.isNotBlank() }
    }

    private fun Video.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private fun Episode.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private companion object {
        const val SURROUND_CHANNELS = 6
    }
}
