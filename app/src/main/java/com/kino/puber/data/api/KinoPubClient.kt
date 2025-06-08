package com.kino.puber.data.api

import com.kino.puber.data.api.auth.DeviceFlowResult
import com.kino.puber.data.api.auth.TokenResponse
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.BookmarkToggleResult
import com.kino.puber.data.api.models.Comment
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.DeviceInfo
import com.kino.puber.data.api.models.DeviceSettings
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemFiles
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.MediaLinks
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.QualityType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.ServerLocation
import com.kino.puber.data.api.models.StreamingType
import com.kino.puber.data.api.models.TVChannel
import com.kino.puber.data.api.models.TranslationType
import com.kino.puber.data.api.models.UserInfo
import com.kino.puber.data.api.models.VoiceAuthor
import com.kino.puber.data.api.models.VoteResult
import com.kino.puber.data.api.models.WatchingStatus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach


/**
 * Main KinoPub client that combines OAuth authentication and API functionality
 */

private const val DEFAULT_TOKEN_EXPIRY = 60 * 60 * 24 * 1000

class KinoPubClient(
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
    private val apiClient: KinoPubApiClient,
) {
    /**
     * Update username in configuration (will affect future requests)
     */
    fun updateUsername(username: String) {
        // Update oauth client
        cryptoPreferenceRepository.saveUsername(username)
    }

    /**
     * Authenticate using device flow
     * @return Flow that emits device flow states and final result
     */
    fun authenticateWithDeviceFlow(): Flow<Result<DeviceFlowResult>> {
        return apiClient.completeDeviceFlow().onEach { result ->
            if (result.isSuccess && result.getOrNull()?.token != null) {
                val flowResult = result.getOrThrow()
                setTokens(flowResult.token!!)
            }
        }
    }

    /**
     * Authenticate with existing tokens
     */
    fun authenticateWithTokens(accessToken: String, refreshToken: String? = null) {
        setTokens(TokenResponse(accessToken, refreshToken ?: "", "Bearer", DEFAULT_TOKEN_EXPIRY))
    }

    /**
     * Check if client is authenticated
     */
    fun isAuthenticated(): Boolean = cryptoPreferenceRepository.getAccessToken().isNullOrEmpty().not()

    /**
     * Clear authentication
     */
    fun clearAuthentication() {
        cryptoPreferenceRepository.clearAccessToken()
        cryptoPreferenceRepository.clearRefreshToken()
    }

    private fun setTokens(tokenResponse: TokenResponse) {
        cryptoPreferenceRepository.saveAccessToken(tokenResponse.accessToken)
        cryptoPreferenceRepository.saveRefreshToken(tokenResponse.refreshToken)
    }

    private fun requireAuthentication(): KinoPubApiClient {
        return apiClient
    }

    // Content API methods

    suspend fun getItems(
        type: String? = null,
        sort: String? = null,
        page: Int? = null,
        quality: String? = null,
        genre: String? = null,
        conditions: List<String>? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().getItems(type, sort, page, quality, genre, conditions)

    suspend fun getItemDetails(id: Int): Result<Item> =
        requireAuthentication().getItemDetails(id)

    suspend fun getItemsByShortcut(
        shortcut: String,
        type: String? = null,
        page: Int? = null,
        genre: String? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().getItemsByShortcut(shortcut, type, page, genre)

    suspend fun searchItems(
        query: String,
        field: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().searchItems(query, field, perpage)

    suspend fun searchByTitle(
        title: String,
        type: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().searchByTitle(title, type, perpage)

    suspend fun searchByDirector(
        director: String,
        sort: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().searchByDirector(director, sort, perpage)

    suspend fun searchByActor(
        actor: String,
        sort: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> =
        requireAuthentication().searchByActor(actor, sort, perpage)

    suspend fun getSimilarItems(id: Int): Result<PaginatedResponse<Item>> =
        requireAuthentication().getSimilarItems(id)

    suspend fun getItemComments(id: Int): Result<List<Comment>> =
        requireAuthentication().getItemComments(id)

    suspend fun getTrailerUrl(sid: String): Result<String> =
        requireAuthentication().getTrailerUrl(sid)

    // History & Watching API methods

    suspend fun getHistory(type: String, subscribed: Int? = null): Result<List<History>> =
        requireAuthentication().getHistory(type, subscribed)

    suspend fun getHistoryData(page: Int): Result<PaginatedResponse<History>> =
        requireAuthentication().getHistoryData(page)

    suspend fun clearItemHistory(id: Int): Result<Unit> =
        requireAuthentication().clearItemHistory(id)

    suspend fun clearMediaHistory(id: Int): Result<Unit> =
        requireAuthentication().clearMediaHistory(id)

    suspend fun clearSeasonHistory(id: Int): Result<Unit> =
        requireAuthentication().clearSeasonHistory(id)

    suspend fun toggleWatchingStatus(
        id: Int,
        status: Int? = null,
        season: Int? = null,
        video: Int? = null
    ): Result<WatchingStatus> =
        requireAuthentication().toggleWatchingStatus(id, status, season, video)

    suspend fun setWatchingTime(
        id: Int,
        videoId: Int,
        time: Int,
        season: Int? = null
    ): Result<WatchingStatus> =
        requireAuthentication().setWatchingTime(id, videoId, time, season)

    suspend fun toggleWatchlist(id: Int): Result<WatchingStatus> =
        requireAuthentication().toggleWatchlist(id)

    // Bookmarks API methods

    suspend fun getBookmarks(): Result<List<Bookmark>> =
        requireAuthentication().getBookmarks()

    suspend fun getBookmarkItems(id: Int, page: Int? = null): Result<PaginatedResponse<Item>> =
        requireAuthentication().getBookmarkItems(id, page)

    suspend fun createBookmark(title: String): Result<Bookmark> =
        requireAuthentication().createBookmark(title)

    suspend fun addBookmarkItem(itemId: Int, folderId: Int): Result<Unit> =
        requireAuthentication().addBookmarkItem(itemId, folderId)

    suspend fun removeBookmarkItem(itemId: Int, folderId: Int): Result<Unit> =
        requireAuthentication().removeBookmarkItem(itemId, folderId)

    suspend fun deleteBookmark(folderId: Int): Result<Unit> =
        requireAuthentication().deleteBookmark(folderId)

    // Collections API methods

    suspend fun getCollections(
        sort: String? = null,
        page: Int? = null
    ): Result<PaginatedResponse<KCollection>> =
        requireAuthentication().getCollections(sort, page)

    suspend fun getCollectionItems(id: Int): Result<PaginatedResponse<Item>> =
        requireAuthentication().getCollectionItems(id)

    // User & Device API methods

    suspend fun getAccountInfo(): Result<UserInfo> =
        requireAuthentication().getAccountInfo()

    suspend fun getDeviceSettings(): Result<DeviceSettings> =
        requireAuthentication().getDeviceSettings()

    suspend fun getDevicesInfo(): Result<List<DeviceSettings>> =
        requireAuthentication().getDevicesInfo()

    suspend fun updateDeviceInfo(title: String, hardware: String, software: String): Result<Unit> =
        requireAuthentication().updateDeviceInfo(title, hardware, software)

    suspend fun unlinkDevice(): Result<Unit> =
        requireAuthentication().unlinkDevice()

    // Metadata API methods

    suspend fun getGenres(): Result<List<Genre>> =
        requireAuthentication().getGenres()

    suspend fun getCountries(): Result<List<Country>> =
        requireAuthentication().getCountries()

    // Reference data API methods

    suspend fun getServerLocations(): Result<List<ServerLocation>> =
        requireAuthentication().getServerLocations()

    suspend fun getStreamingTypes(): Result<List<StreamingType>> =
        requireAuthentication().getStreamingTypes()

    suspend fun getTranslationTypes(): Result<List<TranslationType>> =
        requireAuthentication().getTranslationTypes()

    suspend fun getVoiceAuthors(): Result<List<VoiceAuthor>> =
        requireAuthentication().getVoiceAuthors()

    suspend fun getQualityTypes(): Result<List<QualityType>> =
        requireAuthentication().getQualityTypes()

    // TV Broadcasting API methods

    suspend fun getTVChannels(): Result<List<TVChannel>> =
        requireAuthentication().getTVChannels()

    suspend fun getTVChannelDetails(id: Int): Result<TVChannel> =
        requireAuthentication().getTVChannelDetails(id)

    // Media files and links API methods

    suspend fun getMediaLinks(
        id: Int,
        season: Int? = null,
        episode: Int? = null
    ): Result<MediaLinks> =
        requireAuthentication().getMediaLinks(id, season, episode)

    suspend fun getVideoFileLink(filename: String): Result<String> =
        requireAuthentication().getVideoFileLink(filename)

    suspend fun getItemFiles(
        id: Int,
        season: Int? = null,
        episode: Int? = null
    ): Result<ItemFiles> =
        requireAuthentication().getItemFiles(id, season, episode)

    // Voting API methods

    suspend fun voteForItem(id: Int, rating: Int): Result<VoteResult> =
        requireAuthentication().voteForItem(id, rating)

    suspend fun removeVoteForItem(id: Int): Result<VoteResult> =
        requireAuthentication().removeVoteForItem(id)

    // Extended device API methods

    suspend fun getAllDevices(): Result<List<DeviceInfo>> =
        requireAuthentication().getAllDevices()

    suspend fun removeDevice(deviceId: String): Result<Unit> =
        requireAuthentication().removeDevice(deviceId)

    suspend fun getDeviceSettingsById(deviceId: String): Result<DeviceSettings> =
        requireAuthentication().getDeviceSettingsById(deviceId)

    suspend fun updateDeviceSettings(
        supportSsl: Boolean? = null,
        supportHevc: Boolean? = null,
        supportHdr: Boolean? = null,
        support4k: Boolean? = null,
        mixedPlaylist: Boolean? = null,
        streamingType: Int? = null,
        serverLocation: Int? = null
    ): Result<DeviceSettings> =
        requireAuthentication().updateDeviceSettings(
            supportSsl, supportHevc, supportHdr, support4k,
            mixedPlaylist, streamingType, serverLocation
        )

    // Extended bookmarks API methods

    suspend fun getItemBookmarkFolders(itemId: Int): Result<List<BookmarkFolder>> =
        requireAuthentication().getItemBookmarkFolders(itemId)

    suspend fun toggleBookmark(itemId: Int, folderId: Int): Result<BookmarkToggleResult> =
        requireAuthentication().toggleBookmark(itemId, folderId)

    // Extended content API methods

    suspend fun getItemSeasons(id: Int): Result<List<Season>> =
        requireAuthentication().getItemSeasons(id)

    suspend fun getSeasonEpisodes(itemId: Int, seasonNumber: Int): Result<List<Episode>> =
        requireAuthentication().getSeasonEpisodes(itemId, seasonNumber)

    suspend fun getEpisodeDetails(
        itemId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): Result<Episode> =
        requireAuthentication().getEpisodeDetails(itemId, seasonNumber, episodeNumber)

    /**
     * Close the client and release resources
     */
    fun close() {
        apiClient.close()
    }
} 