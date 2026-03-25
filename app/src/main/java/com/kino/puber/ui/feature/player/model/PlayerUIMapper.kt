package com.kino.puber.ui.feature.player.model

import android.content.Context
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.VideoFile

internal class PlayerUIMapper(private val context: Context) {

    fun isSeriesType(type: ItemType): Boolean = when (type) {
        ItemType.SERIAL, ItemType.TV_SHOW, ItemType.DOCU_SERIAL -> true
        else -> false
    }

    fun mapAudioTracks(audios: List<Audio>?): List<AudioTrackUIState> {
        return audios?.mapIndexed { index, audio ->
            val label = buildString {
                append(audio.lang ?: "Unknown")
                audio.author?.title?.let { append(" · $it") }
                    ?: audio.type?.title?.let { append(" · $it") }
            }
            AudioTrackUIState(
                index = index,
                label = label,
                language = audio.lang ?: "",
            )
        } ?: emptyList()
    }

    fun mapSubtitleTracks(subtitles: List<SubtitleLink>?): List<SubtitleTrackUIState> {
        val result = mutableListOf(
            SubtitleTrackUIState(
                index = 0,
                label = context.getString(R.string.player_subtitles_off),
                language = "",
                url = "",
            )
        )
        subtitles?.forEachIndexed { index, sub ->
            result.add(
                SubtitleTrackUIState(
                    index = index + 1,
                    label = sub.lang,
                    language = sub.lang,
                    url = sub.url,
                )
            )
        }
        return result
    }

    fun mapQualities(files: List<VideoFile>?): List<QualityUIState> {
        if (files.isNullOrEmpty()) return emptyList()

        // Deduplicate by quality string, keep first occurrence (prefer h265 over h264)
        val unique = files
            .distinctBy { it.quality ?: "${it.h}p" }
            .sortedByDescending { it.qualityId ?: 0 }

        val result = mutableListOf(
            QualityUIState(
                index = 0,
                label = context.getString(R.string.player_aspect_auto),
                qualityId = null,
                width = null,
                height = null,
            )
        )
        unique.forEachIndexed { i, file ->
            result.add(
                QualityUIState(
                    index = i + 1,
                    label = file.quality ?: "${file.h}p",
                    qualityId = file.qualityId,
                    width = file.w,
                    height = file.h,
                )
            )
        }
        return result
    }

    fun mapEpisodes(item: Item): VideoGridUIState? {
        val seasons = item.seasons ?: return null
        val gridItems = mutableListOf<VideoGridItemUIState>()
        for (season in seasons) {
            val episodeCount = season.episodes?.size ?: 0
            gridItems.add(
                VideoGridItemUIState.Title(
                    context.getString(R.string.player_season_episodes_count, season.number, episodeCount)
                )
            )
            val items = season.episodes?.map { episode ->
                VideoItemUIState(
                    id = episode.id,
                    title = "${episode.number}. ${episode.title ?: ""}",
                    imageUrl = episode.thumbnail ?: "",
                    bigImageUrl = episode.thumbnail ?: "",
                )
            } ?: emptyList()
            gridItems.add(VideoGridItemUIState.Items(items))
        }
        return VideoGridUIState(list = gridItems)
    }

    fun buildTitle(item: Item, seasonNumber: Int?, episodeNumber: Int?): String {
        return item.title
    }

    fun buildSubtitle(item: Item, seasonNumber: Int?, episodeNumber: Int?, episodeTitle: String?): String? {
        if (seasonNumber == null || episodeNumber == null) return null
        return if (episodeTitle != null) {
            context.getString(R.string.player_season_episode_title, seasonNumber, episodeNumber, episodeTitle)
        } else {
            context.getString(R.string.player_season_episode, seasonNumber, episodeNumber)
        }
    }

    fun findEpisode(item: Item, seasonNumber: Int, episodeNumber: Int): Episode? {
        return item.seasons
            ?.find { it.number == seasonNumber }
            ?.episodes
            ?.find { it.number == episodeNumber }
    }

    fun findFirstUnwatchedEpisode(item: Item): Pair<Int, Int>? {
        val seasons = item.seasons ?: return null
        for (season in seasons) {
            val episodes = season.episodes ?: continue
            for (episode in episodes) {
                if (episode.watched != 1) {
                    return season.number to episode.number
                }
            }
        }
        return seasons.firstOrNull()?.let { season ->
            season.episodes?.firstOrNull()?.let { episode ->
                season.number to episode.number
            }
        }
    }

    fun findVideoForMovie(item: Item): Video? {
        return item.videos?.firstOrNull()
    }

    fun findNextEpisode(item: Item, currentSeason: Int, currentEpisode: Int): Pair<Int, Int>? {
        val seasons = item.seasons ?: return null
        val season = seasons.find { it.number == currentSeason } ?: return null
        val episodes = season.episodes ?: return null
        val currentIndex = episodes.indexOfFirst { it.number == currentEpisode }
        if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
            return currentSeason to episodes[currentIndex + 1].number
        }
        val seasonIndex = seasons.indexOf(season)
        if (seasonIndex < seasons.size - 1) {
            val nextSeason = seasons[seasonIndex + 1]
            val firstEpisode = nextSeason.episodes?.firstOrNull() ?: return null
            return nextSeason.number to firstEpisode.number
        }
        return null
    }

    fun selectStreamUrl(files: List<VideoFile>?, qualityIndex: Int): String? {
        if (files.isNullOrEmpty()) return null
        // qualityIndex 0 = "Авто" → use hls4 of first file (adaptive bitrate)
        // qualityIndex > 0 → map to deduplicated quality list
        if (qualityIndex == 0) {
            val url = files.first().url ?: return null
            return url.hls4 ?: url.hls ?: url.http
        }
        val uniqueFiles = files.distinctBy { it.quality ?: "${it.h}p" }
            .sortedByDescending { it.qualityId ?: 0 }
        val file = uniqueFiles.getOrNull(qualityIndex - 1) ?: files.first()
        val url = file.url ?: return null
        return url.hls4 ?: url.hls ?: url.http
    }

    fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    companion object {
        val SPEEDS = listOf(
            SpeedUIState(0, "0.25x", 0.25f),
            SpeedUIState(1, "0.5x", 0.5f),
            SpeedUIState(2, "0.75x", 0.75f),
            SpeedUIState(3, "1.0x", 1.0f),
            SpeedUIState(4, "1.25x", 1.25f),
            SpeedUIState(5, "1.5x", 1.5f),
            SpeedUIState(6, "1.75x", 1.75f),
            SpeedUIState(7, "2.0x", 2.0f),
        )

        val ASPECT_RATIOS = listOf(
            AspectRatioUIState(0, "Авто", AspectRatioMode.AUTO),
            AspectRatioUIState(1, "Растянуть", AspectRatioMode.STRETCH),
            AspectRatioUIState(2, "Заполнить", AspectRatioMode.CROP),
        )

        const val DEFAULT_SPEED_INDEX = 3
        const val DEFAULT_ASPECT_RATIO_INDEX = 0
    }
}
