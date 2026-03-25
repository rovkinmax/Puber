package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemFiles
import com.kino.puber.data.api.models.MediaLinks
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.ui.feature.player.model.SubtitleSize

internal class PlayerInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
) {

    suspend fun getItemDetails(id: Int): Item {
        return itemDetailsRepository.getItemDetails(id)
    }

    suspend fun getMediaLinks(id: Int, season: Int? = null, episode: Int? = null): MediaLinks {
        return api.getMediaLinks(id, season, episode).getOrThrow()
    }

    suspend fun getItemFiles(id: Int, season: Int? = null, episode: Int? = null): ItemFiles {
        return api.getItemFiles(id, season, episode).getOrThrow()
    }

    suspend fun saveWatchingTime(id: Int, videoId: Int, time: Int, season: Int? = null) {
        api.setWatchingTime(id, videoId, time, season)
    }

    suspend fun markAsWatched(id: Int, season: Int? = null, videoId: Int? = null) {
        api.toggleWatchingStatus(id, status = 1, season = season, video = videoId)
    }

    fun getPreferredAudioTrackId(itemId: Int): Int? {
        return playerPreferencesRepository.getPreferredAudioTrackId(itemId)
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return playerPreferencesRepository.getPreferredSubtitleLang(itemId)
    }

    fun saveTrackPreferences(itemId: Int, audioTrackId: Int?, subtitleLang: String?) {
        playerPreferencesRepository.saveTrackPreferences(itemId, audioTrackId, subtitleLang)
    }

    fun getSubtitleSize(): SubtitleSize {
        return playerPreferencesRepository.getSubtitleSize()
    }

    fun saveSubtitleSize(size: SubtitleSize) {
        playerPreferencesRepository.saveSubtitleSize(size)
    }
}
