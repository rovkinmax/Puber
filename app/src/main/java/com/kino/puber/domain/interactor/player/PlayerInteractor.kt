package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Audio
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.BufferPreset

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

private data class EpisodeCursor(
    val seasons: List<Season>,
    val seasonIndex: Int,
    val episodeIndex: Int,
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
        val cursor = episodeCursor(item, currentSeason, currentEpisode)
        return cursor?.nextInCurrentSeason()
            ?: cursor?.firstEpisodeInNextSeason()
    }

    fun findPreviousEpisode(item: Item, currentSeason: Int, currentEpisode: Int): Pair<Int, Int>? {
        val cursor = episodeCursor(item, currentSeason, currentEpisode)
        return cursor?.previousInCurrentSeason()
            ?: cursor?.lastEpisodeInPreviousSeason()
    }

    private fun episodeCursor(item: Item, currentSeason: Int, currentEpisode: Int): EpisodeCursor? {
        val seasons = item.seasons.orEmpty()
        val seasonIndex = seasons.indexOfFirst { it.number == currentSeason }
        val episodes = seasons.getOrNull(seasonIndex)?.episodes.orEmpty()
        val episodeIndex = episodes.indexOfFirst { it.number == currentEpisode }
        return if (seasonIndex >= 0 && episodeIndex >= 0) {
            EpisodeCursor(
                seasons = seasons,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
            )
        } else {
            null
        }
    }

    private fun EpisodeCursor.nextInCurrentSeason(): Pair<Int, Int>? {
        val season = seasons[seasonIndex]
        val episodes = season.episodes.orEmpty()
        return episodes.getOrNull(episodeIndex + 1)?.let { episode ->
            season.number to episode.number
        }
    }

    private fun EpisodeCursor.firstEpisodeInNextSeason(): Pair<Int, Int>? {
        val nextSeason = seasons.getOrNull(seasonIndex + 1)
        return nextSeason?.episodes?.firstOrNull()?.let { episode ->
            nextSeason.number to episode.number
        }
    }

    private fun EpisodeCursor.previousInCurrentSeason(): Pair<Int, Int>? {
        val season = seasons[seasonIndex]
        val episodes = season.episodes.orEmpty()
        return episodes.getOrNull(episodeIndex - 1)?.let { episode ->
            season.number to episode.number
        }
    }

    private fun EpisodeCursor.lastEpisodeInPreviousSeason(): Pair<Int, Int>? {
        val previousSeason = seasons.getOrNull(seasonIndex - 1)
        return previousSeason?.episodes?.lastOrNull()?.let { episode ->
            previousSeason.number to episode.number
        }
    }

    fun selectStreamUrl(files: List<VideoFile>?, qualityIndex: Int): String? {
        return selectBaseStreamUrl(files, qualityIndex)?.let(::withSurroundAudioPreference)
    }

    private fun selectBaseStreamUrl(files: List<VideoFile>?, qualityIndex: Int): String? {
        if (files.isNullOrEmpty()) return null
        return if (qualityIndex == 0) {
            selectAutoStreamUrl(files)
        } else {
            selectManualStreamUrl(files, qualityIndex)
        }
    }

    private fun selectAutoStreamUrl(files: List<VideoFile>): String? {
        val url = files.firstOrNull()?.url
        return url?.hls4 ?: url?.hls ?: url?.http
    }

    private fun selectManualStreamUrl(files: List<VideoFile>, qualityIndex: Int): String? {
        val uniqueFiles = files.distinctBy { it.quality ?: "${it.h}p" }
            .sortedByDescending { it.qualityId ?: 0 }
        val url = (uniqueFiles.getOrNull(qualityIndex - 1) ?: files.first()).url
        return url?.hls ?: url?.hls4 ?: url?.http
    }

    private fun withSurroundAudioPreference(baseUrl: String): String {
        if (!playerPreferencesRepository.preferSurroundAudio) return baseUrl
        val separator = if ("?" in baseUrl) "&" else "?"
        return "${baseUrl}${separator}ac3default=1"
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

    fun getPreferredAudioLabel(itemId: Int): String? {
        return playerPreferencesRepository.getPreferredAudioLabel(itemId)
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return playerPreferencesRepository.getPreferredSubtitleLang(itemId)
    }

    fun saveTrackPreferences(itemId: Int, audioLang: String?, audioLabel: String?, subtitleLang: String?) {
        playerPreferencesRepository.saveTrackPreferences(
            itemId = itemId,
            audioLang = audioLang,
            audioLabel = audioLabel,
            subtitleLang = subtitleLang,
        )
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

    fun getBufferPreset(): BufferPreset {
        return playerPreferencesRepository.bufferPreset
    }

    fun saveBufferPreset(preset: BufferPreset) {
        playerPreferencesRepository.bufferPreset = preset
    }

    fun isFastDnsEnabled(): Boolean = playerPreferencesRepository.fastDnsEnabled

    fun setFastDnsEnabled(enabled: Boolean) {
        playerPreferencesRepository.fastDnsEnabled = enabled
    }
}
