package com.kino.puber.data.api

import android.content.Context
import com.kino.puber.data.api.auth.DeviceFlowResult
import com.kino.puber.data.api.auth.OAuthClient
import com.kino.puber.data.api.auth.TokenResponse
import com.kino.puber.data.api.config.KinoPubClientConfig
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Comment
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.DeviceSettings
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.UserInfo
import com.kino.puber.data.api.models.WatchingStatus

/**
 * Main KinoPub client that combines OAuth authentication and API functionality
 */
class KinoPubClient(
    private val config: KinoPubClientConfig = KinoPubClientConfig()
) {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    private val oauthClient = OAuthClient(config)
    private var apiClient: KinoPubApiClient? = null

    companion object {
        private const val DEFAULT_TOKEN_EXPIRY = 3600

        /**
         * Create default client with logging enabled
         * @param context Android context for device info
         * @param username Username for User-Agent
         * @param enableLogging Enable HTTP logging
         */
        fun create(
            context: Context,
            username: String? = null,
            enableLogging: Boolean = true
        ): KinoPubClient {
            return KinoPubClient(
                KinoPubClientConfig(
                    enableLogging = enableLogging,
                    context = context,
                    username = username
                )
            )
        }

        /**
         * Create client with custom configuration
         */
        fun create(config: KinoPubClientConfig): KinoPubClient {
            return KinoPubClient(config)
        }
    }

    /**
     * Update username in configuration (will affect future requests)
     */
    fun updateUsername(username: String) {
        val newConfig = config.copy(username = username)
        // Update oauth client
        oauthClient.updateConfig(newConfig)

        // Update API client if authenticated
        accessToken?.let { token ->
            apiClient = KinoPubApiClient(token, newConfig)
        }
    }

    /**
     * Authenticate using device flow
     * @param username Username for authentication
     * @return Device flow result with verification info and token
     */
    suspend fun authenticateWithDeviceFlow(username: String): Result<DeviceFlowResult> {
        return oauthClient.completeDeviceFlow(username).also { result ->
            if (result.isSuccess) {
                val flowResult = result.getOrThrow()
                setTokens(flowResult.token)
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
     * Refresh access token
     */
    suspend fun refreshAccessToken(): Result<TokenResponse> {
        val currentRefreshToken = refreshToken
            ?: return Result.failure(IllegalStateException("No refresh token available"))

        return oauthClient.refreshToken(currentRefreshToken).also { result ->
            if (result.isSuccess) {
                setTokens(result.getOrThrow())
            }
        }
    }

    /**
     * Check if client is authenticated
     */
    fun isAuthenticated(): Boolean = accessToken != null

    /**
     * Get current access token
     */
    fun getAccessToken(): String? = accessToken

    /**
     * Clear authentication
     */
    fun clearAuthentication() {
        accessToken = null
        refreshToken = null
        apiClient = null
    }

    private fun setTokens(tokenResponse: TokenResponse) {
        accessToken = tokenResponse.accessToken
        refreshToken = tokenResponse.refreshToken
        apiClient = KinoPubApiClient(tokenResponse.accessToken, config)
    }

    private fun requireAuthentication(): KinoPubApiClient {
        return apiClient
            ?: throw IllegalStateException("Client is not authenticated. Call authenticate* method first.")
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

    /**
     * Close the client and release resources
     */
    fun close() {
        oauthClient.close()
        apiClient?.close()
    }
} 