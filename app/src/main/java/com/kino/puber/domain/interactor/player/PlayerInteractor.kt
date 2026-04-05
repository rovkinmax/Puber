package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.model.SubtitleSize

internal data class ResolvedMedia(
    val files: List<VideoFile>?,
    val audios: List<Audio>?,
    val subtitles: List<SubtitleLink>?,
    val watchingTime: Int?,
    val duration: Int?,
    val videoNumber: Int?,
    val episodeId: Int?,
    val episodeTitle: String?,
    val isSeries: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

internal class PlayerInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    fun resolveMedia(item: Item, seasonNumber: Int?, episodeNumber: Int?): ResolvedMedia {
        val isSeries = isSeriesType(item.type)

        var resolvedSeason = seasonNumber
        var resolvedEpisode = episodeNumber

        if (isSeries && resolvedSeason == null) {
            val firstUnwatched = findFirstUnwatchedEpisode(item)
            resolvedSeason = firstUnwatched?.first
            resolvedEpisode = firstUnwatched?.second
        }

        return if (isSeries) {
            val episode = findEpisode(item, resolvedSeason ?: 1, resolvedEpisode ?: 1)
            ResolvedMedia(
                files = episode?.files,
                audios = episode?.audios,
                subtitles = episode?.subtitles,
                watchingTime = episode?.watching?.time,
                duration = episode?.duration,
                videoNumber = episode?.number,
                episodeId = episode?.id,
                episodeTitle = episode?.title,
                isSeries = true,
                hasNext = resolvedSeason != null && resolvedEpisode != null &&
                        findNextEpisode(item, resolvedSeason, resolvedEpisode) != null,
                hasPrevious = resolvedSeason != null && resolvedEpisode != null &&
                        findPreviousEpisode(item, resolvedSeason, resolvedEpisode) != null,
                seasonNumber = resolvedSeason,
                episodeNumber = resolvedEpisode,
            )
        } else {
            val video = item.videos?.firstOrNull()
            ResolvedMedia(
                files = video?.files,
                audios = video?.audios,
                subtitles = video?.subtitles,
                watchingTime = video?.watching?.time,
                duration = video?.duration,
                videoNumber = video?.number,
                episodeId = null,
                episodeTitle = null,
                isSeries = false,
                hasNext = false,
                hasPrevious = false,
                seasonNumber = null,
                episodeNumber = null,
            )
        }
    }

    fun isSeriesType(type: ItemType): Boolean = when (type) {
        ItemType.SERIAL, ItemType.TV_SHOW, ItemType.DOCU_SERIAL -> true
        else -> false
    }

    fun findEpisode(item: Item, seasonNumber: Int, episodeNumber: Int) =
        item.seasons
            ?.find { it.number == seasonNumber }
            ?.episodes
            ?.find { it.number == episodeNumber }

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

    fun findPreviousEpisode(item: Item, currentSeason: Int, currentEpisode: Int): Pair<Int, Int>? {
        val seasons = item.seasons ?: return null
        val season = seasons.find { it.number == currentSeason } ?: return null
        val episodes = season.episodes ?: return null
        val currentIndex = episodes.indexOfFirst { it.number == currentEpisode }
        if (currentIndex > 0) {
            return currentSeason to episodes[currentIndex - 1].number
        }
        val seasonIndex = seasons.indexOf(season)
        if (seasonIndex > 0) {
            val prevSeason = seasons[seasonIndex - 1]
            val lastEpisode = prevSeason.episodes?.lastOrNull() ?: return null
            return prevSeason.number to lastEpisode.number
        }
        return null
    }

    fun selectStreamUrl(files: List<VideoFile>?, qualityIndex: Int): String? {
        if (files.isNullOrEmpty()) return null
        val baseUrl = if (qualityIndex == 0) {
            val url = files.first().url ?: return null
            url.hls4 ?: url.hls ?: url.http
        } else {
            val uniqueFiles = files.distinctBy { it.quality ?: "${it.h}p" }
                .sortedByDescending { it.qualityId ?: 0 }
            val file = uniqueFiles.getOrNull(qualityIndex - 1) ?: files.first()
            val url = file.url ?: return null
            url.hls ?: url.hls4 ?: url.http
        } ?: return null

        return if (playerPreferencesRepository.preferSurroundAudio) {
            val separator = if ("?" in baseUrl) "&" else "?"
            "${baseUrl}${separator}ac3default=1"
        } else {
            baseUrl
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
        return seasons.firstOrNull()?.let { season ->
            season.episodes?.firstOrNull()?.let { episode ->
                season.number to episode.number
            }
        }
    }

    suspend fun saveWatchingTime(id: Int, videoNumber: Int, time: Int, season: Int? = null) {
        api.setWatchingTime(id, videoNumber, time, season)
    }

    suspend fun markAsWatched(id: Int, season: Int? = null, videoNumber: Int? = null) {
        api.toggleWatchingStatus(id, status = 1, season = season, video = videoNumber)
    }

    fun getPreferredAudioLang(itemId: Int): String? {
        return playerPreferencesRepository.getPreferredAudioLang(itemId)
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return playerPreferencesRepository.getPreferredSubtitleLang(itemId)
    }

    fun saveTrackPreferences(itemId: Int, audioLang: String?, subtitleLang: String?) {
        playerPreferencesRepository.saveTrackPreferences(itemId, audioLang = audioLang, subtitleLang = subtitleLang)
    }

    fun isDebugOverlayEnabled(): Boolean {
        return playerPreferencesRepository.debugOverlayEnabled
    }

    fun getSubtitleSize(): SubtitleSize {
        return playerPreferencesRepository.getSubtitleSize()
    }

    fun saveSubtitleSize(size: SubtitleSize) {
        playerPreferencesRepository.saveSubtitleSize(size)
    }
}
